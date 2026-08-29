package com.uzairansar.hermex.core.model

import java.io.File

enum class SessionExportFormat(
    val wireValue: String,
    val fileExtension: String,
    val mimeType: String,
) {
    Html("html", "html", "text/html"),
    Json("json", "json", "application/json"),
}

data class SessionExportFile(
    val file: File,
    val filename: String,
    val mimeType: String,
)
