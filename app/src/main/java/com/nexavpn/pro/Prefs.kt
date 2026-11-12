package com.nexavpn.pro

import android.content.Context

/** Réglages de l'utilisateur (aucune donnée sensible). */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("mvp_settings", Context.MODE_PRIVATE)

    var mtu: Int
        get() = p.getInt("mtu", 8500)
        set(v) = p.edit().putInt("mtu", v).apply()

    /** Paquets d'applications qui contournent le VPN (split tunneling). */
    var excluded: Set<String>
        get() = HashSet(p.getStringSet("excluded", emptySet()) ?: emptySet())
        set(v) = p.edit().putStringSet("excluded", HashSet(v)).apply()
}
