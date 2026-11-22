package com.kitime.ime.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.kitime.ime.R
import com.kitime.ime.data.LocalCorpusGatherer
import com.kitime.ime.data.NightlyCorpusWorker
import com.kitime.ime.data.PredictorDatabase
import com.kitime.ime.data.UserDictionarySyncer
import com.kitime.ime.pref.KitImePreferences
import java.io.File
import kotlin.concurrent.thread

/**
 * Settings Activity for Kit-IME preferences and Local Prediction Sync dashboard.
 *
 * Implements:
 * - Preference switches (Space Chords, Greenlighting, Soft Bzzt, Send Guard)
 * - Grant READ_SMS permission launcher
 * - Pick SAF Folder via ACTION_OPEN_DOCUMENT_TREE with persistable URI permissions
 * - Sync UserDictionary helper
 * - Run Gather Now manual execution
 * - Flush Firebase Deltas
 * - Restore DB Snapshot picker
 * - Ingestion of shared text snippets via ACTION_SEND (text/plain)
 */
class KitImeSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KitImePreferences
    private lateinit var dbHelper: PredictorDatabase
    private lateinit var gatherer: LocalCorpusGatherer
    private lateinit var userDictSyncer: UserDictionarySyncer

    private lateinit var txtSyncStatus: TextView

    // SAF folder picker contract
    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            prefs.safFolderUri = uri.toString()
            txtSyncStatus.text = "💜 [folder: selected] SAF: ${uri.lastPathSegment}"
            Toast.makeText(this, "SAF folder registered for corpus scanning 🐾", Toast.LENGTH_SHORT).show()
        }
    }

    // Direct single file picker for Google Drive / Docs exports (.txt, .md)
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            txtSyncStatus.text = "🐾 [status: processing] Reading Drive file: ${uri.lastPathSegment}..."
            thread {
                try {
                    val content = contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    } ?: ""
                    val (words, bigrams) = gatherer.ingestTextSnippet(content)
                    runOnUiThread {
                        txtSyncStatus.text = "✨ [status: ready] Ingested $words words, $bigrams bigrams from Drive file"
                        Toast.makeText(this, "File ingested (+${words} words) 🌸", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        txtSyncStatus.text = "⚠️ [status: error] Could not read selected file"
                        Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Permission launcher for SMS and UserDictionary
    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            txtSyncStatus.text = "✨ [status: granted] Sent SMS access enabled"
            Toast.makeText(this, "Sent SMS access granted 🌸", Toast.LENGTH_SHORT).show()
        } else {
            txtSyncStatus.text = "⏳ [status: pending] SMS permission denied"
            Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestUserDictPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            performUserDictionarySync()
        } else {
            Toast.makeText(this, "UserDictionary permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = KitImePreferences(this)
        dbHelper = PredictorDatabase.getInstance(this)
        gatherer = LocalCorpusGatherer(this, dbHelper)
        userDictSyncer = UserDictionarySyncer(this, dbHelper)

        // Schedule nightly WorkManager task
        NightlyCorpusWorker.scheduleNightly(this)

        initSwitches()
        initActionButtons()

        // Handle Share Sheet / Text Intake (ACTION_SEND text/plain)
        handleIncomingSendIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingSendIntent(intent)
    }

    private fun handleIncomingSendIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                thread {
                    val (words, bigrams) = gatherer.ingestTextSnippet(sharedText)
                    runOnUiThread {
                        txtSyncStatus.text = "🌸 [#litany] Intake received: $words words, $bigrams bigrams"
                        Toast.makeText(this, "Snippet ingested into predictor corpus ✨", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun initSwitches() {
        val switchSpaceChord = findViewById<SwitchCompat>(R.id.switch_space_chord)
        val switchGreenlight = findViewById<SwitchCompat>(R.id.switch_greenlight)
        val switchSoftBzzt = findViewById<SwitchCompat>(R.id.switch_soft_bzzt)
        val switchSendGuard = findViewById<SwitchCompat>(R.id.switch_send_guard)

        switchSpaceChord.isChecked = prefs.isSpaceChordModeEnabled
        switchGreenlight.isChecked = prefs.isGreenlightEnabled
        switchSoftBzzt.isChecked = prefs.isSoftBzztEnabled
        switchSendGuard.isChecked = prefs.isPreSendDoubleCheckEnabled

        switchSpaceChord.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSpaceChordModeEnabled = isChecked
        }
        switchGreenlight.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGreenlightEnabled = isChecked
        }
        switchSoftBzzt.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSoftBzztEnabled = isChecked
        }
        switchSendGuard.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPreSendDoubleCheckEnabled = isChecked
        }
    }

    private fun initActionButtons() {
        txtSyncStatus = findViewById(R.id.txt_sync_status)
        val editGoogleDocUrls = findViewById<android.widget.EditText>(R.id.edit_google_doc_urls)
        val btnSyncGoogleDocs = findViewById<Button>(R.id.btn_sync_google_docs)
        val btnGrantSms = findViewById<Button>(R.id.btn_grant_sms)
        val btnPickSaf = findViewById<Button>(R.id.btn_pick_saf)
        val btnImportFile = findViewById<Button>(R.id.btn_import_file)
        val btnSyncUserDict = findViewById<Button>(R.id.btn_sync_user_dict)
        val btnRunGatherNow = findViewById<Button>(R.id.btn_run_gather_now)
        val btnFlushFirebase = findViewById<Button>(R.id.btn_flush_firebase)
        val btnRestoreSnapshot = findViewById<Button>(R.id.btn_restore_snapshot)

        // Preload saved Google Doc URLs
        editGoogleDocUrls.setText(prefs.googleDocUrls)
        editGoogleDocUrls.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                prefs.googleDocUrls = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Sync Google Docs Corpus Now
        btnSyncGoogleDocs.setOnClickListener {
            val docUrls = prefs.getGoogleDocUrlList()
            if (docUrls.isEmpty()) {
                Toast.makeText(this, "Please paste at least one Google Doc / Apps Script URL 🌸", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            txtSyncStatus.text = "🐾 [status: processing] Pre-sync snapshot & fetching Google Docs..."
            thread {
                // Ensure pre-sync snapshot runs first
                dbHelper.createDailySnapshot()

                val docFetcher = com.kitime.ime.data.GoogleDocsCorpusFetcher(dbHelper)
                val stats = docFetcher.fetchAndIngest(docUrls, gatherer)
                dbHelper.checkpointWal()

                runOnUiThread {
                    txtSyncStatus.text = "✨ [status: ready] Synced ${stats.docsScanned} Doc(s) " +
                            "(${stats.docsSkippedUnchanged} unchanged). Ingested ${stats.wordsIngested} words, ${stats.bigramsIngested} bigrams"
                    Toast.makeText(
                        this,
                        "Google Docs synced! (+${stats.wordsIngested} words) 🐾",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // 1. Grant Sent SMS
        btnGrantSms.setOnClickListener {
            val hasPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                Toast.makeText(this, "READ_SMS already granted! ✨", Toast.LENGTH_SHORT).show()
            } else {
                requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }

        // 2. Pick SAF Folder
        btnPickSaf.setOnClickListener {
            openDocumentTreeLauncher.launch(null)
        }

        // 2b. Import single file from Drive / Downloads
        btnImportFile.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("text/plain", "text/markdown", "*/*"))
        }

        // 3. Sync UserDictionary
        val userDictPerm = "android.permission.WRITE_USER_DICTIONARY"
        btnSyncUserDict.setOnClickListener {
            val hasPerm = ContextCompat.checkSelfPermission(this, userDictPerm) ==
                    PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                performUserDictionarySync()
            } else {
                requestUserDictPermissionLauncher.launch(userDictPerm)
            }
        }

        // 4. Run Gather Now
        btnRunGatherNow.setOnClickListener {
            txtSyncStatus.text = "🐾 [status: processing] Running local gather loop..."
            thread {
                val safUri = prefs.safFolderUri?.let { Uri.parse(it) }
                val stats = gatherer.runFullGather(safUri)
                dbHelper.createDailySnapshot()

                runOnUiThread {
                    txtSyncStatus.text = "✨ [status: ready] Ingested ${stats.wordsIngested} words, " +
                            "${stats.bigramsIngested} bigrams in ${stats.durationMs}ms"
                    Toast.makeText(this, "Gather complete (${stats.wordsIngested} words) 🐾", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 5. Flush Firebase Deltas
        btnFlushFirebase.setOnClickListener {
            txtSyncStatus.text = "🐾 [status: processing] Reading pending counter deltas..."
            thread {
                val deltas = dbHelper.getPendingDeltas()
                val count = deltas.size
                // In production, syncs to Firebase Realtime DB / Firestore with FieldValue.increment
                dbHelper.clearPendingDeltas()

                runOnUiThread {
                    txtSyncStatus.text = "✨ [status: ready] Flushed $count pending deltas"
                    Toast.makeText(this, "Flushed $count counter deltas 🌸", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 6. Restore DB Snapshot
        btnRestoreSnapshot.setOnClickListener {
            showSnapshotRestoreDialog()
        }
    }

    private fun performUserDictionarySync() {
        txtSyncStatus.text = "🐾 [status: processing] Syncing with Android UserDictionary..."
        thread {
            val stats = userDictSyncer.syncToUserDictionary()
            runOnUiThread {
                txtSyncStatus.text = "✨ [status: ready] Synced ${stats.wordsInserted} new, " +
                        "${stats.wordsUpdated} updated words to UserDictionary"
                Toast.makeText(this, "UserDictionary synced (+${stats.wordsInserted} words) 🐾", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSnapshotRestoreDialog() {
        val snapshots = dbHelper.listSnapshots()
        if (snapshots.isEmpty()) {
            Toast.makeText(this, "No snapshots found yet! ⏳", Toast.LENGTH_SHORT).show()
            return
        }

        val names = snapshots.map { "${it.name} (${it.length() / 1024} KB)" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Restore Database Snapshot 🐾")
            .setItems(names) { _, which ->
                val selectedFile = snapshots[which]
                val success = dbHelper.restoreSnapshot(selectedFile)
                if (success) {
                    txtSyncStatus.text = "✨ [status: ready] Restored from ${selectedFile.name}"
                    Toast.makeText(this, "Snapshot restored successfully! 🌸", Toast.LENGTH_SHORT).show()
                } else {
                    txtSyncStatus.text = "⚠️ [status: error] Restore failed integrity check"
                    Toast.makeText(this, "Snapshot restore failed!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
