#!/usr/bin/env python3
"""
Kit-IME Desktop/Drive Corpus Builder & Predictor DB Compiler 🐾
--------------------------------------------------------------
Scans local text/markdown notes, chat exports, or Google Drive synced directories,
computes word frequencies, positional distribution (start, mid, end), bigram transitions,
and compiles a clean SQLite database ready for Kit-IME deployment.

Output SQLite Schema:
- corpus_words(word, display_word, freq, pos_start, pos_mid, pos_end, is_custom, last_updated)
- usage_words(word, display_word, confirmed_freq, pos_start, pos_mid, pos_end, is_custom, last_used)
- bigrams(prev_word, next_word, freq)
- typo_confusion(bad_token, intended_token, slip_type, count, last_seen)
- pending_firebase_deltas(delta_key, kind, delta)
- sync_cursors(source_key, cursor_val)
"""

import os
import re
import sys
import time
import sqlite3
import argparse
from collections import defaultdict
from pathlib import Path

# Common skip tokens / extensions
SKIP_EXTENSIONS = {'.json', '.html', '.apk', '.dex', '.arsc', '.pyc', '.zip', '.tar', '.gz'}
SKIP_DIRS = {'build', '.git', '.gradle', 'node_modules', '__pycache__'}

URL_REGEX = re.compile(r'https?://\S+|www\.\S+', re.IGNORECASE)
OTP_REGEX = re.compile(r'\b(?:\d{4,8}|[A-Z0-9]{6,8})\b')
TOKEN_DELIMITER = re.compile(r"[^\w']+", re.UNICODE)
SENTENCE_SPLIT = re.compile(r'[.!?\n]+')

def sanitize_text(text: str) -> str:
    # Strip URLs
    text = URL_REGEX.sub(' ', text)
    # Strip standalone OTP codes
    text = OTP_REGEX.sub(' ', text)
    return text

def parse_files(input_paths):
    words_acc = defaultdict(lambda: {
        'count': 0, 'display': '', 'pos_start': 0, 'pos_mid': 0, 'pos_end': 0
    })
    bigrams_acc = defaultdict(float)

    total_files = 0
    total_tokens = 0

    for base_path in input_paths:
        p = Path(base_path)
        if not p.exists():
            print(f"⚠️ Warning: Path not found: {p}")
            continue

        if p.is_file():
            candidates = [p]
        else:
            candidates = [
                f for f in p.rglob('*') 
                if f.is_file() 
                and f.suffix.lower() in {'.md', '.txt', '.log'}
                and not any(part in SKIP_DIRS for part in f.parts)
            ]

        for file_path in candidates:
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
            except Exception as e:
                continue

            total_files += 1
            content = sanitize_text(content)
            sentences = SENTENCE_SPLIT.split(content)

            for sentence in sentences:
                raw_tokens = [t.strip() for t in TOKEN_DELIMITER.split(sentence) if t.strip()]
                # Filter out pure numbers or overly long tokens
                tokens = [t for t in raw_tokens if not t.isdigit() and len(t) <= 40]
                if not tokens:
                    continue

                for i, token in enumerate(tokens):
                    lower = token.lower()
                    entry = words_acc[lower]
                    entry['count'] += 1
                    if not entry['display'] or entry['display'].islower():
                        entry['display'] = token
                    
                    if i == 0:
                        entry['pos_start'] += 1
                    elif i == len(tokens) - 1:
                        entry['pos_end'] += 1
                    else:
                        entry['pos_mid'] += 1

                    if i > 0:
                        prev_lower = tokens[i - 1].lower()
                        bigrams_acc[(prev_lower, lower)] += 1.0

                total_tokens += len(tokens)

    return words_acc, bigrams_acc, total_files, total_tokens

def compile_database(output_db_path: str, words_acc, bigrams_acc, min_freq: int = 1):
    db_file = Path(output_db_path)
    if db_file.exists():
        db_file.unlink()

    conn = sqlite3.connect(output_db_path)
    cursor = conn.cursor()

    cursor.execute("PRAGMA journal_mode = WAL;")
    cursor.execute("PRAGMA synchronous = NORMAL;")

    # Schema creation matching PredictorDatabase.kt
    cursor.execute("""
    CREATE TABLE corpus_words (
        word TEXT PRIMARY KEY COLLATE NOCASE,
        display_word TEXT,
        freq REAL,
        pos_start INTEGER DEFAULT 0,
        pos_mid INTEGER DEFAULT 0,
        pos_end INTEGER DEFAULT 0,
        is_custom INTEGER DEFAULT 0,
        last_updated INTEGER
    );
    """)

    cursor.execute("""
    CREATE TABLE usage_words (
        word TEXT PRIMARY KEY COLLATE NOCASE,
        display_word TEXT,
        confirmed_freq INTEGER DEFAULT 0,
        pos_start INTEGER DEFAULT 0,
        pos_mid INTEGER DEFAULT 0,
        pos_end INTEGER DEFAULT 0,
        is_custom INTEGER DEFAULT 0,
        last_used INTEGER
    );
    """)

    cursor.execute("""
    CREATE TABLE bigrams (
        prev_word TEXT COLLATE NOCASE,
        next_word TEXT COLLATE NOCASE,
        freq REAL,
        PRIMARY KEY (prev_word, next_word)
    );
    """)

    cursor.execute("""
    CREATE TABLE typo_confusion (
        bad_token TEXT COLLATE NOCASE,
        intended_token TEXT COLLATE NOCASE,
        slip_type TEXT,
        count INTEGER DEFAULT 1,
        last_seen INTEGER,
        PRIMARY KEY (bad_token, intended_token, slip_type)
    );
    """)

    cursor.execute("""
    CREATE TABLE pending_firebase_deltas (
        delta_key TEXT PRIMARY KEY,
        kind TEXT,
        delta INTEGER
    );
    """)

    cursor.execute("""
    CREATE TABLE sync_cursors (
        source_key TEXT PRIMARY KEY,
        cursor_val TEXT
    );
    """)

    cursor.execute("CREATE INDEX idx_corpus_freq ON corpus_words (freq DESC);")
    cursor.execute("CREATE INDEX idx_usage_freq ON usage_words (confirmed_freq DESC);")
    cursor.execute("CREATE INDEX idx_bigrams_prev ON bigrams (prev_word, freq DESC);")
    cursor.execute("CREATE INDEX idx_typo_bad ON typo_confusion (bad_token, count DESC);")

    now = int(time.time() * 1000)

    # Insert corpus words
    word_rows = []
    for word, entry in words_acc.items():
        if entry['count'] >= min_freq:
            word_rows.append((
                word,
                entry['display'],
                float(entry['count']),
                entry['pos_start'],
                entry['pos_mid'],
                entry['pos_end'],
                0,
                now
            ))

    cursor.executemany("""
    INSERT INTO corpus_words (word, display_word, freq, pos_start, pos_mid, pos_end, is_custom, last_updated)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?);
    """, word_rows)

    # Insert bigrams
    bigram_rows = []
    for (prev_w, next_w), freq in bigrams_acc.items():
        if freq >= min_freq:
            bigram_rows.append((prev_w, next_w, float(freq)))

    cursor.executemany("""
    INSERT INTO bigrams (prev_word, next_word, freq)
    VALUES (?, ?, ?);
    """, bigram_rows)

    conn.commit()

    # Seed baseline typo confusion patterns
    common_typos = [
        ('okiwa', 'okies', 'neighbor_slip'),
        ('teh', 'the', 'transposition'),
        ('dont', "don't", 'apostrophe'),
        ('cant', "can't", 'apostrophe'),
        ('wont', "won't", 'apostrophe'),
        ('im', "i'm", 'apostrophe'),
    ]
    cursor.executemany("""
    INSERT OR IGNORE INTO typo_confusion (bad_token, intended_token, slip_type, count, last_seen)
    VALUES (?, ?, ?, 5, ?);
    """, [(bad, intent, slip, now) for bad, intent, slip in common_typos])

    conn.commit()
    cursor.execute("PRAGMA wal_checkpoint(TRUNCATE);")
    conn.close()

    return len(word_rows), len(bigram_rows)

def main():
    parser = argparse.ArgumentParser(description="Kit-IME Corpus & Predictor DB Builder 🐾")
    parser.add_argument("paths", nargs="+", help="Input directories or files to scan (Markdown, txt)")
    parser.add_argument("-o", "--output", default="kit_predictor.db", help="Target SQLite DB path (default: kit_predictor.db)")
    parser.add_argument("--min-freq", type=int, default=1, help="Minimum word frequency threshold to include in DB")

    args = parser.parse_args()

    print(f"🐾 [1/3] Scanning {len(args.paths)} source path(s)...")
    words_acc, bigrams_acc, total_files, total_tokens = parse_files(args.paths)
    print(f"✨ [2/3] Scanned {total_files} file(s), extracted {total_tokens} tokens ({len(words_acc)} unique words, {len(bigrams_acc)} bigrams).")

    print(f"💜 [3/3] Compiling SQLite WAL predictor database into: {args.output}...")
    n_words, n_bigrams = compile_database(args.output, words_acc, bigrams_acc, args.min_freq)
    print(f"✨ Successfully compiled database with {n_words} corpus words and {n_bigrams} bigrams! 🐾")

if __name__ == '__main__':
    main()
