package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Locale

class LazImportRepository(
    private val downloader: LazDownloadManager,
) {
    suspend fun importFromUrl(
        url: String,
        store: LazDatasetStore,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(isSupportedRemoteUrl(url)) { "Enter a direct HTTPS LAZ or LAS download URL" }
        downloader.download(
            sourceUrl = url,
            destinationDirectory = store.directory,
            progress = onProgress,
        )
    }

    fun isSupportedRemoteUrl(url: String): Boolean {
        return runCatching {
            val parsed = URL(url.trim())
            parsed.protocol.equals("https", ignoreCase = true) && parsed.host.isNotBlank()
        }.getOrDefault(false)
    }

    fun looksLikeLazUrl(url: String): Boolean {
        val path = runCatching { URL(url.trim()).path }.getOrDefault(url)
        return path.substringAfterLast('.', "").lowercase(Locale.US) in setOf("laz", "las")
    }
}

object NoaaLidarCatalog {
    const val DATA_VIEWER_URL = "https://coast.noaa.gov/dataviewer/#/lidar/search"
}
