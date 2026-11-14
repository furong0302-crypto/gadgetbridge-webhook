package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.ActivityIconView
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import java.util.Calendar

class EcgListFragment : AbstractCoroutineChartFragment<List<EcgRecord>>() {
    private lateinit var recordsView: RecyclerView
    private lateinit var emptyView: TextView
    private val adapter = RecordAdapter()

    override fun getTitle(): CharSequence = getString(R.string.pref_screen_ecg)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(R.layout.fragment_ecg_list, container, false)
        recordsView = rootView.findViewById(R.id.ecg_records)
        emptyView = rootView.findViewById(R.id.ecg_records_empty)
        recordsView.layoutManager = LinearLayoutManager(requireContext())
        recordsView.adapter = adapter
        recordsView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                chartsHost().enableSwipeRefresh(!recyclerView.canScrollVertically(-1))
            }
        })
        return rootView
    }

    override fun onResume() {
        super.onResume()
        // The base class shows the date bar, but the list does not depend on the date
        chartsHost().dateBar.visibility = View.GONE
    }

    override suspend fun loadChart(scope: ChartDataScope, host: ChartsHost): List<EcgRecord> = scope.db { db ->
        host.device.deviceCoordinator.getEcgDataProvider(host.device, db.daoSession)?.records ?: emptyList()
    }

    override fun renderChart(data: List<EcgRecord>) {
        adapter.records = data
        emptyView.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun renderError() {
        renderChart(emptyList())
    }

    private fun openDetails(record: EcgRecord) {
        startActivity(
            Intent(requireContext(), EcgDetailActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, chartsHost().device)
                .putExtra(EcgDetailActivity.EXTRA_RECORD_ID, record.id)
        )
    }

    private inner class RecordAdapter : RecyclerView.Adapter<RecordViewHolder>() {
        var records: List<EcgRecord> = emptyList()
            @Suppress("NotifyDataSetChanged")
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RecordViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_workout_row, parent, false)
        )

        override fun getItemCount() = records.size

        override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
            val record = records[position]
            val context = holder.itemView.context
            val start = Calendar.getInstance().apply { timeInMillis = record.startTimestamp }
            holder.icon.setImageResource(R.drawable.ic_ecg)
            holder.name.text = EcgDetailActivity.formatClassification(context, record)
            val heartRate = EcgDetailActivity.formatHeartRate(context, record)
            holder.subtitle.text = "$heartRate · ${EcgDetailActivity.formatDuration(context, record)}"
            holder.date.text = DateTimeUtils.formatDateRelative(context, start.time)
            holder.time.text = DateTimeUtils.formatTime(start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE))
            holder.separator.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener { openDetails(record) }
        }
    }

    private class RecordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ActivityIconView = view.findViewById(R.id.workout_row_icon)
        val name: TextView = view.findViewById(R.id.workout_row_name)
        val subtitle: TextView = view.findViewById(R.id.workout_row_subtitle)
        val date: TextView = view.findViewById(R.id.workout_row_date)
        val time: TextView = view.findViewById(R.id.workout_row_time)
        val separator: View = view.findViewById(R.id.workout_row_separator)
    }
}
