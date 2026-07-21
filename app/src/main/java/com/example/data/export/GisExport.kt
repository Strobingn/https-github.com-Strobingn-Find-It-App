package com.example.data.export

import com.example.data.TargetSignal
import java.util.Locale

fun buildCsv(signals: List<TargetSignal>): String = buildString {
    append("ID,GridX,GridY,Latitude,Longitude,Type,Source,SignalStrength,DepthCm,Status,Notes\n")
    signals.forEach { signal ->
        val fields = listOf(
            signal.id.toString(),
            formatDecimal(signal.gridX.toDouble(), 3),
            formatDecimal(signal.gridY.toDouble(), 3),
            signal.latitude?.let { formatDecimal(it, 7) }.orEmpty(),
            signal.longitude?.let { formatDecimal(it, 7) }.orEmpty(),
            signal.metalType.label,
            signal.source.name,
            formatDecimal(signal.signalStrength.toDouble(), 1),
            signal.depthCm?.toString().orEmpty(),
            signal.status,
            signal.notes,
        )
        append(fields.joinToString(",") { csvEscape(it) }).append('\n')
    }
}

fun buildGpx(signals: List<TargetSignal>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<gpx version=\"1.1\" creator=\"Find It\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
    signals.forEach { signal ->
        val lat = signal.latitude ?: return@forEach
        val lon = signal.longitude ?: return@forEach
        append("  <wpt lat=\"").append(formatDecimal(lat, 7)).append("\" lon=\"")
            .append(formatDecimal(lon, 7)).append("\">\n")
        append("    <name>").append(xmlEscape(signal.metalType.label)).append("</name>\n")
        val description = buildString {
            append("Source: ${signal.source.name}; Strength: ${signal.signalStrength.toInt()}%; ")
            append(signal.depthCm?.let { "Depth: $it cm; " } ?: "Depth: unknown; ")
            append("Status: ${signal.status}")
            if (signal.notes.isNotBlank()) append("; Notes: ${signal.notes}")
        }
        append("    <desc>").append(xmlEscape(description)).append("</desc>\n")
        append("  </wpt>\n")
    }
    append("</gpx>\n")
}

private fun formatDecimal(value: Double, places: Int) =
    String.format(Locale.US, "%.${places}f", value)

private fun csvEscape(value: String) = "\"${value.replace("\"", "\"\"")}\""

private fun xmlEscape(value: String) = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
