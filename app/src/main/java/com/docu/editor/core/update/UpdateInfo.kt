package com.docu.editor.core.update

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeMb: Float = 0f
)
