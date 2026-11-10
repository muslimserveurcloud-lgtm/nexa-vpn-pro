package com.nexavpn.pro

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NexaApplication : Application() {

    companion object {
        const val PREFS_NAME = "nexa_vpn_prefs"
        const val KEY_CONNECTING_FLAG = "is_connecting"
        const val KEY_LAST_CRASH = "last_crash_report"
        lateinit var instance: NexaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val report = buildString {
                    append("=== RAPPORT DE CRASH NEXA VPN PRO ===\n")
                    append("Date: $time\n")
                    append("Modèle: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
                    append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    append("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
                    append("Thread: ${thread.name}\n\n")
                    append("--- TRACE D'ERREUR ---\n")
                    append(stackTrace)
                }

                // Sauvegarder dans les préférences et sur le disque
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_LAST_CRASH, report).apply()

                val crashFile = File(filesDir, "crash.log")
                crashFile.writeText(report)
                Log.e("NexaApp", "Crash enregistré: $report")
            } catch (e: Exception) {
                Log.e("NexaApp", "Échec sauvegarde crash", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
