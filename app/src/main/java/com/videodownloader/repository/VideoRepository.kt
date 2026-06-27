package com.videodownloader.repository

import com.videodownloader.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VideoRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // רשימת שרתים לנסות בזה אחר זה
    private val cobaltInstances = listOf(
        "https://cobalt.api.onrender.com",
        "https://co.wuk.sh",
        "https://cobalt.tools/api"
    )

    suspend fun fetchVideoInfo(url: String): Result<VideoInfo> {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    isYouTube(url) -> fetchVideo(url)
                    isVimeo(url) -> fetchVideo(url)
                    else -> Result.failure(Exception("פלטפורמה לא נתמכת. נסה YouTube או Vimeo."))
                }
            } catch (e: Exception) {
                Result.failure(Exception("שגיאה: ${e.message}"))
            }
        }
    }

    private fun isYouTube(url: String) =
        url.contains("youtube.com") || url.contains("youtu.be")

    private fun isVimeo(url: String) =
        url.contains("vimeo.com")

    private fun extractYouTubeId(url: String): String? {
        val patterns = listOf(
            Regex("v=([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Regex("embed/([a-zA-Z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private suspend fun fetchVideo(url: String): Result<VideoInfo> {
        // שלב 1: קבל מידע על הסרטון
        val title: String
        val thumbnail: String

        if (isYouTube(url)) {
            val videoId = extractYouTubeId(url)
                ?: return Result.failure(Exception("לא ניתן לחלץ מזהה הסרטון"))
            val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                title = json.optString("title", "סרטון YouTube")
            } else {
                title = "סרטון YouTube"
            }
            thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else {
            title = "סרטון Vimeo"
            thumbnail = ""
        }

        // שלב 2: נסה כל instance בזה אחר זה
        val jsonBody = JSONObject().apply {
            put("url", url)
            put("vCodec", "h264")
            put("vQuality", "720")
            put("filenamePattern", "basic")
            put("isAudioOnly", false)
        }.toString()

        for (instance in cobaltInstances) {
            try {
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$instance/api/json")
                    .post(requestBody)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val status = json.optString("status")

                val downloadUrl = when (status) {
                    "stream", "redirect", "success" -> json.optString("url", "")
                    "picker" -> {
                        val picker = json.optJSONArray("picker")
                        picker?.getJSONObject(0)?.optString("url", "") ?: ""
                    }
                    else -> continue
                }

                if (downloadUrl.isNotEmpty()) {
                    return Result.success(
                        VideoInfo(
                            title = title,
                            thumbnailUrl = thumbnail,
                            downloadUrl = downloadUrl,
                            source = if (isYouTube(url)) "YouTube" else "Vimeo"
                        )
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }

        return Result.failure(Exception("לא ניתן לקבל קישור הורדה. נסה שוב מאוחר יותר."))
    }
}
