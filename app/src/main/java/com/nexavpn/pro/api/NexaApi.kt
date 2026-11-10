package com.nexavpn.pro.api

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nexavpn.pro.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object NexaApi {
    private const val BASE_URL = "https://nexa-token-navigator.lovable.app"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getHardwareId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
            val raw = "$androidId:${Build.MANUFACTURER}:${Build.MODEL}"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray())
            val hex = digest.take(8).joinToString("") { "%02X".format(it) }
            "NEXA-$hex"
        } catch (e: Exception) {
            "NEXA-00000000"
        }
    }

    suspend fun verifyToken(code: String, hardwareId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("token", code)
                put("device_id", hardwareId)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/public/verify-token")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val text = response.body?.string() ?: "{}"
            Result.success(JSONObject(text))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUsage(tokenId: String, bytesDelta: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("token_id", tokenId)
                put("bytes_delta", bytesDelta)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/api/public/report-usage")
                .post(body)
                .build()
            val resp = client.newCall(request).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
