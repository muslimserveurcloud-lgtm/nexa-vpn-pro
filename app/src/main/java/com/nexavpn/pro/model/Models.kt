package com.nexavpn.pro.model

import org.json.JSONObject

enum class ProtocolType {
    SSH, UDP, DNST, VMESS, VLESS, TROJAN, VITORA, UNKNOWN;

    companion object {
        fun fromString(type: String?): ProtocolType = when (type?.lowercase()) {
            "ssh" -> SSH
            "udp" -> UDP
            "dnst" -> DNST
            "vmess" -> VMESS
            "vless" -> VLESS
            "trojan" -> TROJAN
            "vitora" -> VITORA
            else -> UNKNOWN
        }
    }
}

data class ServerConfig(
    val id: String,
    val name: String,
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val country: String?,
    val network: String?,
    val tls: Boolean,
    val sni: String?,
    val path: String?,
    val username: String? = null,
    val password: String? = null,
    val uuid: String? = null,
    val rawConfig: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): ServerConfig {
            return ServerConfig(
                id = json.optString("id"),
                name = json.optString("name", "Serveur NEXA"),
                protocol = ProtocolType.fromString(json.optString("protocol")),
                host = json.optString("host", "127.0.0.1"),
                port = json.optInt("port", 443),
                country = json.optString("country", "FR"),
                network = json.optString("network", null),
                tls = json.optBoolean("tls", true),
                sni = json.optString("sni", null),
                path = json.optString("path", null),
                username = json.optString("username", null),
                password = json.optString("password", null),
                uuid = json.optString("uuid", null),
                rawConfig = json.optString("raw_config", null)
            )
        }
    }
}

data class TokenInfo(
    val id: String,
    val code: String,
    val status: String,
    val deviceId: String?,
    val durationDays: Int,
    val dataLimitBytes: Long,
    val dataUsedBytes: Long,
    val expiresAt: String?
)
