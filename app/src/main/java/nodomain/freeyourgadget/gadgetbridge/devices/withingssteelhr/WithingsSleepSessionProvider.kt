package nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr

import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionSegmenter
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage

/**
 * Withings stores each sleep phase as a single activity sample with a start and a duration, rather
 * than one sample per minute, so sessions are built from those spans instead of being segmented
 * like in [nodomain.freeyourgadget.gadgetbridge.devices.DefaultSleepSessionProvider].
 *
 * @param getRawSamples returns the samples between two timestamps (in seconds) as stored, without
 * sleep overlaid from these sessions, e.g. [WithingsSteelHRSampleProvider.getRawActivitySamples]
 */
class WithingsSleepSessionProvider(
    private val getRawSamples: (Int, Int) -> List<AbstractWithingsActivitySample>
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val samples = getRawSamples(tsFrom - LOOK_BACK_SECONDS, tsTo + LOOK_AHEAD_SECONDS)
        return buildSessions(samples)
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60
        private const val LOOK_AHEAD_SECONDS: Int = 12 * 60 * 60

        /**
         * Groups the sleep samples into sessions. Like in [SleepSessionSegmenter], a gap between
         * two sleep samples counts as awake if it is no longer than
         * [SleepSessionSegmenter.MAX_WAKE_PHASE_LENGTH_SECONDS] and has no steps in it. Any other
         * gap ends the session.
         */
        @JvmStatic
        fun buildSessions(samples: List<AbstractWithingsActivitySample>): List<SleepSession> {
            val sleepStages = samples
                .filter { ActivityKind.isSleep(it.kind) && it.duration > 0 }
                .map { SleepStage(it.kind, it.timestamp * 1000L, (it.timestamp.toLong() + it.duration) * 1000L) }
                .sortedBy { it.startTime }
            val stepTimestamps = samples.filter { it.steps > 0 }.map { it.timestamp * 1000L }

            val stages = ArrayList<SleepStage>(sleepStages.size)
            var lastEnd: Long? = null
            for (stage in sleepStages) {
                val gapStart = lastEnd
                if (gapStart != null && isAwakeGap(gapStart, stage.startTime, stepTimestamps)) {
                    stages.add(SleepStage(ActivityKind.AWAKE_SLEEP, gapStart, stage.startTime))
                }
                stages.add(stage)
                lastEnd = maxOf(gapStart ?: stage.endTime, stage.endTime)
            }

            return SleepSessionProvider.groupIntoSessions(stages)
                .filter { (it.endTime - it.startTime) / 1000L > SleepSessionSegmenter.MIN_SESSION_LENGTH_SECONDS }
        }

        private fun isAwakeGap(start: Long, end: Long, stepTimestamps: List<Long>): Boolean {
            return end > start &&
                end - start <= SleepSessionSegmenter.MAX_WAKE_PHASE_LENGTH_SECONDS * 1000L &&
                stepTimestamps.none { it in start..<end }
        }
    }
}
