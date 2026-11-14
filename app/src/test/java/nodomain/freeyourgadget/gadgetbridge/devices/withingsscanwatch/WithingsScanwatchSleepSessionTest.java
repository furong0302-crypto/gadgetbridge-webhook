package nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanwatchActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

// The ScanWatch builds its sleep sessions from its own samples, like the Steel HR. The session
// logic itself is covered by WithingsSleepSessionProviderTest.
public class WithingsScanwatchSleepSessionTest extends TestBase {
    // 2023-11-14T22:13:20Z
    private static final int T0 = 1_700_000_000;
    private static final int MIN = 60;

    private GBDevice dummyGBDevice;
    private long deviceId;
    private long userId;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        dummyGBDevice = createDummyGDevice("00:00:00:00:10");
        deviceId = DBHelper.getDevice(dummyGBDevice, daoSession).getId();
        userId = DBHelper.getUser(daoSession).getId();

        final List<WithingsScanwatchActivitySample> samples = new ArrayList<>();
        samples.add(sample(0, 60, ActivityKind.LIGHT_SLEEP.getCode()));
        samples.add(sample(60, 60, ActivityKind.DEEP_SLEEP.getCode()));
        samples.add(sample(120, 30, ActivityKind.REM_SLEEP.getCode()));
        // Heart-rate-only sample during the light phase
        samples.add(sample(30, 0, ActivityKind.NOT_MEASURED.getCode()));
        daoSession.getWithingsScanwatchActivitySampleDao().insertOrReplaceInTx(samples);
    }

    @Test
    public void coordinatorBuildsSessionsFromScanwatchSamples() {
        final List<SleepSession> sessions = new WithingsScanwatchDeviceCoordinator()
                .getSleepSessionProvider(dummyGBDevice, daoSession)
                .getSleepSessions(T0, T0 + 3600);

        assertEquals(1, sessions.size());
        final SleepSession session = sessions.get(0);
        assertEquals(T0 * 1000L, session.getStartTime());
        assertEquals((T0 + 150 * MIN) * 1000L, session.getEndTime());
        assertEquals(60 * MIN, session.getLightSleepDuration());
        assertEquals(60 * MIN, session.getDeepSleepDuration());
        assertEquals(30 * MIN, session.getRemSleepDuration());
        assertEquals(0, session.getAwakeSleepDuration());
    }

    @Test
    public void activitySamplesHaveTheKindOfTheirSleepStage() {
        final List<WithingsScanwatchActivitySample> samples = new WithingsScanwatchSampleProvider(dummyGBDevice, daoSession)
                .getAllActivitySamples(T0, T0 + 3 * 3600);

        assertEquals(4, samples.size());
        assertEquals(T0 + 30 * MIN, samples.get(1).getTimestamp());
        assertEquals(ActivityKind.LIGHT_SLEEP, samples.get(1).getKind());
    }

    private WithingsScanwatchActivitySample sample(final int startMinute, final int durationMinutes, final int rawKind) {
        final WithingsScanwatchActivitySample sample = new WithingsScanwatchActivitySample();
        sample.setTimestamp(T0 + startMinute * MIN);
        sample.setDeviceId(deviceId);
        sample.setUserId(userId);
        sample.setDuration(durationMinutes * MIN);
        sample.setRawKind(rawKind);
        sample.setRawIntensity(0);
        return sample;
    }
}
