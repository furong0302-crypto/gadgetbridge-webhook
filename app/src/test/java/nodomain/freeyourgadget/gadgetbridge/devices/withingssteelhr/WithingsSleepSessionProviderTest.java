package nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsSteelHRActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

// Withings stores one activity sample per sleep phase, with a start and a duration. The samples
// are written with the raw kinds the device support stores, and read back through the real
// sample provider.
public class WithingsSleepSessionProviderTest extends TestBase {
    // 2023-11-14T22:13:20Z
    private static final int T0 = 1_700_000_000;
    private static final int MIN = 60;

    private GBDevice dummyGBDevice;
    private long deviceId;
    private long userId;
    private final List<WithingsSteelHRActivitySample> samples = new ArrayList<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        dummyGBDevice = createDummyGDevice("00:00:00:00:10");
        deviceId = DBHelper.getDevice(dummyGBDevice, daoSession).getId();
        userId = DBHelper.getUser(daoSession).getId();
    }

    @Test
    public void contiguousNight_exactTotals_lastPhaseCountedInFull() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(60, 60, ActivityKind.DEEP_SLEEP);
        sleep(120, 30, ActivityKind.REM_SLEEP);
        sleep(150, 10, ActivityKind.AWAKE_SLEEP);
        sleep(160, 80, ActivityKind.LIGHT_SLEEP);

        final SleepSession session = single(sessions(T0, T0 + 3600));
        assertBounds(session, 0, 240);
        assertEquals(140 * MIN, session.getLightSleepDuration());
        assertEquals(60 * MIN, session.getDeepSleepDuration());
        assertEquals(30 * MIN, session.getRemSleepDuration());
        assertEquals(10 * MIN, session.getAwakeSleepDuration());
    }

    @Test
    public void nonSleepSamplesWithoutSteps_ignored() {
        sleep(0, 120, ActivityKind.LIGHT_SLEEP);
        sleep(120, 60, ActivityKind.DEEP_SLEEP);
        // Heart-rate-only and movement samples during a sleep phase
        sample(30, 0, ActivityKind.NOT_MEASURED.getCode(), 0);
        sample(90, 1, ActivityKind.UNKNOWN.getCode(), 0);

        final SleepSession session = single(sessions(T0, T0 + 3600));
        assertBounds(session, 0, 180);
        assertEquals(120 * MIN, session.getLightSleepDuration());
        assertEquals(60 * MIN, session.getDeepSleepDuration());
        assertEquals(0, session.getAwakeSleepDuration());
    }

    @Test
    public void overlappingSamples_mergedWithoutDoubleCounting() {
        // A heart-rate sample merged into a sleep phase keeps its kind and the remaining duration
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(30, 30, ActivityKind.LIGHT_SLEEP);
        sleep(60, 60, ActivityKind.DEEP_SLEEP);

        final SleepSession session = single(sessions(T0, T0 + 3600));
        assertBounds(session, 0, 120);
        assertEquals(2, session.getStages().size());
        assertEquals(60 * MIN, session.getLightSleepDuration());
        assertEquals(60 * MIN, session.getDeepSleepDuration());
    }

    @Test
    public void shortGapWithoutSteps_countedAsAwake() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(80, 60, ActivityKind.DEEP_SLEEP);

        final SleepSession session = single(sessions(T0, T0 + 3600));
        assertBounds(session, 0, 140);
        assertStage(session.getStages().get(1), ActivityKind.AWAKE_SLEEP, 60, 80);
        assertEquals(20 * MIN, session.getAwakeSleepDuration());
    }

    @Test
    public void gapWithSteps_splitsSessions() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sample(70, 1, ActivityKind.UNKNOWN.getCode(), 25);
        sleep(80, 60, ActivityKind.DEEP_SLEEP);

        final List<SleepSession> sessions = sessions(T0, T0 + 3 * 3600);
        assertEquals(2, sessions.size());
        assertBounds(sessions.get(0), 0, 60);
        assertBounds(sessions.get(1), 80, 140);
    }

    @Test
    public void gapLongerThanMaxWakePhase_splitsSessions() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(121, 60, ActivityKind.DEEP_SLEEP);

        final List<SleepSession> sessions = sessions(T0, T0 + 3 * 3600);
        assertEquals(2, sessions.size());
        assertEquals(0, sessions.get(0).getAwakeSleepDuration());
        assertEquals(0, sessions.get(1).getAwakeSleepDuration());
    }

    @Test
    public void tooShortSession_dropped() {
        sleep(0, 5, ActivityKind.LIGHT_SLEEP);

        assertEquals(0, sessions(T0, T0 + 3600).size());
    }

    @Test
    public void sessionStartingInWindow_returnedWholeEvenPastTsTo() {
        // Without looking ahead, the session would end with the first phase
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(60, 180, ActivityKind.DEEP_SLEEP);

        assertBounds(single(sessions(T0, T0 + 30 * MIN)), 0, 240);
    }

    @Test
    public void sessionStartingBeforeWindow_isExcluded() {
        // Without looking back, only the second phase would be found, and returned as a session
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(60, 60, ActivityKind.DEEP_SLEEP);

        assertEquals(0, sessions(T0 + 30 * MIN, T0 + 3 * 3600).size());
    }

    @Test
    public void nonSleepSampleWithoutStepsInGap_doesNotSplit() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sample(70, 1, ActivityKind.UNKNOWN.getCode(), 0);
        sleep(80, 60, ActivityKind.DEEP_SLEEP);

        assertBounds(single(sessions(T0, T0 + 3600)), 0, 140);
    }

    @Test
    public void gapOfMaxWakePhase_doesNotSplit() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(120, 60, ActivityKind.DEEP_SLEEP);

        final SleepSession session = single(sessions(T0, T0 + 3600));
        assertBounds(session, 0, 180);
        assertEquals(60 * MIN, session.getAwakeSleepDuration());
    }

    @Test
    public void activitySamples_haveTheKindOfTheirSleepStage() {
        sleep(0, 60, ActivityKind.LIGHT_SLEEP);
        sleep(80, 60, ActivityKind.DEEP_SLEEP);
        // Heart-rate-only sample during the light phase, and a movement sample in the gap
        sample(30, 0, ActivityKind.NOT_MEASURED.getCode(), 0);
        sample(70, 1, ActivityKind.UNKNOWN.getCode(), 0);
        insertSamples();

        final List<WithingsSteelHRActivitySample> activitySamples = new WithingsSteelHRSampleProvider(dummyGBDevice, daoSession)
                .getAllActivitySamples(T0, T0 + 3 * 3600);

        assertEquals(4, activitySamples.size());
        assertKind(activitySamples.get(0), 0, ActivityKind.LIGHT_SLEEP);
        assertKind(activitySamples.get(1), 30, ActivityKind.LIGHT_SLEEP);
        assertKind(activitySamples.get(2), 70, ActivityKind.AWAKE_SLEEP);
        assertKind(activitySamples.get(3), 80, ActivityKind.DEEP_SLEEP);
    }

    private void sleep(final int startMinute, final int durationMinutes, final ActivityKind kind) {
        sample(startMinute, durationMinutes, kind.getCode(), 0);
    }

    private void sample(final int startMinute, final int durationMinutes, final int rawKind, final int steps) {
        final WithingsSteelHRActivitySample sample = new WithingsSteelHRActivitySample();
        sample.setTimestamp(T0 + startMinute * MIN);
        sample.setDeviceId(deviceId);
        sample.setUserId(userId);
        sample.setDuration(durationMinutes * MIN);
        sample.setRawKind(rawKind);
        sample.setSteps(steps);
        sample.setRawIntensity(0);
        samples.add(sample);
    }

    private void insertSamples() {
        daoSession.getWithingsSteelHRActivitySampleDao().insertOrReplaceInTx(samples);
        samples.clear();
    }

    private List<SleepSession> sessions(final int tsFrom, final int tsTo) {
        insertSamples();
        return new WithingsSleepSessionProvider(new WithingsSteelHRSampleProvider(dummyGBDevice, daoSession)::getRawActivitySamples)
                .getSleepSessions(tsFrom, tsTo);
    }

    private static SleepSession single(final List<SleepSession> sessions) {
        assertEquals(1, sessions.size());
        return sessions.get(0);
    }

    private static void assertBounds(final SleepSession session, final int startMinute, final int endMinute) {
        assertEquals((T0 + startMinute * MIN) * 1000L, session.getStartTime());
        assertEquals((T0 + endMinute * MIN) * 1000L, session.getEndTime());
    }

    private static void assertKind(final WithingsSteelHRActivitySample sample, final int minute, final ActivityKind kind) {
        assertEquals(T0 + minute * MIN, sample.getTimestamp());
        assertEquals(kind, sample.getKind());
    }

    private static void assertStage(final SleepStage stage, final ActivityKind kind, final int startMinute, final int endMinute) {
        assertEquals(kind, stage.getKind());
        assertEquals((T0 + startMinute * MIN) * 1000L, stage.getStartTime());
        assertEquals((T0 + endMinute * MIN) * 1000L, stage.getEndTime());
    }
}
