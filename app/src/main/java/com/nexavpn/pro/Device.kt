package com.nexavpn.pro

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object Device {
    /**
     * Identifiant de l'appareil sous le format requis : NEXA-XXXX-XXXX-XXXX.
     * Compatible avec la fonction Supabase activate_vpn_token qui exige un préfixe NEXA-.
     */
    fun id(ctx: Context): String {
        val a = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val seed = if (a.length >= 8) a else "nexa-device-seed"
        val h = MessageDigest.getInstance("SHA-256")
            .digest(("nexa:" + seed).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
        return "NEXA-" + h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12)
    }
}
