package com.example.data

import java.io.File
import java.util.Locale

data class LazDataset(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

/** Persistent app-private storage for downloaded LAZ/LAS datasets. */
class LazDatasetStore(
    val directory: File,
) {
    init {
        directory.mkdirs()
    }

    fun list(): List<LazDataset> = directory.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.extension.lowercase(Locale.US) in setOf("laz", "las") }
        ?.map {
            LazDataset(
                file = it,
                displayName = it.name,
                sizeBytes = it.length(),
                modifiedAtMillis = it.lastModified(),
            )
        }
        ?.sortedByDescending { it.modifiedAtMillis }
        ?.toList()
        ?: emptyList()

    fun contains(file: File): Boolean {
        return runCatching {
            file.canonicalFile.parentFile == directory.canonicalFile && file.exists()
        }.getOrDefault(false)
    }
}
