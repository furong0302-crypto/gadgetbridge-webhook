package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addStatTileGrid
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord
import nodomain.freeyourgadget.gadgetbridge.model.EcgSample
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.EcgExportHelper
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import org.slf4j.LoggerFactory
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class EcgDetailActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var waveformChart: EcgWaveformChart
    private var record: EcgRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getDevice() ?: throw IllegalArgumentException("${GBDevice.EXTRA_DEVICE} must not be null")
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1)

        setContentView(R.layout.activity_ecg_detail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val chart = findViewById<LineChart>(R.id.ecg_line_chart)
        waveformChart = EcgWaveformChart(this, chart)
        setupChartTouchHandling(chart)

        lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) { load(recordId) }
            } catch (e: Exception) {
                LOG.error("Failed to load ECG record {}", recordId, e)
                null
            }
            if (loaded == null) {
                LOG.warn("ECG record {} not found", recordId)
                finish()
                return@launch
            }
            record = loaded.first
            render(loaded.first, loaded.second)
            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ecg_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.ecg_detail_share).isVisible = record != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }

        R.id.ecg_detail_share -> {
            record?.let(::shareCsv)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun load(recordId: Long): Pair<EcgRecord, List<EcgSample>>? = GBApplication.acquireDbReadOnly().use { db ->
        val provider = device.deviceCoordinator.getEcgDataProvider(device, db.daoSession) ?: return null
        val record = provider.records.firstOrNull { it.id == recordId } ?: return null
        record to provider.getDisplaySamples(record)
    }

    private fun shareCsv(record: EcgRecord) {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val samples = GBApplication.acquireDbReadOnly().use { db ->
                        device.deviceCoordinator.getEcgDataProvider(device, db.daoSession)?.getSamples(record)
                    } ?: FloatArray(0)
                    EcgExportHelper.writeCsv(this@EcgDetailActivity, device, record, samples)
                }
                AndroidUtils.shareFile(this@EcgDetailActivity, file, "text/csv")
            } catch (e: Exception) {
                GB.toast(
                    this@EcgDetailActivity,
                    getString(R.string.activity_error_share_failed),
                    Toast.LENGTH_LONG,
                    GB.ERROR,
                    e
                )
            }
        }
    }

    private fun render(record: EcgRecord, waveform: List<EcgSample>) {
        val start = DateTimeUtils.formatDateTimeRelative(this, Date(record.startTimestamp))
        val dateString = if (record.endTimestamp - record.startTimestamp < TimeUnit.MINUTES.toMillis(1)) {
            start
        } else {
            val end = Calendar.getInstance().apply { timeInMillis = record.endTimestamp }
            getString(
                R.string.date_placeholders__start_time__end_time,
                start,
                DateTimeUtils.formatTime(end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE))
            )
        }
        findViewById<TextView>(R.id.ecg_detail_title).text = formatClassification(this, record)
        findViewById<TextView>(R.id.ecg_detail_meta).text = "$dateString · ${formatDuration(this, record)}"
        waveformChart.setWaveform(record, waveform)

        val stats = mutableListOf(
            StatTileData(formatHeartRate(this, record), getString(R.string.heart_rate)),
            StatTileData(formatDuration(this, record), getString(R.string.activity_detail_duration_label)),
            StatTileData(
                getString(R.string.ecg_sample_rate_value, record.sampleRateHz),
                getString(R.string.ecg_sample_rate)
            ),
        )
        for (extra in record.extras) {
            stats.add(StatTileData(getString(extra.format, *extra.formatArgs), getString(extra.label)))
        }

        val statsContainer = findViewById<LinearLayout>(R.id.ecg_detail_stats)
        statsContainer.removeAllViews()
        addStatTileGrid(statsContainer, this, stats, horizontalMarginDp = 16)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupChartTouchHandling(chart: LineChart) {
        var downX = 0f
        var downY = 0f
        chart.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }

                MotionEvent.ACTION_POINTER_DOWN -> view.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_MOVE -> {
                    val horizontal = abs(event.x - downX) > abs(event.y - downY)
                    view.parent.requestDisallowInterceptTouchEvent(event.pointerCount > 1 || horizontal)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    companion object {
        const val EXTRA_RECORD_ID = "ecg_record_id"

        private val LOG = LoggerFactory.getLogger(EcgDetailActivity::class.java)

        fun formatClassification(context: Context, record: EcgRecord): String = when {
            record.classification != 0 -> context.getString(record.classification)
            record.classificationCode == null -> context.getString(R.string.unknown)
            else -> context.getString(R.string.training_status_unknown, record.classificationCode)
        }

        fun formatHeartRate(context: Context, record: EcgRecord): String =
            record.averageHeartRate?.let { context.getString(R.string.bpm_value_unit, it) }
                ?: context.getString(R.string.stats_empty_value)

        fun formatDuration(context: Context, record: EcgRecord): String =
            context.getString(
                R.string.seconds_value_unit,
                ((record.endTimestamp - record.startTimestamp) / 1000).toInt()
            )
    }
}
