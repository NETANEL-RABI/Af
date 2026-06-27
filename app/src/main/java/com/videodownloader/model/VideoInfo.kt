package com.videodownloader.model

data class VideoInfo(
    val title: String,
    val thumbnailUrl: String,
    val downloadUrl: String,
    val fileSize: String = "לא ידוע",
    val duration: String = "",
    val source: String = ""
)
