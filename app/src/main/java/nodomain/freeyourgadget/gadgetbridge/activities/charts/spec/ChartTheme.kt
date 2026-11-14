package nodomain.freeyourgadget.gadgetbridge.activities.charts.spec

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Chart colors, resolved once from a themed [Context].
 *
 * [backgroundColor] is transparent, so charts show the surface they are drawn on.
 */
data class ChartTheme(
    val textColor: Int,
    val secondaryTextColor: Int,
    val backgroundColor: Int,
    val heartRateColor: Int,
    val heartRateFillColor: Int,
    val activityColor: Int,
    val deepSleepColor: Int,
    val lightSleepColor: Int,
    val remSleepColor: Int,
    val awakeSleepColor: Int,
    val notWornColor: Int,
    val markerBackgroundColor: Int,
    val markerBorderColor: Int,
    val markerTitleColor: Int,
    val markerValueColor: Int,
    val markerDotGapColor: Int,
) {
    companion object {
        @JvmStatic
        fun from(context: Context): ChartTheme {
            val theme = context.theme
            fun attrColor(attrRes: Int): Int {
                val value = TypedValue()
                theme.resolveAttribute(attrRes, value, true)
                return value.data
            }

            val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)

            return ChartTheme(
                textColor = MaterialColors.getColor(context, R.attr.textColorPrimary, Color.BLACK),
                secondaryTextColor = MaterialColors.getColor(context, R.attr.textColorSecondary, Color.GRAY),
                backgroundColor = Color.TRANSPARENT,
                heartRateColor = ContextCompat.getColor(context, GBApplication.getPrefs().chartsHeartRateColor.colorRes),
                heartRateFillColor = ContextCompat.getColor(context, R.color.chart_heartrate_fill),
                activityColor = attrColor(R.attr.chart_activity),
                deepSleepColor = attrColor(R.attr.chart_deep_sleep),
                lightSleepColor = attrColor(R.attr.chart_light_sleep),
                remSleepColor = attrColor(R.attr.chart_rem_sleep),
                awakeSleepColor = attrColor(R.attr.chart_awake_sleep),
                notWornColor = attrColor(R.attr.chart_not_worn),
                markerBackgroundColor = MaterialColors.getColor(context, R.attr.chart_marker_bg, surface),
                markerBorderColor = MaterialColors.getColor(context, R.attr.stat_tile_border, Color.GRAY),
                markerTitleColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY),
                markerValueColor = MaterialColors.getColor(context, R.attr.textColorPrimary, Color.BLACK),
                markerDotGapColor = MaterialColors.getColor(context, android.R.attr.colorBackground, surface),
            )
        }
    }
}
