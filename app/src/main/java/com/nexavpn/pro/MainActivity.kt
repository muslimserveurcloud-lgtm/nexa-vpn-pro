package com.nexavpn.pro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexaTheme {
                AppRoot(vm)
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Fichier .nexa ouvert avec l'application, ou partagé vers elle. */
    private fun handle(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_VIEW -> i.data?.let { vm.importUri(it) }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) vm.importUri(uri) else vm.importText(i.getStringExtra(Intent.EXTRA_TEXT))
            }
        }
    }
}
