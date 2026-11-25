/*  Copyright (C) 2026 MedusasSphinx

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GearFitProtocolTest {

    private static byte[] hexToBytes(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @Test
    public void testHandshakeFota() {
        final byte[] packet = GearFitProtocol.handshakeFota();
        // port 77 (0x4D), len 4 LE (04000000), "ODIN" (4F44494E)
        assertArrayEquals(hexToBytes("4D040000004F44494E"), packet);
    }

    @Test
    public void testHandshakeFollowUp() {
        final byte[] packet = GearFitProtocol.handshakeFollowUp();
        // port 100 (0x64), len 8 LE, 0402010104020501
        assertArrayEquals(hexToBytes("64080000000402010104020501"), packet);
    }

    @Test
    public void testHandshakeFotaCommand() {
        final byte[] packet = GearFitProtocol.handshakeFotaCommand();
        // port 78 (0x4E), len 2 LE, 0101
        assertArrayEquals(hexToBytes("4E020000000101"), packet);
    }

    @Test
    public void testFindDevice() {
        final byte[] start = GearFitProtocol.findDevice(true);
        // port 5, len 2, 0100
        assertArrayEquals(hexToBytes("05020000000100"), start);

        final byte[] stop = GearFitProtocol.findDevice(false);
        // port 5, len 2, 0101
        assertArrayEquals(hexToBytes("05020000000101"), stop);
    }

    @Test
    public void testMediaVolume() {
        final byte[] packet = GearFitProtocol.mediaVolume(10);
        // port 6, len 2, type 1 (volume), 10 (0x0A)
        assertArrayEquals(hexToBytes("0602000000010A"), packet);

        final byte[] clamped = GearFitProtocol.mediaVolume(99);
        // clamped to MAX_MEDIA_VOLUME (15 = 0x0F)
        assertArrayEquals(hexToBytes("0602000000010F"), clamped);
    }

    @Test
    public void testAlarmClear() {
        final byte[] packet = GearFitProtocol.alarmClear();
        // port 10 (0x0A), len 1, 00
        assertArrayEquals(hexToBytes("0A0100000000"), packet);
    }

    @Test
    public void testSyncDone() {
        final byte[] packet = GearFitProtocol.syncDone();
        // port 2, len 4, 01000000
        assertArrayEquals(hexToBytes("020400000001000000"), packet);
    }

    @Test
    public void testGpsReady() {
        final byte[] packet = GearFitProtocol.gpsReady();
        // port 2, len 4, 12 LE (0C000000)
        assertArrayEquals(hexToBytes("02040000000C000000"), packet);
    }

    @Test
    public void testEncodeTemperature() {
        assertEquals(500, GearFitProtocol.encodeTemperature(5));
        assertEquals(-500, GearFitProtocol.encodeTemperature(-5));
        assertEquals(2500, GearFitProtocol.encodeTemperature(25));
        assertEquals(-2500, GearFitProtocol.encodeTemperature(-25));
        assertEquals(1500, GearFitProtocol.encodeTemperature(150));
        assertEquals(-1500, GearFitProtocol.encodeTemperature(-150));
        assertEquals(1500, GearFitProtocol.encodeTemperature(1500));
    }

    @Test
    public void testEncodeStringHasBOM() {
        final byte[] encoded = GearFitProtocol.encodeString("A");
        // BOM (FF FE) + 'A' (41 00)
        assertEquals(4, encoded.length);
        assertEquals((byte) 0xFF, encoded[0]);
        assertEquals((byte) 0xFE, encoded[1]);
        assertEquals((byte) 0x41, encoded[2]);
        assertEquals((byte) 0x00, encoded[3]);
    }

    @Test
    public void testFitnessParseRealCapture() {
        // Real fitness packet capture from OpenFit protocol dump:
        // 02 5C000000 040000000A000000FF 040100000008FFCF556500000052389C42A870AD40 00... 01...
        // Payload starts at offset 5 (skipping port and 4-byte length)
        final String captureHex = "040000000A000000FF040100000008FFCF556500000052389C42A870AD40000100000045B5C8552300000000002A430000824235E60200CD7FCF12F1490200D1FB01001198020022BF0200010100000032EED0551027000000000000";
        final byte[] payload = hexToBytes(captureHex);

        final GearFitFitnessParser.Result result = GearFitFitnessParser.parse(payload);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.pedometer.size());

        final GearFitFitnessParser.PedometerRecord pedo = result.pedometer.get(0);
        assertEquals(101, pedo.steps);
        assertEquals(1439694600000L, pedo.timestampMillis);
        assertEquals(78.11f, pedo.distanceMeters, 0.01f);
        assertEquals(5.42f, pedo.calories, 0.01f);
    }

    @Test
    public void testFitnessParseExerciseRecord() {
        // Construct a synthetic envelope + COACHING_RESULT_RECORD (type 12)
        final ByteBuffer buf = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        // 9-byte envelope
        buf.putInt(4);
        buf.putInt(10);
        buf.put((byte) 0xFF);

        // Record type 12 (COACHING_RESULT_RECORD), count 1
        buf.put(GearFitConstants.FITNESS_COACHING_RESULT_RECORD);
        buf.putInt(1);

        // Exercise record fields (in wire order):
        buf.putInt(1439694600); // timestamp
        buf.putInt(1800);       // duration seconds
        buf.putFloat(250.5f);   // calories
        buf.putInt(145);        // avg heart rate
        buf.putFloat(5000.0f);  // distance meters
        buf.put((byte) 3);      // fitness level
        buf.putInt(GearFitConstants.EXERCISE_RUNNING); // type
        buf.putFloat(2.77f);    // avg speed
        buf.putFloat(3.5f);     // max speed
        buf.putFloat(120.0f);   // max alt
        buf.putFloat(45.0f);    // min alt
        buf.putInt(172);        // max heart rate
        buf.putFloat(100.0f);   // incline dist
        buf.putFloat(80.0f);    // decline dist

        final byte[] payload = new byte[buf.position()];
        System.arraycopy(buf.array(), 0, payload, 0, payload.length);

        final GearFitFitnessParser.Result result = GearFitFitnessParser.parse(payload);
        assertNotNull(result);
        assertEquals(1, result.exercises.size());

        final GearFitFitnessParser.ExerciseRecord ex = result.exercises.get(0);
        assertEquals(1439694600000L, ex.timestampMillis);
        assertEquals(1800, ex.durationSeconds);
        assertEquals(250.5f, ex.calories, 0.01f);
        assertEquals(145, ex.averageHeartRate);
        assertEquals(5000.0f, ex.distanceMeters, 0.01f);
        assertEquals(3, ex.fitnessLevel);
        assertEquals(GearFitConstants.EXERCISE_RUNNING, ex.type);
        assertEquals(2.77f, ex.averageSpeed, 0.01f);
        assertEquals(3.5f, ex.maxSpeed, 0.01f);
        assertEquals(120.0f, ex.maxAltitude, 0.01f);
        assertEquals(45.0f, ex.minAltitude, 0.01f);
        assertEquals(172, ex.maxHeartRate);
        assertEquals(100.0f, ex.inclineDistance, 0.01f);
        assertEquals(80.0f, ex.declineDistance, 0.01f);
    }
}
