package nodomain.freeyourgadget.gadgetbridge.util

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.activities.charts.EcgDetailActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object EcgExportHelper {
    private val LOG = LoggerFactory.getLogger(EcgExportHelper::class.java)
    private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)

    fun writeCsv(context: Context, device: GBDevice, record: EcgRecord, samples: FloatArray): File {
        val exportDir = File(context.cacheDir, "csv")
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            LOG.warn("Failed to create {}", exportDir)
        }

        val start = Instant.ofEpochMilli(record.startTimestamp).atZone(ZoneId.systemDefault())
        val file = File(exportDir, "ecg_${FILE_DATE_FORMAT.format(start)}.csv")

        file.bufferedWriter().use { writer ->
            writer.appendLine("# Gadgetbridge ECG export")
            writer.appendLine("# Device: ${device.aliasOrName}")
            writer.appendLine("# Model: ${context.getString(device.deviceCoordinator.deviceNameResource)}")
            writer.appendLine("# Start: ${DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(start)}")
            writer.appendLine("# Sample rate (Hz): ${record.sampleRateHz}")
            record.averageHeartRate?.let { writer.appendLine("# Average heart rate (bpm): $it") }
            writer.appendLine("# Classification: ${EcgDetailActivity.formatClassification(context, record)}")
            for (extra in record.extras) {
                writer.appendLine(
                    "# ${context.getString(extra.label)}: ${
                        context.getString(
                            extra.format,
                            *extra.formatArgs
                        )
                    }"
                )
            }
            writer.appendLine("time_s,value")
            for ((index, value) in samples.withIndex()) {
                writer.append(String.format(Locale.ROOT, "%.4f", index / record.sampleRateHz))
                    .append(',')
                    .append(if (value.isFinite()) BigDecimal(value.toString()).toPlainString() else value.toString())
                    .append('\n')
            }
        }

        LOG.debug("Exported ECG at {} with {} samples to {}", record.startTimestamp, samples.size, file)
        return file
    }
}
