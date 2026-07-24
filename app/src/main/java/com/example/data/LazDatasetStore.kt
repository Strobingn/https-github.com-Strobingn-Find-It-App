package com.example.data

import java.io.File

class LazDatasetStore(
    private val directory: File,
) {
    init {
        directory.mkdirs()
    }

    fun destinationFor(name: String): File {
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(directory, safeName)
    }

    fun list(): List<File> = directory.listFiles()
        ?.filter { it.extension.lowercase() in setOf("laz", "las") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}
