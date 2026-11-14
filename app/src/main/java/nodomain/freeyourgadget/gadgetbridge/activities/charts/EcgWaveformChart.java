package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartTheme;
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord;
import nodomain.freeyourgadget.gadgetbridge.model.EcgSample;

public class EcgWaveformChart {
    private static final float DISPLAY_Y_AXIS_HALF_RANGE = 1.5f;
    private static final float ECG_MAJOR_X_GRID_SECONDS = 0.2f;
    private static final float ECG_MINOR_X_GRID_SECONDS = 0.04f;
    private static final float ECG_MAJOR_Y_GRID = 0.5f;
    private static final float ECG_MINOR_Y_GRID = 0.1f;

    private final Context context;
    private final LineChart chart;

    public EcgWaveformChart(final Context context, final LineChart chart) {
        this.context = context;
        this.chart = chart;

        final ChartTheme chartTheme = ChartTheme.from(context);
        final int chartTextColor = chartTheme.getSecondaryTextColor();
        final boolean darkTheme = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;

        chart.setBackgroundColor(chartTheme.getBackgroundColor());
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setPinchZoomEnabled(false);
        chart.setScaleEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setNoDataText(context.getString(R.string.chart_no_data_synchronize));

        final XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLinesEnabled(false);
        xAxis.setTextColor(chartTextColor);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(30f);
        xAxis.setLabelCount(6);
        xAxis.setForceLabelsEnabled(true);
        xAxis.setGranularity(1f);

        final YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLinesEnabled(false);
        leftAxis.setTextColor(chartTextColor);
        leftAxis.setAxisMinimum(-2f);
        leftAxis.setAxisMaximum(2f);

        final YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setDrawLabelsEnabled(false);
        rightAxis.setDrawGridLinesEnabled(false);
        rightAxis.setDrawAxisLineEnabled(true);

        chart.setRenderer(new EcgPaperGridRenderer(chart,
            ECG_MINOR_X_GRID_SECONDS, ECG_MAJOR_X_GRID_SECONDS,
            ECG_MINOR_Y_GRID, ECG_MAJOR_Y_GRID,
            darkTheme ? 0x20FF8A8A : 0x1AE57373,
            darkTheme ? 0x40FF8A8A : 0x33E57373));
    }

    public void setWaveform(@NonNull final EcgRecord record, @NonNull final List<EcgSample> waveform) {
        chart.setData(null);
        chart.clear();

        if (waveform.isEmpty()) {
            chart.invalidate();
            return;
        }

        final ArrayList<Entry<?>> entries = new ArrayList<>(waveform.size());
        float minValue = Float.MAX_VALUE;
        float maxValue = -Float.MAX_VALUE;
        int maxDelta = 0;

        for (final EcgSample sample : waveform) {
            final float seconds = sample.getTimeDeltaMs() / 1000f;
            final float value = sample.getValue();
            entries.add(new Entry<>(seconds, value, null, null));
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
            maxDelta = Math.max(maxDelta, sample.getTimeDeltaMs());
        }

        final List<ILineDataSet<?>> dataSets = new ArrayList<>(1);
        dataSets.add(createDataSet(entries));
        chart.setData(new LineData(dataSets));

        final long recordDurationMs = record.getEndTimestamp() - record.getStartTimestamp();
        final float durationSeconds = recordDurationMs > 0
            ? Math.max(maxDelta / 1000f, recordDurationMs / 1000f)
            : Math.max(maxDelta / 1000f, 1f);
        chart.getXAxis().setAxisMaximum(durationSeconds);
        chart.getXAxis().setLabelCount(6);
        chart.getXAxis().setForceLabelsEnabled(true);
        chart.getXAxis().setValueFormatter(new SessionXAxisFormatter(record.getStartTimestamp()));

        final YAxis leftAxis = chart.getAxisLeft();
        final float maxAbs = Math.max(Math.abs(minValue), Math.abs(maxValue));
        final float halfRange = Math.max(DISPLAY_Y_AXIS_HALF_RANGE, maxAbs * 1.25f);
        leftAxis.setAxisMinimum(-halfRange);
        leftAxis.setAxisMaximum(halfRange);
        chart.fitScreen();
        chart.invalidate();
    }

    private LineDataSet<?> createDataSet(final List<Entry<?>> values) {
        final LineDataSet<?> dataSet = new LineDataSet<>(values, context.getString(R.string.pref_screen_ecg));
        dataSet.setColor(ContextCompat.getColor(context, R.color.chart_line_heart_rate));
        dataSet.setDrawCirclesEnabled(false);
        dataSet.setDrawValuesEnabled(false);
        dataSet.setLineWidth(1.5f);
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setHighlightEnabled(false);
        return dataSet;
    }

    private static class SessionXAxisFormatter implements IAxisValueFormatter {
        private final long startTimestamp;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        private SessionXAxisFormatter(final long startTimestamp) {
            this.startTimestamp = startTimestamp;
        }

        @NonNull
        @Override
        public String getFormattedValue(final float value, @NonNull final AxisBase axis) {
            return timeFormat.format(new Date(startTimestamp + (long) (value * 1000L)));
        }
    }
}
