package com.nrw.filefinder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: FileAdapter
    private lateinit var status: TextView

    // Incremented on every new query; a running search stops when it no longer matches.
    @Volatile private var searchToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        val list = findViewById<RecyclerView>(R.id.list)
        adapter = FileAdapter { openFile(it) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<EditText>(R.id.query).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                startSearch((s?.toString() ?: "").trim())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        ensureStoragePermission()
    }

    override fun onResume() {
        super.onResume()
        status.text = if (hasAllFilesAccess()) "" else "ยังไม่ได้อนุญาตเข้าถึงไฟล์ทั้งเครื่อง"
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager() else true
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun startSearch(query: String) {
        val token = ++searchToken
        if (query.isEmpty()) {
            adapter.submit(emptyList())
            status.text = ""
            return
        }
        status.text = "กำลังค้นหา..."
        Thread {
            val needle = query.lowercase()
            val results = ArrayList<File>()
            val stack = ArrayDeque<File>()
            Environment.getExternalStorageDirectory()?.let { if (it.exists()) stack.addLast(it) }

            loop@ while (stack.isNotEmpty()) {
                if (token != searchToken) return@Thread
                val children = stack.removeLast().listFiles() ?: continue
                for (f in children) {
                    if (token != searchToken) return@Thread
                    if (f.isDirectory) {
                        stack.addLast(f)
                    } else if (f.name.lowercase().contains(needle)) {
                        results.add(f)
                        if (results.size >= MAX_RESULTS) break@loop
                    }
                }
            }

            if (token == searchToken) {
                val snapshot = results.toList()
                runOnUiThread {
                    adapter.submit(snapshot)
                    status.text = "พบ ${snapshot.size} ไฟล์" +
                        if (snapshot.size >= MAX_RESULTS) " (แสดง $MAX_RESULTS แรก)" else ""
                }
            }
        }.start()
    }

    private fun openFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "เปิดไฟล์ด้วย"))
        } catch (e: Exception) {
            status.text = "เปิดไฟล์ไม่ได้: ${e.message}"
        }
    }

    companion object {
        private const val MAX_RESULTS = 500
    }
}
