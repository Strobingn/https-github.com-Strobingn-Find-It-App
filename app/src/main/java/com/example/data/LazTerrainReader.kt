package com.example.data

import com.github.mreutegg.laszip4j.LASReader
import java.io.BufferedInputStream
import java.io.InputStream

/** Pure-Java LAZ decoding backed by laszip4j; all rasterization remains memory bounded. */
internal object LazTerrainReader {
    fun read(
        inputStream: InputStream,
        options: LidarImportOptions,
    ): DemGenerator.LasLoadResult? {
        return try {
            val buffered = if (inputStream is BufferedInputStream) {
                inputStream
            } else {
                BufferedInputStream(inputStream, 256 * 1024)
            }
            
            // Mark the stream so we can reset it after reading the header
            buffered.mark(1024 * 1024) // Mark with a generous limit (1MB)
            
            // Use LASReader to get the header - this ensures correct parsing
            val lasHeader = LASReader.getHeader(buffered) ?: run {
                buffered.reset()
                return null
            }
            
            // Validate the header has required fields
            val scaleX = lasHeader.getXScaleFactor()
            val scaleY = lasHeader.getYScaleFactor()
            val scaleZ = lasHeader.getZScaleFactor()
            val offsetX = lasHeader.getXOffset()
            val offsetY = lasHeader.getYOffset()
            val offsetZ = lasHeader.getZOffset()
            val maxX = lasHeader.getMaxX()
            val minX = lasHeader.getMinX()
            val maxY = lasHeader.getMaxY()
            val minY = lasHeader.getMinY()
            
            if (!listOf(scaleX, scaleY, scaleZ, offsetX, offsetY, offsetZ, maxX, minX, maxY, minY)
                    .all { it.isFinite() } || maxX <= minX || maxY <= minY
            ) {
                buffered.reset()
                return null
            }
            
            val pointCount = lasHeader.getNumberOfPointRecords()
            val versionMajor = lasHeader.getVersionMajor().toInt() and 0xFF
            val versionMinor = lasHeader.getVersionMinor().toInt() and 0xFF
            val pointFormat = lasHeader.getPointDataRecordFormat().toInt() and 0x3F
            
            // Reset the stream so LASReader.getPoints() can read from the beginning
            buffered.reset()
            
            val rasterizer = LidarRasterizer(
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                options = options,
                declaredPointCount = pointCount,
            )

            // LASReader.getPoints() will internally create a reader and handle the stream
            for (point in LASReader.getPoints(buffered)) {
                val x = point.getX() * scaleX + offsetX
                val y = point.getY() * scaleY + offsetY
                val z = (point.getZ() * scaleZ + offsetZ).toFloat()
                if (!rasterizer.addPoint(
                        x = x,
                        y = y,
                        z = z,
                        classification = point.getClassification().toInt(),
                        isKeyPoint = point.isKeyPoint(),
                    )
                ) {
                    break
                }
            }
            rasterizer.finish(
                pointFormat = pointFormat,
                sourceLabel = "LAZ ${versionMajor}.${versionMinor} format ${pointFormat}",
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
    }
}
