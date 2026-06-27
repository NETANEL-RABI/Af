package com.videodownloader.repository

import com.videodownloader.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
                    isYouTube(url) -> fetchYouTubeInfo(url)
                    isVimeo(url) -> fetchVimeoInfo(url)
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

    private fun extractVimeoId(url: String): String? {
        val pattern = Regex("vimeo\\.com/(\\d+)")
        return pattern.find(url)?.groupValues?.get(1)
    }

    private suspend fun fetchYouTubeInfo(url: String): Result<VideoInfo> {
        val videoId = extractYouTubeId(url)
            ?: return Result.failure(Exception("לא ניתן לחלץ מזהה הסרטון"))

        // שימוש ב-yt-dlp API ציבורי
        val apiUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"

        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return Result.failure(Exception("לא ניתן לטעון את פרטי הסרטון"))
        }

        val body = response.body?.string()
            ?: return Result.failure(Exception("תגובה ריקה מהשרת"))

        val json = JSONObject(body)
        val title = json.optString("title", "סרטון YouTube")
        val thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

        // שימוש בשירות הורדה חינמי
        val downloadUrl = "https://www.y2mate.com/youtube/$videoId"

        return Result.success(
            VideoInfo(
                title = title,
                thumbnailUrl = thumbnail,
                downloadUrl = downloadUrl,
                source = "YouTube"
            )
        )
    }

    private suspend fun fetchVimeoInfo(url: String): Result<VideoInfo> {
        val videoId = extractVimeoId(url)
            ?: return Result.failure(Exception("לא ניתן לחלץ מזהה הסרטון"))

        val apiUrl = "https://vimeo.com/api/v2/video/$videoId.json"

        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return Result.failure(Exception("לא ניתן לטעון את פרטי הסרטון"))
        }

        val body = response.body?.string()
            ?: return Result.failure(Exception("תגובה ריקה מהשרת"))

        val json = org.json.JSONArray(body).getJSONObject(0)
        val title = json.optString("title", "סרטון Vimeo")
        val thumbnail = json.optString("thumbnail_large", "")
        val downloadUrl = json.optString("url", url)

        return Result.success(
            VideoInfo(
                title = title,
                thumbnailUrl = thumbnail,
                downloadUrl = downloadUrl,
                source = "Vimeo"
            )
        )
    }
}
