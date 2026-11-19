package com.medireport.portal.util

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Catches any uncaught crash, saves the stack trace and shows it on screen (CrashActivity runs in its own
 * process), so the problem can be copied or shared without a computer.
 */
object CrashReporter {
    const val EXTRA_TEXT = "crash_text"
    const val FILE_NAME = "last_crash.txt"

    fun install(app: Application) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = report(app, thread, error)
                runCatching { File(app.filesDir, FILE_NAME).writeText(text) }
                val intent = Intent(app, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_TEXT, text.take(150_000))
                app.startActivity(intent)
                Thread.sleep(400)
            } catch (_: Throwable) {
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** True inside the separate ":crash" process, where the app must not start its normal services. */
    fun isCrashProcess(): Boolean = try {
        File("/proc/self/cmdline").readText().trim('\u0000', ' ').endsWith(":crash")
    } catch (_: Exception) {
        false
    }

    private fun report(app: Application, thread: Thread, error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val version = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        return buildString {
            appendLine("MediReport crash report")
            appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("App: $version   Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(sw.toString())
        }
    }
}
