package com.nexavpn.pro

import android.app.Application
import android.os.Build
import java.io.File

class NexaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Le processus de test (":probe") ne doit ni lire ni effacer le diagnostic du processus principal.
        if (isMainProcess()) CrashLog.init(this)
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            try {
                File("/proc/self/cmdline").readText().trim('\u0000')
            } catch (_: Exception) {
                packageName
            }
        }
        return name == packageName
    }
}
