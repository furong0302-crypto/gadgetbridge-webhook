package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.ChecksumCalculator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminByteBufferReader;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.GFDIStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.GenericStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public abstract class GFDIMessage {
    protected static final Logger LOG = LoggerFactory.getLogger(GFDIMessage.class);
    protected final ByteBuffer response = ByteBuffer.allocate(10 * 1024); // FIXME we should allocate the minimum necessary for each message
    protected GFDIStatusMessage statusMessage;
    protected GarminMessage garminMessage;

    public static GFDIMessage parseIncoming(byte[] message) {
        final MessageReader messageReader = new MessageReader(message);

        boolean supportedType = false;
        int messageType = messageReader.readShort();
        try {
            if ((messageType & 0x8000) != 0) {
                // final int sequenceNumber = (messageType >> 8) & 0x7f;
                messageType = (messageType & 0xff) + 5000;
            }
            final GarminMessage garminMessage = GarminMessage.fromId(messageType);
            if (garminMessage == null) {
                return new UnhandledMessage(messageType);
            } else if (garminMessage.objectClass == null) {
                return new UnhandledMessage(garminMessage);
            }
            final Method m = garminMessage.objectClass.getMethod("parseIncoming", MessageReader.class, GarminMessage.class);
            supportedType = true;
            return garminMessage.objectClass.cast(m.invoke(null, messageReader, garminMessage));
        } catch (final Exception e) {
            LOG.error("UNHANDLED GFDI MESSAGE TYPE {}, MESSAGE {}", messageType, message, e);
            return new UnhandledMessage(messageType);
        } finally {
            messageReader.warnIfLeftover(messageType, supportedType);
        }
    }

    public GarminMessage getGarminMessage() {
        return garminMessage;
    }

    protected abstract boolean generateOutgoing();

    public byte[] getOutgoingMessage() {
        response.clear();
        boolean toSend = generateOutgoing();
        response.order(ByteOrder.LITTLE_ENDIAN);
        if (!toSend)
            return null;
        addLengthAndChecksum();
        response.flip();

        final byte[] packet = new byte[response.limit()];
        response.get(packet);
        return packet;
    }

    protected GFDIStatusMessage getStatusMessage() {
        return new GenericStatusMessage(garminMessage, Status.ACK);
    }

    public List<GBDeviceEvent> getGBDeviceEvent() {
        return Collections.emptyList();
    }

    public byte[] getAckBytestream() {
        if (null == this.statusMessage) {
            return null;
        }
        return this.statusMessage.getOutgoingMessage();
    }

    private void addLengthAndChecksum() {
        response.putShort(0, (short) (response.position() + 2));
        response.putShort((short) ChecksumCalculator.computeCrc(response.asReadOnlyBuffer(), 0, response.position()));
    }

    public enum GarminMessage {
        RESPONSE(5000, GFDIStatusMessage.class), //TODO: STATUS is a better name?
        DOWNLOAD_REQUEST(5002, DownloadRequestMessage.class),
        UPLOAD_REQUEST(5003, UploadRequestMessage.class),
        FILE_TRANSFER_DATA(5004, FileTransferDataMessage.class),
        CREATE_FILE(5005, CreateFileMessage.class),
        DELETE_FILE(5006, null),
        FILTER(5007, FilterMessage.class),
        SET_FILE_FLAG(5008, SetFileFlagsMessage.class),
        FILE_AVAILABLE(5009, FileAvailableMessage.class),
        FIT_DEFINITION(5011, FitDefinitionMessage.class),
        FIT_DATA(5012, FitDataMessage.class),
        WEATHER_REQUEST(5014, WeatherMessage.class),
        WEATHER_ALERT(5015, null),
        LIVE_TRACK_TRACKING_REQUEST(5016, null),
        EPHEMERIS_DATA_REQUEST(5019, null),
        EPHEMERIS_DATA(5020, null),
        CANCEL_FILE_TRANSFER(5022, null),
        BATTERY_STATUS(5023, BatteryStatusMessage.class),
        DEVICE_INFORMATION(5024, DeviceInformationMessage.class),
        LIVE_TRACK_STOP_TRACKING(5025, null),
        DEVICE_SETTINGS(5026, SetDeviceSettingsMessage.class),
        QUEUED_DOWNLOAD(5027, null),
        EPHEMERIS_EPO_REQUEST(5028, null),
        EPHEMERIS_EPO_DATA(5029, null),
        SYSTEM_EVENT(5030, SystemEventMessage.class),
        SUPPORTED_FILE_TYPES_REQUEST(5031, SupportedFileTypesMessage.class),
        NOTIFICATION_UPDATE(5033, NotificationUpdateMessage.class),
        NOTIFICATION_CONTROL(5034, NotificationControlMessage.class),
        NOTIFICATION_DATA(5035, NotificationDataMessage.class),
        NOTIFICATION_SUBSCRIPTION(5036, NotificationSubscriptionMessage.class),
        SYNCHRONIZATION(5037, SynchronizationMessage.class),
        FIND_MY_PHONE_REQUEST(5039, FindMyPhoneRequestMessage.class),
        FIND_MY_PHONE_CANCEL(5040, FindMyPhoneCancelMessage.class),
        MUSIC_CONTROL(5041, MusicControlMessage.class),
        MUSIC_CONTROL_CAPABILITIES(5042, MusicControlCapabilitiesMessage.class),
        PROTOBUF_REQUEST(5043, ProtobufMessage.class),
        PROTOBUF_RESPONSE(5044, ProtobufMessage.class),
        CANCEL_PROTOBUF(5045, null),
        LIVE_TRACK_AUTO_START(5046, null),
        LIVE_TRACK_AUTO_START_CANCEL(5047, null),
        LIVE_TRACK_AUTO_START_FAILURE(5048, null),
        MUSIC_CONTROL_ENTITY_UPDATE(5049, MusicControlEntityUpdateMessage.class),
        CONFIGURATION(5050, ConfigurationMessage.class),
        CURRENT_TIME_REQUEST(5052, CurrentTimeRequestMessage.class),
        COMPRESSED_FILE_DATA(5054, null),
        AUTH_NEGOTIATION(5101, AuthNegotiationMessage.class),
        LTK_RECONNECT(5102,null),
        STK_BEGIN_GENERATION(5103,null),
        CONFIRM_NUMBER(5104,null),
        STK_RANDOM_NUMBER(5105,null),
        STK_GENERATION_STATUS(5106,null),
        LTK_KEY_DISTRIBUTION(5107,null),
        SESSION_SKD_DISTRIBUTION(5108,null),
        SESSION_KEY_VERIFICATION(5109,null),
        PASSKEY_REDISPLAY(5110,null),
        SECURE_SESSION(5111,null),
        OUT_OF_BAND_PASSKEY_DATA(5112,null),
        ;

        @Nullable
        private final Class<? extends GFDIMessage> objectClass;
        private final int id;

        GarminMessage(int id, @Nullable Class<? extends GFDIMessage> objectClass) {
            this.id = id;
            this.objectClass = objectClass;
        }

        @Nullable
        public static GarminMessage fromId(final int id) {
            for (final GarminMessage garminMessage : GarminMessage.values()) {
                if (garminMessage.getId() == id) {
                    return garminMessage;
                }
            }
            return null;
        }

        public int getId() {
            return id;
        }
    }

    public enum Status {
        ACK,
        NAK,
        UNSUPPORTED,
        DECODE_ERROR,
        CRC_ERROR,
        LENGTH_ERROR;

        public static Status fromCode(final int code) {
            for (final Status status : Status.values()) {
                if (status.ordinal() == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status code " + code);
        }

    }

    public static class MessageReader extends GarminByteBufferReader {

        private final int payloadSize;

        public MessageReader(byte[] data) {
            super(data);
            this.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            this.payloadSize = readShort(); //includes CRC
            checkSize();
            checkCRC();
            this.byteBuffer.limit(payloadSize - 2); //remove CRC
        }

        public void skip(int offset) {
            if (remaining() < offset) throw new IllegalStateException();
            byteBuffer.position(byteBuffer.position() + offset);
        }

        private void checkSize() {
            if (payloadSize != byteBuffer.capacity()) {
                LOG.error("Received GFDI packet with invalid length: {} vs {}", payloadSize, byteBuffer.capacity());
                throw new IllegalArgumentException("Received GFDI packet with invalid length");
            }
        }

        private void checkCRC() {
            final int crc = Short.toUnsignedInt(byteBuffer.getShort(payloadSize - 2));
            final int correctCrc = ChecksumCalculator.computeCrc(byteBuffer.asReadOnlyBuffer(), 0, payloadSize - 2);
            if (crc != correctCrc) {
                LOG.error("Received GFDI packet with invalid CRC: {} vs {}", crc, correctCrc);
                throw new IllegalArgumentException("Received GFDI packet with invalid CRC");
            }
        }

        public void warnIfLeftover(int messageType, boolean supportedType) {
            if (byteBuffer.hasRemaining() && byteBuffer.position() < (byteBuffer.limit())) {
                int pos = byteBuffer.position();
                int numBytes = (byteBuffer.limit()) - byteBuffer.position();
                byte[] leftover = new byte[numBytes];
                byteBuffer.get(leftover);
                byteBuffer.position(pos);
                if (supportedType){
                    LOG.warn("Leftover bytes when parsing message type {}. Bytes: {}, complete message: {}", messageType, GB.hexdump(leftover), GB.hexdump(byteBuffer.array()));
                } else {
                    LOG.warn("Unknown message type {}. Bytes: {}", messageType, GB.hexdump(leftover));
                }
            }
        }
    }
}
