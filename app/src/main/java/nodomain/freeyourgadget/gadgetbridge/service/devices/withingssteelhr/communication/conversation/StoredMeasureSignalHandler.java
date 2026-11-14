/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSpo2Sample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;

public class StoredMeasureSignalHandler implements ResponseHandler {
    private static final Logger logger = LoggerFactory.getLogger(StoredMeasureSignalHandler.class);
    private static final int MEASUREMENT_TYPE_SPO2 = 54;
    private static final int MEASUREMENT_TYPE_HEART_RATE = 11;
    private static final int MEASUREMENT_TYPE_SIGNAL_QUALITY = 89;
    private static final int ECG_MEASUREMENT_TYPE = 0x0103;
    private static final int ECG_WAVEFORM_SIGNAL_TYPE = 0x0001;
    private static final int MAX_DELETE_ATTEMPTS = 256;
    private static final int MAX_REPEATED_PAGE_RETRIES = 16;
    private static final int MAX_SIGNAL_DATA_PACKETS = 500;

    private final WithingsBaseDeviceSupport support;
    private final GBDevice device;
    private final int signalType;
    private int pendingPageCursor = -1;
    private int pendingPageSignalFlags = 0;
    private boolean pendingSawSignalMeta = false;
    private boolean pendingSawStoredData = false;
    private boolean pendingSawDurablyStoredData = false;
    private boolean pendingPersistenceFailed = false;
    private boolean pendingSawUndecodableData = false;
    private StoredMeasureMeta pendingEcgMeta = null;
    private long pendingLastSampleTimestampMs = -1;
    private int pendingLastSampleSpo2 = -1;
    private int consecutiveSignalDataPackets = 0;
    private int lastDeletedCursor = -1;
    private int lastDeletedSignalFlags = -1;
    private long lastDeletedTimestampMs = -1;
    private int lastDeletedSpo2 = -1;
    private int repeatedPageRetries;

    /**
     * Handles the head-based stored-measure queues used for SpO2 and mixed stored signals.
     *
     * <p>Based on official app HCI captures, these queues do not behave like classic pagination.
     * Gadgetbridge re-requests cursor 0, parses the returned head page, stores any SpO2 / HR
     * samples, and then deletes that exact page using the returned 0x0143 key. Some head pages
     * are mixed and contain both ECG metadata and SpO2 values, so this handler may hand off ECG
     * retrieval to {@link WithingsEcgHandler} before resuming deletion of the same queue head.
     */

    public StoredMeasureSignalHandler(final WithingsBaseDeviceSupport support, final GBDevice device, final int signalType) {
        this.support = support;
        this.device = device;
        this.signalType = signalType;
    }

    @Override
    public void handleResponse(final Message response) {
        logger.info("StoredMeasureSignal response: signalType=0x{} structureCount={} hasEOT={}",
                Integer.toHexString(signalType),
                response.getDataStructures().size(),
                support.hasEndOfTransmission(response));
        StoredMeasureMeta currentMeta = null;
        boolean sawAnyStoredData = false;
        boolean sawSignalMetaThisPage = false;
        boolean sawUndecodableData = false;
        int pageCursor = -1;

        // Collect all valid samples from this response; there may be multiple
        // (StoredMeasureMeta, StoredMeasureData) pairs in a single BLE message.
        final List<long[]> collectedSamples = new ArrayList<>(); // [timestampMs, spo2]
        final List<long[]> collectedHeartRates = new ArrayList<>(); // [timestampMs, bpm]

        for (final WithingsStructure structure : response.getDataStructures()) {
            logger.info("  TLV structure: {} (type=0x{})", structure.getClass().getSimpleName(), Integer.toHexString(structure.getType() & 0xffff));
            if (structure instanceof StoredSignalMeta) {
                final StoredSignalMeta storedSignalMeta = (StoredSignalMeta) structure;
                logger.info("  StoredSignalMeta: signalType=0x{} cursor={} signalFlags={}",
                        Integer.toHexString(storedSignalMeta.getSignalType()), storedSignalMeta.getCursor(), storedSignalMeta.getSignalFlags());
                if (storedSignalMeta.getSignalType() == signalType && storedSignalMeta.getCursor() >= 0) {
                    sawSignalMetaThisPage = true;
                    pageCursor = storedSignalMeta.getCursor();
                    pendingPageSignalFlags = storedSignalMeta.getSignalFlags();
                }
                continue;
            }

            if (structure instanceof StoredMeasureMeta) {
                currentMeta = (StoredMeasureMeta) structure;
                sawAnyStoredData = true;
                logger.info("  StoredMeasureMeta: measurementType=0x{} ({}) timestampMs={}",
                        Integer.toHexString(currentMeta.getMeasurementType()), currentMeta.getMeasurementType(), currentMeta.getTimestampMs());
                if (isEcgMeasurement(signalType, currentMeta.getMeasurementType())) {
                    pendingEcgMeta = currentMeta;
                }
                continue;
            }

            if (structure instanceof StoredSignalData) {
                final StoredSignalData signalData = (StoredSignalData) structure;
                sawAnyStoredData = true;
                consecutiveSignalDataPackets++;
                final byte[] raw = signalData.getSamples();
                logger.info("  StoredSignalData: {} bytes, sampleCount={} (packet #{})",
                        raw.length,
                        signalData.getSampleCount(),
                        consecutiveSignalDataPackets);
                if (consecutiveSignalDataPackets > MAX_SIGNAL_DATA_PACKETS) {
                    logger.warn("StoredMeasureSignal: received {} StoredSignalData packets for signalType=0x{} without EOT; aborting to prevent stall",
                            consecutiveSignalDataPackets, Integer.toHexString(signalType));
                    resetPendingPageState();
                    return;
                }
                continue;
            }

            if (!(structure instanceof StoredMeasureData)) {
                continue;
            }

            final StoredMeasureData data = (StoredMeasureData) structure;
            sawAnyStoredData = true;
            logger.info("  StoredMeasureData: measurementType={} rawValue={} exponent={} spo2Percent={}",
                    data.getMeasurementType(), data.getRawValue(), data.getExponent(), data.getSpo2Percent());

            if (currentMeta == null) {
                logger.debug("Skipping stored measure data without meta: spo2Percent={} hasEOT={}", data.getSpo2Percent(), support.hasEndOfTransmission(response));
                sawUndecodableData = true;
                continue;
            }

            if (!isLikelySpo2Measurement(currentMeta, data)) {
                if (isLikelyHeartRateMeasurement(currentMeta, data)) {
                    final int heartRate = data.getRawValue();
                    final long timestampMs = currentMeta.getTimestampMs();
                    if (heartRate >= 20 && heartRate <= 250 && timestampMs > 0) {
                        logger.debug("Collected Withings heart rate sample from stored measures: ts={} bpm={} metaType={} dataType={}",
                                timestampMs, heartRate, currentMeta.getMeasurementType(), data.getMeasurementType());
                        collectedHeartRates.add(new long[]{timestampMs, heartRate});
                    } else {
                        sawUndecodableData = true;
                    }
                    continue;
                }

                if (isKnownAuxiliaryMeasurement(data.getMeasurementType())) {
                    logger.debug("Ignoring known stored-measure auxiliary value: metaType={} dataType={} rawValue={}",
                            currentMeta.getMeasurementType(), data.getMeasurementType(), data.getRawValue());
                    continue;
                }

                logger.debug(
                        "Skipping stored measure sample: metaType={} dataType={} spo2Percent={} hasEOT={}",
                        currentMeta.getMeasurementType(),
                        data.getMeasurementType(),
                        data.getSpo2Percent(),
                        support.hasEndOfTransmission(response)
                );
                sawUndecodableData = true;
                continue;
            }

            final int spo2 = data.getSpo2Percent();
            if (spo2 < 1 || spo2 > 100) {
                sawUndecodableData = true;
                continue;
            }

            final long timestampMs = currentMeta.getTimestampMs();
            if (timestampMs <= 0) {
                sawUndecodableData = true;
                continue;
            }

            logger.info("Collected Withings SpO2 sample: ts={} spo2={} metaType={} dataType={}",
                    timestampMs, spo2, currentMeta.getMeasurementType(), data.getMeasurementType());
            collectedSamples.add(new long[]{timestampMs, spo2});
            pendingLastSampleTimestampMs = timestampMs;
            pendingLastSampleSpo2 = spo2;
        }

        if (!collectedSamples.isEmpty()) {
            try (DBHandler dbHandler = GBApplication.acquireDB()) {
                final Long userId = DBHelper.getUser(dbHandler.getDaoSession()).getId();
                final Long deviceId = DBHelper.getDevice(device, dbHandler.getDaoSession()).getId();
                final GenericSpo2SampleProvider provider = new GenericSpo2SampleProvider(device, dbHandler.getDaoSession());
                final List<GenericSpo2Sample> samples = new ArrayList<>(collectedSamples.size());
                for (final long[] entry : collectedSamples) {
                    samples.add(new GenericSpo2Sample(entry[0], deviceId, userId, (int) entry[1]));
                }
                provider.addSamples(samples);
                pendingSawDurablyStoredData = true;
                logger.info("Stored {} Withings SpO2 sample(s)", samples.size());
            } catch (final Exception ex) {
                pendingPersistenceFailed = true;
                logger.warn("Failed storing Withings SpO2 samples", ex);
            }
        }

        if (!collectedHeartRates.isEmpty()) {
            try (DBHandler dbHandler = GBApplication.acquireDB()) {
                final Long userId = DBHelper.getUser(dbHandler.getDaoSession()).getId();
                final Long deviceId = DBHelper.getDevice(device, dbHandler.getDaoSession()).getId();
                final GenericHeartRateSampleProvider provider = new GenericHeartRateSampleProvider(device, dbHandler.getDaoSession());
                final List<GenericHeartRateSample> samples = new ArrayList<>(collectedHeartRates.size());
                for (final long[] entry : collectedHeartRates) {
                    samples.add(new GenericHeartRateSample(entry[0], deviceId, userId, (int) entry[1]));
                }
                provider.addSamples(samples);
                pendingSawDurablyStoredData = true;
                logger.debug("Stored {} Withings heart rate sample(s) from stored measures", samples.size());
            } catch (final Exception ex) {
                pendingPersistenceFailed = true;
                logger.warn("Failed storing Withings heart rate samples from stored measures", ex);
            }
        }

        if (!sawAnyStoredData) {
            logger.info(
                    "StoredMeasureSignal: no meta/data structures in response: signalType=0x{} hasEOT={}",
                    Integer.toHexString(signalType),
                    support.hasEndOfTransmission(response)
            );
        }

        if (sawSignalMetaThisPage) {
            pendingSawSignalMeta = true;
            pendingPageCursor = pageCursor;
        }
        if (sawAnyStoredData) {
            pendingSawStoredData = true;
        }
        if (sawUndecodableData) {
            pendingSawUndecodableData = true;
        }

        final boolean hasEot = support.hasEndOfTransmission(response);
        if (!hasEot) {
            logger.info("StoredMeasureSignal: awaiting more packets for signalType=0x{}", Integer.toHexString(signalType));
            return;
        }

        if (!pendingSawSignalMeta || pendingPageCursor < 0) {
            resetPendingPageState();
            return;
        }

        final int currentCursor = pendingPageCursor;
        final int currentSignalFlags = pendingPageSignalFlags;
        final boolean sawStoredData = pendingSawStoredData;
        final boolean sawDurablyStoredData = pendingSawDurablyStoredData;
        final boolean persistenceFailed = pendingPersistenceFailed;
        final boolean sawUndecodableStoredData = pendingSawUndecodableData;
        final StoredMeasureMeta ecgMetaToNotify = pendingEcgMeta;
        final long lastSampleTimestampMs = pendingLastSampleTimestampMs;
        final int lastSampleSpo2 = pendingLastSampleSpo2;
        resetPendingPageState();

        if (ecgMetaToNotify != null) {
            if (support.hasDiscoveredEcgRecord(ecgMetaToNotify)) {
                logger.info("Stored-measure page signalType={} cursor={} contains an already-seen ECG record; continuing with page deletion instead of re-fetching it again",
                        signalType, currentCursor);
            } else {
                // Mixed signalType=0x0004 pages can contain an ECG record key plus SpO2 data.
                // Match the official app by fetching the ECG waveform first, then returning to
                // the same stored-measure queue head so the mixed page can still be deleted.
                logger.info("Stored-measure page signalType={} cursor={} contains ECG markers; scheduling ECG fetch before continuing this stored-measure loop",
                        signalType, currentCursor);
                support.notifyEcgRecordDiscovered(ecgMetaToNotify, signalType);
                return;
            }
        }

        if (persistenceFailed || sawUndecodableStoredData) {
            logger.warn("Keeping stored-measure page on watch: signalType={} cursor={} persistenceFailed={} undecodableData={}",
                    signalType, currentCursor, persistenceFailed, sawUndecodableStoredData);
            return;
        }

        if (!sawDurablyStoredData && ecgMetaToNotify == null) {
            logger.warn("Keeping empty stored-measure page on watch: signalType={} cursor={}", signalType, currentCursor);
            return;
        }

        if (isRepeatedPage(currentCursor, currentSignalFlags, lastSampleTimestampMs, lastSampleSpo2)) {
            if (!sawStoredData) {
                logger.warn("Stopping stored measure loop for signalType={} after repeated empty head page cursor={}", signalType, currentCursor);
                return;
            }
            if (repeatedPageRetries >= MAX_REPEATED_PAGE_RETRIES) {
                logger.warn("Watch refused to clear head page after {} delete retries for signalType={} cursor={} flags={}; stopping stored measure loop",
                        repeatedPageRetries, signalType, currentCursor, currentSignalFlags);
                return;
            }

            repeatedPageRetries++;
            final int deleteAttempts = support.incrementStoredMeasureDeleteAttempts();
            if (deleteAttempts > MAX_DELETE_ATTEMPTS) {
                logger.warn("Stopping stored measure loop for signalType={} after {} delete attempts", signalType, deleteAttempts);
                return;
            }

            logger.warn("Retrying delete for repeated stored measure head page signalType={} cursor={} flags={} ts={} spo2={} retry={}",
                    signalType, currentCursor, currentSignalFlags, lastSampleTimestampMs, lastSampleSpo2, repeatedPageRetries);
            final int resumeCursor = getResumeCursor(currentCursor);
            support.queueDeleteStoredMeasureSignal(signalType, currentSignalFlags, currentCursor, deleteResponse ->
                    support.queueGetStoredMeasureSignal(signalType, resumeCursor, StoredMeasureSignalHandler.this));
            return;
        }

        repeatedPageRetries = 0;

        lastDeletedCursor = currentCursor;
        lastDeletedSignalFlags = currentSignalFlags;
        lastDeletedTimestampMs = lastSampleTimestampMs;
        lastDeletedSpo2 = lastSampleSpo2;

        final int deleteAttempts = support.incrementStoredMeasureDeleteAttempts();
        if (deleteAttempts > MAX_DELETE_ATTEMPTS) {
            logger.warn("Stopping stored measure loop for signalType={} after {} delete attempts", signalType, deleteAttempts);
            return;
        }

        final int resumeCursor = getResumeCursor(currentCursor);
        support.queueDeleteStoredMeasureSignal(signalType, currentSignalFlags, currentCursor, deleteResponse -> {
            if (sawStoredData) {
                support.queueGetStoredMeasureSignal(signalType, resumeCursor, StoredMeasureSignalHandler.this);
            }
        });
    }

    private void resetPendingPageState() {
        pendingPageCursor = -1;
        pendingPageSignalFlags = 0;
        pendingSawSignalMeta = false;
        pendingSawStoredData = false;
        pendingSawDurablyStoredData = false;
        pendingPersistenceFailed = false;
        pendingSawUndecodableData = false;
        pendingEcgMeta = null;
        pendingLastSampleTimestampMs = -1;
        pendingLastSampleSpo2 = -1;
        consecutiveSignalDataPackets = 0;
    }

    private boolean isRepeatedPage(final int cursor,
                                   final int signalFlags,
                                   final long timestampMs,
                                   final int spo2) {
        return cursor == lastDeletedCursor
                && signalFlags == lastDeletedSignalFlags
                && timestampMs == lastDeletedTimestampMs
                && spo2 == lastDeletedSpo2;
    }

    private int getResumeCursor(final int currentCursor) {
        // Official app behavior in the captured sessions is head-queue based: it re-requests
        // stored-signal pages from cursor 0 and uses the returned cursor only as an opaque
        // delete key for the current head page.
        return 0;
    }

    private static boolean isLikelySpo2Measurement(final StoredMeasureMeta meta, final StoredMeasureData data) {
        if (data.getMeasurementType() == MEASUREMENT_TYPE_SPO2) {
            return true;
        }

        return meta.getMeasurementType() == MEASUREMENT_TYPE_SPO2;
    }

    private static boolean isLikelyHeartRateMeasurement(final StoredMeasureMeta meta, final StoredMeasureData data) {
        // In current Withings mixed ECG/SpO2 pages, type 11 consistently carries values like 65/66
        // while true SpO2 arrives as type 54 with exponent scaling (for example 941 -> 94.1%).
        // Treat type 11 as pulse / heart rate so we do not mis-store it as SpO2.
        if (data.getMeasurementType() == MEASUREMENT_TYPE_HEART_RATE) {
            return true;
        }

        return meta.getMeasurementType() == MEASUREMENT_TYPE_HEART_RATE;
    }

    static boolean isEcgMeasurement(final int signalType, final int measurementType) {
        // 0x0103 also identifies on-demand SpO2 records on signal queue 0x0004.
        // It is an ECG key only on the dedicated ECG waveform queue.
        return signalType == ECG_WAVEFORM_SIGNAL_TYPE && measurementType == ECG_MEASUREMENT_TYPE;
    }

    static boolean isKnownAuxiliaryMeasurement(final int measurementType) {
        // Present alongside the SpO2 value in official-app captures. The official app stores the
        // measurement and deletes the page, so this must not make an otherwise valid page unsafe.
        return measurementType == MEASUREMENT_TYPE_SIGNAL_QUALITY;
    }
}
