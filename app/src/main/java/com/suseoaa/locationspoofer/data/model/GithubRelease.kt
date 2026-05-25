package com.suseoaa.locationspoofer.data.model

data class GithubRelease(
    val versionName: String,
    val body: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val isPrerelease: Boolean
)
