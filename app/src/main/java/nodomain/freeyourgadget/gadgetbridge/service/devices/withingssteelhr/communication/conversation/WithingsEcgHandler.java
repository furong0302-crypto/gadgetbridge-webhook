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

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgDataSample;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgDataSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgSummarySample;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgSummarySampleDao;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.EndOfTransmission;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.RawWithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureDataExtend;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMetaExtended;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.WithingsEcgWaveformUtil;

public class WithingsEcgHandler implements ResponseHandler {
    private static final Logger logger = LoggerFactory.getLogger(WithingsEcgHandler.class);

    private static final int ECG_MEASUREMENT_TYPE = 0x0103;
    private static final int ECG_AVERAGE_HR_TYPE = 0x000b;
    private static final int ECG_RESULT_TYPE = 0x0082;
    private static final int ECG_WAVEFORM_SIGNAL_TYPE = 0x0001;
    public static final int ECG_SAMPLE_RATE_HZ = 300;

    private static final int MAX_DISCOVERY_PAGES = 10;
    private static final int MAX_SPO2_FALLBACK_RETRIES = 3;

    private final WithingsBaseDeviceSupport support;
    private final GBDevice device;
    private final List<byte[]> seenRecordKeys = new ArrayList<>();
    private final List<Long> seenRecordTimestamps = new ArrayList<>();

    private int discoveryPagesSeen;
    private boolean waveformFetchQueued;
    private EcgWaveformHandler activeWaveformHandler;
    private int repeatedSpo2FallbackRetries;
    private int discoveredEcgCount;
    private int fetchedEcgCount;
    private int deletedEcgCount;

    /**
     * Mirrors the official ECG sync split seen in HCI captures:
     * first discover stored ECG record keys via MEASURE_* responses, then fetch each waveform by
     * replaying the exact 0x0116 key returned by discovery.
     *
     * <p>This discovery stage means ECG progress is theoretically knowable because the watch tells
     * us how many ECG record keys exist up front. The current implementation uses discovery for
     * fetching order and dedupe, and reports a best-effort ECG-only transfer percentage via logs
     * and the generic transfer notification.
     */

    public WithingsEcgHandler(final WithingsBaseDeviceSupport support, final GBDevice device) {
        this.support = support;
        this.device = device;
    }

    public void start() {
        if (waveformFetchQueued) {
            logger.debug("Withings ECG fetch already in progress, keeping current handler state");
            return;
        }

        seenRecordKeys.clear();
        seenRecordTimestamps.clear();
        discoveryPagesSeen = 0;
        waveformFetchQueued = false;
        repeatedSpo2FallbackRetries = 0;
        discoveredEcgCount = 0;
        fetchedEcgCount = 0;
        deletedEcgCount = 0;
        logger.info("Starting Withings ECG discovery");
        updateEcgProgress("Scanning for ECG records", 0, true);
        queueStoredEcgHead();
    }

    public boolean hasDiscoveredRecord(final StoredMeasureMeta recordKey) {
        if (recordKey == null) {
            return false;
        }
        for (final byte[] seenKey : seenRecordKeys) {
            if (Arrays.equals(seenKey, recordKey.getRawPayload())) {
                return true;
            }
        }
        final long timestampMs = recordKey.getTimestampMs();
        if (timestampMs > 0 && seenRecordTimestamps.contains(timestampMs)) {
            return true;
        }
        return false;
    }

    public void handleDiscoveredRecord(final StoredMeasureMeta recordKey, final int originatingSignalType) {
        final boolean alreadyStored = recordKey != null && isRecordAlreadyStored(recordKey.getTimestampMs());
        final boolean isNewRecord = recordKey != null && !alreadyStored && isNewRecordKey(recordKey);
        logger.info("handleDiscoveredRecord called: recordKey != null = {}, isNewRecordKey = {}, alreadyStored = {}, seenRecordKeys.size = {}",
                recordKey != null,
                isNewRecord,
                alreadyStored,
                seenRecordKeys.size());

        if (recordKey == null) {
            return;
        }

        if (isNewRecord) {
            seenRecordKeys.add(recordKey.getRawPayload());
            final long timestampMs = recordKey.getTimestampMs();
            if (timestampMs > 0 && !seenRecordTimestamps.contains(timestampMs)) {
                seenRecordTimestamps.add(timestampMs);
            }
            discoveredEcgCount++;
            logger.info("Discovered Withings ECG record via fallback ts={} type={}", recordKey.getTimestampMs(), recordKey.getMeasurementType());
            updateEcgProgress("Discovered ECG record via stored-signal fallback", computeEcgProgressPercent(), true);
        } else {
            logger.info("Re-fetching previously seen Withings ECG record ts={} so it can still be deleted", recordKey.getTimestampMs());
        }

        activeWaveformHandler = new EcgWaveformHandler(recordKey, isNewRecord, isNewRecord, false, true, originatingSignalType);
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
        message.addDataStructure(recordKey);
        support.addSimpleConversationFirst(message, activeWaveformHandler);
    }

    public void reset() {
        activeWaveformHandler = null;
        seenRecordKeys.clear();
        seenRecordTimestamps.clear();
        discoveryPagesSeen = 0;
        waveformFetchQueued = false;
        repeatedSpo2FallbackRetries = 0;
        discoveredEcgCount = 0;
        fetchedEcgCount = 0;
        deletedEcgCount = 0;
        clearEcgProgressNotification();
    }

    public void onSyncFinished() {
        clearEcgProgressNotification();
    }

    @Override
    public void handleResponse(final Message response) {
        if (response.getType() == WithingsMessageType.MEASURE_START
                || response.getType() == WithingsMessageType.MEASURE_STOP
                || response.getType() == WithingsMessageType.TRANSFER_COMPLETE) {
            handleDiscoveryResponse(response);
        }
    }

    public boolean maybeHandleMeasurementMessage(final Message response) {
        if (response.getType() == WithingsMessageType.MEASURE_START
                || response.getType() == WithingsMessageType.MEASURE_STOP
                || response.getType() == WithingsMessageType.TRANSFER_COMPLETE) {
            handleDiscoveryResponse(response);
            return true;
        }
        return false;
    }

    private void queueStoredEcgHead() {
        logger.debug("Queueing Withings stored ECG head probe, discoveryPagesSeen={}, waveformFetchQueued={}",
                discoveryPagesSeen, waveformFetchQueued);
        waveformFetchQueued = true;
        activeWaveformHandler = new EcgWaveformHandler(null, true, true, false, false, -1);
        support.addSimpleConversationFirst(createStoredEcgHeadProbe(), activeWaveformHandler);
    }

    static WithingsMessage createStoredEcgHeadProbe() {
        final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
        message.addDataStructure(new StoredSignalMeta(ECG_WAVEFORM_SIGNAL_TYPE, 0));
        return message;
    }

    private void handleDiscoveryResponse(final Message response) {
        final List<WithingsStructure> structures = response.getDataStructures();
        if (structures == null || structures.isEmpty()) {
            logger.warn("Withings ECG discovery response type={} had no structures", response.getType());
            return;
        }

        StoredMeasureMeta recordKey = null;
        boolean hasEot = false;
        boolean hasEcgMarkers = false;

        for (final WithingsStructure structure : structures) {
            if (structure instanceof EndOfTransmission) {
                hasEot = true;
            } else if (structure instanceof StoredMeasureMeta) {
                hasEcgMarkers = true;
                final StoredMeasureMeta meta = (StoredMeasureMeta) structure;
                if (meta.getMeasurementType() == ECG_MEASUREMENT_TYPE) {
                    recordKey = meta;
                }
            }
        }

        logger.debug("Withings ECG discovery response type={} hasEot={} hasEcgMarkers={} discovered={} fetched={} deleted={} structures={}",
                response.getType(), hasEot, hasEcgMarkers, discoveredEcgCount, fetchedEcgCount, deletedEcgCount, describeStructures(structures));

        if (recordKey != null && isNewRecordKey(recordKey)) {
            seenRecordKeys.add(recordKey.getRawPayload());
            final long timestampMs = recordKey.getTimestampMs();
            if (timestampMs > 0 && !seenRecordTimestamps.contains(timestampMs)) {
                seenRecordTimestamps.add(timestampMs);
            }
            discoveredEcgCount++;
            // Discovery gives us the same record key the official app later replays in
            // GET_STORED_MEASURE_SIGNAL to fetch the full waveform.
            logger.info("Discovered Withings ECG record ts={} type={}", recordKey.getTimestampMs(), recordKey.getMeasurementType());
            updateEcgProgress("Discovered ECG record", computeEcgProgressPercent(), true);
            waveformFetchQueued = true;
            activeWaveformHandler = new EcgWaveformHandler(recordKey, true, true, false, false, -1);
            final WithingsMessage message = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
            message.addDataStructure(recordKey);
            support.addSimpleConversationFirst(message, activeWaveformHandler);
        }

        if (response.getType() == WithingsMessageType.MEASURE_STOP) {
            final WithingsMessage ack = new WithingsMessage(WithingsMessageType.MEASURE_STOP, ExpectedResponse.NONE);
            ack.addDataStructure(new EndOfTransmission());
            support.addSimpleConversationFirst(ack, null);
        }

        if (hasEot) {
            discoveryPagesSeen++;
            if (!hasEcgMarkers && discoveredEcgCount == 0) {
                updateEcgProgress("No ECG records to sync", 100, false);
            }
            if (!waveformFetchQueued && hasEcgMarkers && discoveryPagesSeen < MAX_DISCOVERY_PAGES) {
                queueStoredEcgHead();
            } else if (!waveformFetchQueued && hasEcgMarkers) {
                logger.warn("Withings ECG discovery stopped after {} pages without queueing waveform fetch", discoveryPagesSeen);
            }
        } else if (!hasEot) {
            logger.debug("Withings ECG discovery awaiting more packets for response type={}", response.getType());
        }
    }

    private String describeStructures(final List<WithingsStructure> structures) {
        final List<String> descriptions = new ArrayList<>(structures.size());
        for (final WithingsStructure structure : structures) {
            if (structure instanceof StoredMeasureMeta) {
                final StoredMeasureMeta meta = (StoredMeasureMeta) structure;
                descriptions.add("0116(type=" + meta.getMeasurementType() + ",ts=" + meta.getTimestampMs() + ")");
            } else if (structure instanceof StoredMeasureData) {
                final StoredMeasureData data = (StoredMeasureData) structure;
                descriptions.add("0117(type=" + data.getMeasurementType() + ",raw=" + data.getRawValue() + ")");
            } else if (structure instanceof StoredMeasureDataExtend) {
                final StoredMeasureDataExtend dataExt = (StoredMeasureDataExtend) structure;
                descriptions.add("0149(type=" + dataExt.getMeasurementType() + ",extra=" + dataExt.getExtraData() + ")");
            } else if (structure instanceof StoredSignalMeta) {
                final StoredSignalMeta meta = (StoredSignalMeta) structure;
                descriptions.add("0143(signalType=" + meta.getSignalType() + ",flags=" + meta.getSignalFlags() + ",cursor=" + meta.getCursor() + ")");
            } else if (structure instanceof StoredSignalMetaExtended) {
                final byte[] rawPayload = ((StoredSignalMetaExtended) structure).getRawPayload();
                descriptions.add("0146(" + GB.hexdump(rawPayload) + ")");
            } else if (structure instanceof StoredSignalData) {
                descriptions.add("0144(samples)");
            } else if (structure instanceof RawWithingsStructure) {
                final RawWithingsStructure raw = (RawWithingsStructure) structure;
                descriptions.add(String.format("0x%04x(raw=%s)", raw.getType() & 0xffff, GB.hexdump(raw.getRawData())));
            } else {
                descriptions.add(String.format("0x%04x", structure.getType() & 0xffff));
            }
        }
        return descriptions.toString();
    }

    private int computeEcgProgressPercent() {
        if (discoveredEcgCount <= 0) {
            return 0;
        }

        if (deletedEcgCount >= discoveredEcgCount) {
            return 100;
        }

        if (fetchedEcgCount <= 0) {
            return 0;
        }

        return Math.min(99, (fetchedEcgCount * 100) / discoveredEcgCount);
    }

    private void updateEcgProgress(final String stage, final int percent, final boolean ongoing) {
        final int clamped = Math.max(0, Math.min(100, percent));
        final String details;
        if (discoveredEcgCount > 0) {
            details = String.format("%s (%d/%d fetched, %d deleted)",
                    stage,
                    Math.min(fetchedEcgCount, discoveredEcgCount),
                    discoveredEcgCount,
                    Math.min(deletedEcgCount, discoveredEcgCount));
        } else {
            details = stage;
        }

        logger.info("Withings ECG sync progress: {}% - {}", clamped, details);
        GB.updateTransferNotification(
                support.getContext().getString(R.string.busy_task_syncing),
                details,
                ongoing,
                clamped,
                support.getContext()
        );
    }

    private void clearEcgProgressNotification() {
        GB.updateTransferNotification(
                support.getContext().getString(R.string.busy_task_syncing),
                "",
                false,
                100,
                support.getContext()
        );
    }

    private boolean isNewRecordKey(final StoredMeasureMeta recordKey) {
        final byte[] rawPayload = recordKey.getRawPayload();
        for (final byte[] seen : seenRecordKeys) {
            if (Arrays.equals(seen, rawPayload)) {
                return false;
            }
        }
        final long timestampMs = recordKey.getTimestampMs();
        if (timestampMs > 0 && seenRecordTimestamps.contains(timestampMs)) {
            return false;
        }
        return true;
    }

    private boolean isRecordAlreadyStored(final long startTimestampMs) {
        if (startTimestampMs <= 0) {
            return false;
        }

        try (DBHandler db = GBApplication.acquireDbReadOnly()) {
            final Device dbDevice = DBHelper.findDevice(device, db.getDaoSession());
            final WithingsEcgSummarySample summary = findSummary(db.getDaoSession(), dbDevice.getId(), startTimestampMs);
            return summary != null && db.getDaoSession().getWithingsEcgDataSampleDao().queryBuilder()
                .where(WithingsEcgDataSampleDao.Properties.EcgId.eq(summary.getEcgId()))
                .count() > 0;
        } catch (final Exception e) {
            logger.warn("Failed checking whether Withings ECG ts={} already exists", startTimestampMs, e);
            return false;
        }
    }

    private final class EcgWaveformHandler implements ResponseHandler {
        private StoredMeasureMeta requestedRecordKey;
        private final boolean countTowardsProgress;
        private final boolean storeWaveformAfterFetch;
        private final boolean verifyDeletion;
        private final boolean foundViaSpO2Loop;
        private final int originatingSignalType;
        private final List<Float> waveform = new ArrayList<>();
        private final WithingsEcgWaveformUtil.StreamingDecoder waveformDecoder = new WithingsEcgWaveformUtil.StreamingDecoder();
        private StoredSignalMeta deleteKey;
        private byte[] deleteKeyExtendedRaw;
        private long startTimestampMs;
        private int averageHeartRate = -1;
        private long arrhythmiaType = 0;

        private EcgWaveformHandler(final StoredMeasureMeta requestedRecordKey,
                                   final boolean countTowardsProgress,
                                   final boolean storeWaveformAfterFetch,
                                   final boolean verifyDeletion,
                                   final boolean foundViaSpO2Loop,
                                   final int originatingSignalType) {
            this.requestedRecordKey = requestedRecordKey;
            this.countTowardsProgress = countTowardsProgress;
            this.storeWaveformAfterFetch = storeWaveformAfterFetch;
            this.verifyDeletion = verifyDeletion;
            this.foundViaSpO2Loop = foundViaSpO2Loop;
            this.originatingSignalType = originatingSignalType;
        }

        @Override
        public void handleResponse(final Message response) {
            final List<WithingsStructure> structures = response.getDataStructures();
            if (structures == null || structures.isEmpty()) {
                return;
            }

            for (final WithingsStructure structure : structures) {
                if (structure instanceof StoredMeasureMeta) {
                    final StoredMeasureMeta meta = (StoredMeasureMeta) structure;
                    if (meta.getMeasurementType() == ECG_MEASUREMENT_TYPE) {
                        if (requestedRecordKey == null) {
                            requestedRecordKey = meta;
                            if (isNewRecordKey(meta)) {
                                seenRecordKeys.add(meta.getRawPayload());
                                final long timestampMs = meta.getTimestampMs();
                                if (timestampMs > 0 && !seenRecordTimestamps.contains(timestampMs)) {
                                    seenRecordTimestamps.add(timestampMs);
                                }
                                discoveredEcgCount++;
                                logger.info("Discovered Withings ECG record from stored signal queue ts={} type={}",
                                        timestampMs, meta.getMeasurementType());
                            }
                        }
                        startTimestampMs = meta.getTimestampMs();
                    }
                } else if (structure instanceof StoredMeasureData) {
                    final StoredMeasureData data = (StoredMeasureData) structure;
                    if (data.getMeasurementType() == ECG_AVERAGE_HR_TYPE) {
                        averageHeartRate = data.getRawValue();
                    } else if (data.getMeasurementType() == ECG_RESULT_TYPE) {
                        arrhythmiaType = data.getRawValue();
                    }
                } else if (structure instanceof StoredMeasureDataExtend) {
                    final StoredMeasureDataExtend dataExt = (StoredMeasureDataExtend) structure;
                    if (dataExt.getMeasurementType() == ECG_RESULT_TYPE) {
                        long hint = dataExt.getExtraData();
                        // The actual device hint (Normal vs AFib, etc.) is encoded in the 0x0149
                        // (STORED_MEASURE_DATA_EXTEND) TLV's extraData, rather than the 0x0117 raw value
                        // which is usually just 0.
                        // 0x01020401 (16909313) is the specific code for "Normal Sinus Rhythm".
                        if (hint == 16909313L) { // 0x01020401
                            arrhythmiaType = 0;
                            logger.info("Withings ECG Device Hint matched Normal code (0x01020401), mapped to 0.");
                        } else {
                            arrhythmiaType = hint;
                            logger.warn("Withings ECG Device Hint unknown code: " + hint + " (" + Long.toHexString(hint) + ")");
                        }
                    }
                } else if (structure instanceof StoredSignalMeta) {
                    final StoredSignalMeta signalMeta = (StoredSignalMeta) structure;
                    if (signalMeta.getSignalType() == ECG_WAVEFORM_SIGNAL_TYPE) {
                        deleteKey = signalMeta;
                        logger.info("Withings ECG fetch got delete key: signalType={} signalFlags={} cursor={}",
                                signalMeta.getSignalType(), signalMeta.getSignalFlags(), signalMeta.getCursor());
                    }
                } else if (structure instanceof StoredSignalMetaExtended) {
                    deleteKeyExtendedRaw = ((StoredSignalMetaExtended) structure).getRawPayload();
                    logger.info("Withings ECG fetch got 0x0146 extended signal meta: len={} payload={}",
                            deleteKeyExtendedRaw.length, GB.hexdump(deleteKeyExtendedRaw));
                } else if (structure instanceof StoredSignalData) {
                    final StoredSignalData signalData = (StoredSignalData) structure;
                    final List<Float> decodedSamples = WithingsEcgWaveformUtil.decodePacket(signalData.getSampleBytes(), waveformDecoder);
                    if (!decodedSamples.isEmpty()) {
                        waveform.addAll(decodedSamples);
                    }
                }
            }

            if (!support.hasEndOfTransmission(response)) {
                logger.debug("Withings ECG waveform response awaiting more packets for ts={} structures={}",
                        getRecordTimestampMs(),
                        describeStructures(structures));
                return;
            }

            logger.debug("Withings ECG waveform response reached EOT for ts={} deleteKeyPresent={} verifyDeletion={} samples={} structures={}",
                    getRecordTimestampMs(),
                    deleteKey != null,
                    verifyDeletion,
                    waveform.size(),
                    describeStructures(structures));

            if (startTimestampMs <= 0) {
                startTimestampMs = getRecordTimestampMs();
            }

            if (requestedRecordKey == null && waveform.isEmpty() && deleteKey == null) {
                logger.info("No stored Withings ECG records available");
                updateEcgProgress("No ECG records to sync", 100, false);
                activeWaveformHandler = null;
                waveformFetchQueued = false;
                return;
            }

            if (requestedRecordKey == null) {
                logger.warn("Keeping stored ECG data on watch because its exact 0x0116 record key was missing");
                activeWaveformHandler = null;
                waveformFetchQueued = false;
                return;
            }

            if (verifyDeletion) {
                if (!waveform.isEmpty()) {
                    logger.warn("Withings ECG delete verification still returned waveform data for ts={} samples={}", startTimestampMs, waveform.size());
                } else {
                    logger.info("Withings ECG delete verification: waveform gone for ts={}", startTimestampMs);
                    if (countTowardsProgress) {
                        deletedEcgCount = Math.max(deletedEcgCount + 1, fetchedEcgCount);
                        updateEcgProgress(
                                deletedEcgCount >= discoveredEcgCount ? "Completed ECG sync" : "Deleted ECG from watch",
                                computeEcgProgressPercent(),
                                deletedEcgCount < discoveredEcgCount
                        );
                    }
                }
                activeWaveformHandler = null;
                waveformFetchQueued = false;
                if (foundViaSpO2Loop) {
                    if (!waveform.isEmpty()) {
                        logger.warn("Resuming stored-measure head-page loop for signalType={} after ECG delete verification still returned waveform for ts={}",
                                originatingSignalType, startTimestampMs);
                    }
                    support.queueGetStoredMeasureSignal(originatingSignalType, 0, new StoredMeasureSignalHandler(support, device, originatingSignalType));
                } else if (discoveryPagesSeen < MAX_DISCOVERY_PAGES) {
                    queueStoredEcgHead();
                }
                return;
            }

            boolean waveformStored = false;
            if (!waveform.isEmpty()) {
                final long endTimestampMs = startTimestampMs + Math.round((waveform.size() * 1000d) / ECG_SAMPLE_RATE_HZ);
                waveformStored = storeWaveform(startTimestampMs, endTimestampMs, averageHeartRate, arrhythmiaType, waveform);
                if (waveformStored && countTowardsProgress) {
                    fetchedEcgCount = Math.max(fetchedEcgCount + 1, deletedEcgCount + 1);
                    updateEcgProgress("Fetched ECG waveform", computeEcgProgressPercent(), true);
                }
            } else if (storeWaveformAfterFetch) {
                logger.warn("Expected Withings ECG waveform for ts={} but got none", startTimestampMs);
            }

            if (deleteKey != null && waveformStored) {
                repeatedSpo2FallbackRetries = 0;
                support.queueDeleteStoredMeasureSignal(
                        deleteKey.getSignalType(),
                        deleteKey.getSignalFlags(),
                        deleteKey.getCursor(),
                        deleteResponse -> {
                            activeWaveformHandler = new EcgWaveformHandler(requestedRecordKey, countTowardsProgress, false, true, foundViaSpO2Loop, originatingSignalType);
                            final WithingsMessage deleteVerifyMsg = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
                            deleteVerifyMsg.addDataStructure(requestedRecordKey);
                            support.addSimpleConversationFirst(deleteVerifyMsg, activeWaveformHandler);
                        }
                );
                return;
            }

            activeWaveformHandler = null;
            waveformFetchQueued = false;
            if (deleteKey != null) {
                logger.warn("Keeping Withings ECG record ts={} on watch because its waveform was not stored", startTimestampMs);
                return;
            }
            logger.warn("Missing delete key for Withings ECG record ts={}, unable to delete from watch (0x0146={}; structures={})",
                    startTimestampMs,
                    deleteKeyExtendedRaw == null ? "none" : GB.hexdump(deleteKeyExtendedRaw),
                    describeStructures(structures));
            if (foundViaSpO2Loop) {
                repeatedSpo2FallbackRetries++;
                if (repeatedSpo2FallbackRetries > MAX_SPO2_FALLBACK_RETRIES) {
                    logger.warn("Stopping repeated ECG fallback for ts={} after {} missing-delete-key retries; not advancing cursor because official app behavior appears head-page based",
                            startTimestampMs, repeatedSpo2FallbackRetries - 1);
                } else {
                    // Retry from cursor 0 because mixed stored-signal pages behave as a queue head,
                    // not a pageable list. Advancing the cursor risks skipping a stubborn head page.
                    support.queueGetStoredMeasureSignal(originatingSignalType, 0, new StoredMeasureSignalHandler(support, device, originatingSignalType));
                }
            } else if (discoveryPagesSeen < MAX_DISCOVERY_PAGES) {
                queueStoredEcgHead();
            }
        }

        private long getRecordTimestampMs() {
            if (startTimestampMs > 0) {
                return startTimestampMs;
            }
            return requestedRecordKey == null ? 0 : requestedRecordKey.getTimestampMs();
        }

    }

    private boolean storeWaveform(final long start,
                                  final long end,
                                  final int avgHr,
                                  final long arrhythmiaType,
                                  final List<Float> samples) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession session = db.getDaoSession();
            final long deviceId = DBHelper.getDevice(device, session).getId();
            final WithingsEcgSummarySample existing = findSummary(session, deviceId, start);
            final WithingsEcgSummarySample summary = new WithingsEcgSummarySample(
                existing != null ? existing.getEcgId() : null,
                deviceId,
                DBHelper.getUser(session).getId(),
                start,
                end,
                Math.max(avgHr, 0),
                arrhythmiaType
            );

            session.runInTx(() -> {
                session.getWithingsEcgSummarySampleDao().insertOrReplace(summary);

                session.getWithingsEcgDataSampleDao().queryBuilder()
                    .where(WithingsEcgDataSampleDao.Properties.EcgId.eq(summary.getEcgId()))
                    .buildDelete()
                    .executeDeleteWithoutDetachingEntities();

                final List<WithingsEcgDataSample> waveformSamples = new ArrayList<>(samples.size());
                for (int i = 0; i < samples.size(); i++) {
                    final int delta = (int) Math.round((i * 1000d) / ECG_SAMPLE_RATE_HZ);
                    waveformSamples.add(new WithingsEcgDataSample(summary.getEcgId(), delta, samples.get(i)));
                }
                session.getWithingsEcgDataSampleDao().insertInTx(waveformSamples);
            });
            logger.info("Stored Withings ECG ts={} samples={}", start, samples.size());
            return true;
        } catch (final Exception e) {
            logger.error("Failed storing Withings ECG data for ts={}", start, e);
            return false;
        }
    }

    @Nullable
    private static WithingsEcgSummarySample findSummary(final DaoSession session, final long deviceId, final long start) {
        final List<WithingsEcgSummarySample> summaries = session.getWithingsEcgSummarySampleDao().queryBuilder()
            .where(
                WithingsEcgSummarySampleDao.Properties.DeviceId.eq(deviceId),
                WithingsEcgSummarySampleDao.Properties.StartTimestamp.eq(start)
            )
            .build()
            .list();
        return summaries.isEmpty() ? null : summaries.get(0);
    }
}
