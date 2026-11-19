package com.medireport.portal.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Plain-Android screen (no Compose, no app theme, no database) that shows the last crash. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(CrashReporter.EXTRA_TEXT)
            ?: runCatching { File(filesDir, CrashReporter.FILE_NAME).readText() }.getOrDefault("No crash details found.")

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }

        root.addView(TextView(this).apply {
            this.text = "অ্যাপ ক্র্যাশ করেছে / The app crashed"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            this.text = "নিচের লেখাটি কপি করে (বা শেয়ার করে) ডেভেলপারকে পাঠান।\nCopy or share this text so the problem can be fixed."
            textSize = 13f
            setPadding(0, pad / 2, 0, pad / 2)
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            this.text = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(button("Copy") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("crash", text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        })
        row.addView(button("Share") {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share crash report"
                )
            )
        })
        row.addView(button("Close") { finishAndRemoveTask() })
        root.addView(row)

        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            this.text = text
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
