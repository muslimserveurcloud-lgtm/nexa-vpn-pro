package com.nexavpn.pro

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic des fermetures inattendues, sans ordinateur :
 *  - plantage Java : pile d'appel enregistrée ;
 *  - fermeture pendant la connexion (plantage natif) : drapeau « en cours » resté levé au lancement suivant ;
 *  - dernières étapes de connexion, conservées sur disque ;
 *  - lignes du journal système qui concernent l'application (une app peut lire ses propres journaux).
 */
object CrashLog {
    private var app: Application? = null

    /** Texte affiché dans la carte « Diagnostic » de l'accueil (vide = rien à signaler). */
    var report by mutableStateOf("")

    private fun dir(): File? = app?.filesDir
    private fun crashFile(): File? = dir()?.let { File(it, "crash.txt") }
    private fun trailFile(): File? = dir()?.let { File(it, "trail.txt") }
    private fun reportFile(): File? = dir()?.let { File(it, "report.txt") }
    private fun prefs() = app?.getSharedPreferences("mvp_settings", Context.MODE_PRIVATE)

    fun init(a: Application) {
        app = a
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                crashFile()?.writeText(
                    "Plantage Java dans « ${t.name} »\n" + android.util.Log.getStackTraceString(e).take(2500)
                )
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }

        val wasRunning = prefs()?.getBoolean("running", false) ?: false
        if (wasRunning) {
            // L'application a été tuée pendant la phase de connexion : on rassemble les indices.
            Thread {
                val txt = buildAbnormalReport()
                try { reportFile()?.writeText(txt) } catch (_: Throwable) {}
                prefs()?.edit()?.putBoolean("running", false)?.commit()
                report = txt
            }.start()
        } else {
            report = buildReport()
        }
    }

    /** Lève/baisse le drapeau « connexion en cours ». Écrit de façon synchrone : on peut être tué juste après. */
    fun setRunning(v: Boolean) {
        try { prefs()?.edit()?.putBoolean("running", v)?.commit() } catch (_: Throwable) {}
    }

    /** Note une étape sur disque, pour la retrouver même si le processus est tué net. */
    fun mark(msg: String) {
        try {
            val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            trailFile()?.appendText("$t  $msg\n")
        } catch (_: Throwable) {
        }
    }

    fun resetTrail() {
        try { trailFile()?.delete() } catch (_: Throwable) {}
    }

    fun clear() {
        try { crashFile()?.delete() } catch (_: Throwable) {}
        try { trailFile()?.delete() } catch (_: Throwable) {}
        try { reportFile()?.delete() } catch (_: Throwable) {}
        prefs()?.edit()?.putBoolean("running", false)?.putLong("exit_seen", System.currentTimeMillis())?.apply()
        report = ""
    }

    private fun header(): String =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL} · " +
            "ABI ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} · app v$APP_VERSION"

    private fun trailText(): String {
        val tf = trailFile()
        return if (tf != null && tf.exists()) tf.readLines().takeLast(14).joinToString("\n") else "(aucune étape enregistrée)"
    }

    /** Lignes du journal système propres à l'application (plantages natifs, erreurs JNI, chargement de bibliothèque). */
    private fun systemLogLines(): List<String> = try {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1500", "-v", "time"))
        val all = p.inputStream.bufferedReader().readLines()
        val priority = listOf(
            "jni detected", "abort message", "in call to", "fatal signal", "fatal exception",
            "unsatisfiedlink", "dlopen failed", "registernatives", "jniabort"
        )
        val keys = listOf(
            "fatal", "abort", "jni", "hev", "tproxy", "androidruntime", "muslim", "besttunnel", "signal",
            "dlopen", "unsatisfied", "registernatives", "linker", "sigsegv", "sigabrt"
        )
        val first = all.filter { l -> val x = l.lowercase(); priority.any { x.contains(it) } }.takeLast(10)
        val rest = all.filter { l -> val x = l.lowercase(); keys.any { x.contains(it) } && l !in first }.takeLast(15)
        (first + rest).map { it.take(240) }
    } catch (e: Throwable) {
        listOf("(lecture du journal système impossible : ${e.message})")
    }

    /** Dernières lignes utiles du journal système de l'application. */
    fun recentSystemLog(): List<String> = systemLogLines()

    private fun buildAbnormalReport(): String {
        val sb = StringBuilder()
        sb.append(header()).append("\n\n")
        sb.append("L'application a été fermée pendant la connexion.\n\n")
        val cf = crashFile()
        if (cf != null && cf.exists()) sb.append(cf.readText()).append("\n\n")
        sb.append("Dernières étapes :\n").append(trailText()).append("\n\n")
        sb.append("Journal système (extraits) :\n")
        val lines = systemLogLines()
        sb.append(if (lines.isEmpty()) "(rien de trouvé)" else lines.joinToString("\n"))
        return sb.toString().trim()
    }

    private fun reasonName(r: Int): String = when (r) {
        2 -> "signal reçu (SIGNALED)"
        3 -> "manque de mémoire"
        4 -> "plantage Java"
        5 -> "plantage NATIF (bibliothèque C)"
        6 -> "application ne répond plus (ANR)"
        else -> "code $r"
    }

    private fun buildReport(): String {
        val a = app ?: return ""
        val sb = StringBuilder()

        val rf = reportFile()
        if (rf != null && rf.exists()) sb.append(rf.readText()).append("\n\n")

        val cf = crashFile()
        if (cf != null && cf.exists()) sb.append(cf.readText()).append("\n\n")

        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val am = a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val seen = a.getSharedPreferences("mvp_settings", Context.MODE_PRIVATE).getLong("exit_seen", 0L)
                val info = am.getHistoricalProcessExitReasons(a.packageName, 0, 1).firstOrNull()
                if (info != null && info.timestamp > seen && info.reason in setOf(2, 3, 4, 5, 6)) {
                    sb.append("Fin anormale du processus : ").append(reasonName(info.reason))
                        .append(" — ").append(info.description ?: "")
                        .append(" (statut ").append(info.status).append(")\n\n")
                }
            } catch (_: Throwable) {
            }
        }

        if (sb.isNotEmpty() && !sb.contains("Dernières étapes")) {
            sb.append(header()).append("\n\nDernières étapes :\n").append(trailText())
        }
        return sb.toString().trim()
    }
}
