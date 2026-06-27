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

    suspend fun fetchVideoInfo(url: String): Result<VideoInfo> {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    isYouTube(url) -> fetchViaCobalt(url)
                    isVimeo(url) -> fetchViaCobalt(url)
                    else -> Result.failure(Exception("פלטפורמה לא נתמכת. נסה YouTube או Vimeo."))
                }
            } catch (e: Exception) {
                Result.failure(Exception("שגיאה: ${e.message}"))
            }
        }
    }

    private fun isYouTube(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    private fun isVimeo(url: String): Boolean {
        return url.contains("vimeo.com")
    }

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

    private suspend fun fetchViaCobalt(url: String): Result<VideoInfo> {
        // שלב 1: קבל שם סרטון ותמונה מ-oEmbed
        val title: String
        val thumbnail: String

        if (isYouTube(url)) {
            val videoId = extractYouTubeId(url)
                ?: return Result.failure(Exception("לא ניתן לחלץ מזהה הסרטון"))
            val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val oEmbedRequest = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            val oEmbedResponse = client.newCall(oEmbedRequest).execute()
            val oEmbedBody = oEmbedResponse.body?.string() ?: ""
            val oEmbedJson = JSONObject(oEmbedBody)
            title = oEmbedJson.optString("title", "סרטון YouTube")
            thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else {
            title = "סרטון"
            thumbnail = ""
        }

        // שלב 2: קבל קישור הורדה ישיר מ-cobalt.tools
        val jsonBody = JSONObject().apply {
            put("url", url)
            put("vCodec", "h264")
            put("vQuality", "720")
            put("filenamePattern", "basic")
        }.toString()

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val cobaltRequest = Request.Builder()
            .url("https://api.cobalt.tools/api/json")
            .post(requestBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        val cobaltResponse = client.newCall(cobaltRequest).execute()

        if (!cobaltResponse.isSuccessful) {
            return Result.failure(Exception("שגיאה בקבלת קישור הורדה (${cobaltResponse.code})"))
        }

        val cobaltBody = cobaltResponse.body?.string()
            ?: return Result.failure(Exception("תגובה ריקה מהשרת"))

        val cobaltJson = JSONObject(cobaltBody)
        val status = cobaltJson.optString("status")

        val downloadUrl = when (status) {
            "stream", "redirect", "success" -> cobaltJson.optString("url", "")
            "picker" -> {
                val picker = cobaltJson.optJSONArray("picker")
                picker?.getJSONObject(0)?.optString("url", "") ?: ""
            }
            else -> return Result.failure(Exception("לא ניתן לקבל קישור הורדה: $status"))
        }

        if (downloadUrl.isEmpty()) {
            return Result.failure(Exception("קישור הורדה ריק"))
        }

        return Result.success(
            VideoInfo(
                title = title,
                thumbnailUrl = thumbnail,
                downloadUrl = downloadUrl,
                source = if (isYouTube(url)) "YouTube" else "Vimeo"
            )
        )
    }
}
