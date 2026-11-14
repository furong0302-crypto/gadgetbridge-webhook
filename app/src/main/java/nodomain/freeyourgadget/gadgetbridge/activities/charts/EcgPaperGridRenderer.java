/*  Copyright (C) 2026 Brian123

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.renderer.LineChartRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.Utils;

/**
 * Draws an ECG paper style grid (minor and major lines) behind the chart data.
 * <p>
 * The grid needs hundreds of lines, so drawing it with LimitLines makes MPAndroidChart log a
 * warning for every line above 6. This draws the lines for the visible range directly instead.
 */
public class EcgPaperGridRenderer extends LineChartRenderer {
    private static final float EPSILON = 0.0001f;

    private final float minorStepX;
    private final int minorPerMajorX;
    private final float minorStepY;
    private final int minorPerMajorY;
    private final Paint minorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] points = new float[0];
    private float[] minorLines = new float[0];
    private float[] majorLines = new float[0];

    public EcgPaperGridRenderer(final LineChart chart,
                                final float minorStepX,
                                final float majorStepX,
                                final float minorStepY,
                                final float majorStepY,
                                final int minorColor,
                                final int majorColor) {
        super(chart, chart.getAnimator(), chart.getViewPortHandler());
        this.minorStepX = minorStepX;
        this.minorPerMajorX = Math.max(1, Math.round(majorStepX / minorStepX));
        this.minorStepY = minorStepY;
        this.minorPerMajorY = Math.max(1, Math.round(majorStepY / minorStepY));

        minorPaint.setStyle(Paint.Style.STROKE);
        minorPaint.setColor(minorColor);
        minorPaint.setStrokeWidth(Utils.INSTANCE.convertDpToPixel(0.45f));
        majorPaint.setStyle(Paint.Style.STROKE);
        majorPaint.setColor(majorColor);
        majorPaint.setStrokeWidth(Utils.INSTANCE.convertDpToPixel(0.9f));
    }

    @Override
    public void drawData(@NonNull final Canvas c) {
        drawGrid(c);
        super.drawData(c);
    }

    private void drawGrid(final Canvas c) {
        final LineDataProvider chart = getChart();
        final RectF content = getViewPortHandler().getContentRect();
        final Transformer transformer = chart.getTransformer(YAxis.AxisDependency.LEFT);
        final YAxis axis = chart.getAxis(YAxis.AxisDependency.LEFT);

        drawLines(c, transformer, content, true, minorStepX, minorPerMajorX,
                chart.getLowestVisibleX(), chart.getHighestVisibleX());
        drawLines(c, transformer, content, false, minorStepY, minorPerMajorY,
                axis.getAxisMinimum(), axis.getAxisMaximum());
    }

    private void drawLines(final Canvas c,
                           final Transformer transformer,
                           final RectF content,
                           final boolean vertical,
                           final float step,
                           final int minorPerMajor,
                           final float min,
                           final float max) {
        final int first = (int) Math.ceil(min / step - EPSILON);
        final int last = (int) Math.floor(max / step + EPSILON);
        final int count = last - first + 1;
        if (count <= 0) {
            return;
        }

        if (points.length < count * 2) {
            points = new float[count * 2];
        }
        for (int i = 0; i < count; i++) {
            final float value = (first + i) * step;
            points[i * 2] = vertical ? value : 0f;
            points[i * 2 + 1] = vertical ? 0f : value;
        }
        transformer.pointValuesToPixel(points, count * 2);

        if (minorLines.length < count * 4) {
            minorLines = new float[count * 4];
            majorLines = new float[count * 4];
        }
        int minorCount = 0;
        int majorCount = 0;
        for (int i = 0; i < count; i++) {
            final boolean major = Math.floorMod(first + i, minorPerMajor) == 0;
            final float[] lines = major ? majorLines : minorLines;
            final int offset = major ? majorCount : minorCount;
            if (vertical) {
                final float x = points[i * 2];
                lines[offset] = x;
                lines[offset + 1] = content.top;
                lines[offset + 2] = x;
                lines[offset + 3] = content.bottom;
            } else {
                final float y = points[i * 2 + 1];
                lines[offset] = content.left;
                lines[offset + 1] = y;
                lines[offset + 2] = content.right;
                lines[offset + 3] = y;
            }
            if (major) {
                majorCount += 4;
            } else {
                minorCount += 4;
            }
        }

        c.drawLines(minorLines, 0, minorCount, minorPaint);
        c.drawLines(majorLines, 0, majorCount, majorPaint);
    }
}
