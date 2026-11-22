/*  Copyright (C) 2026 rejunte

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiManualSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiManualSample;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileId;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class ManualSamplesParserTest {
    private static final String SPO2_97_PACKET = "EFB9856AF4011800FFEFB9856A0261511E4648";
    private static final String STRESS_40_PACKET = "65C0856AF4011800FF65C0856A03288CA20578";
    private static final String STRESS_27_PACKET = "D1C3856AF4011800FFD1C3856A031BFBF91375";

    @Test
    public void decodeVersion1Spo2() {
        final List<XiaomiManualSample> samples = decode(SPO2_97_PACKET);

        assertNotNull(samples);
        assertEquals(1, samples.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_SPO2, samples.get(0).getType().intValue());
        assertEquals(97, samples.get(0).getValue().intValue());
        assertEquals(1787148783000L, samples.get(0).getTimestamp());
    }

    @Test
    public void decodeVersion1Stress() {
        final List<XiaomiManualSample> stress40 = decode(STRESS_40_PACKET);
        final List<XiaomiManualSample> stress27 = decode(STRESS_27_PACKET);

        assertNotNull(stress40);
        assertEquals(1, stress40.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_STRESS, stress40.get(0).getType().intValue());
        assertEquals(40, stress40.get(0).getValue().intValue());

        assertNotNull(stress27);
        assertEquals(1, stress27.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_STRESS, stress27.get(0).getType().intValue());
        assertEquals(27, stress27.get(0).getValue().intValue());
    }

    // Redmi Watch 5 Active: 0xe0 header, manual measurements taken on the watch
    private static final String RW5A_SPO2_97_PACKET = "ede3016bf4011800e0ede3016b0261356c8e23";
    private static final String RW5A_STRESS_30_PACKET = "34e4016bf4011800e034e4016b031e08099fae";
    private static final String RW5A_HR_98_PACKET = "44e4016bf4011800e044e4016b0162f08164c8";
    private static final String RW5A_HR_106_PACKET = "64e4016bf4011800e064e4016b016a6a32d649";

    @Test
    public void decodeVersion1RedmiWatch5Active() {
        final List<XiaomiManualSample> spo2 = decode(RW5A_SPO2_97_PACKET);
        assertNotNull(spo2);
        assertEquals(1, spo2.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_SPO2, spo2.get(0).getType().intValue());
        assertEquals(97, spo2.get(0).getValue().intValue());
        assertEquals(0x6B01E3EDL * 1000, spo2.get(0).getTimestamp());

        final List<XiaomiManualSample> stress = decode(RW5A_STRESS_30_PACKET);
        assertNotNull(stress);
        assertEquals(1, stress.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_STRESS, stress.get(0).getType().intValue());
        assertEquals(30, stress.get(0).getValue().intValue());

        final List<XiaomiManualSample> hr98 = decode(RW5A_HR_98_PACKET);
        assertNotNull(hr98);
        assertEquals(1, hr98.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_HR, hr98.get(0).getType().intValue());
        assertEquals(98, hr98.get(0).getValue().intValue());
    }

    @Test
    public void decodeVersion1HeartRateAbove100() {
        final List<XiaomiManualSample> samples = decode(RW5A_HR_106_PACKET);

        assertNotNull(samples);
        assertEquals(1, samples.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_HR, samples.get(0).getType().intValue());
        assertEquals(106, samples.get(0).getValue().intValue());
    }

    @Test
    public void decodeVersion1RejectsOutOfRangeHeartRate() {
        final byte[] packet = GB.hexStringToByteArray(RW5A_HR_98_PACKET);
        packet[14] = (byte) 0xff;

        assertNull(decode(packet));
    }

    @Test
    public void decodeVersion1RejectsUnknownHeader() {
        final byte[] packet = GB.hexStringToByteArray(SPO2_97_PACKET);
        packet[8] = 0;

        assertNull(decode(packet));
    }

    @Test
    public void decodeVersion1RejectsUnknownType() {
        final byte[] packet = GB.hexStringToByteArray(SPO2_97_PACKET);
        packet[13] = 0x04;

        assertNull(decode(packet));
    }

    @Test
    public void decodeVersion1RejectsOutOfRangeValue() {
        final byte[] packet = GB.hexStringToByteArray(SPO2_97_PACKET);
        packet[14] = 101;

        assertNull(decode(packet));
    }

    /// 7-byte file id, padding byte, the given records, 4-byte CRC (not checked by the decoder).
    private static byte[] version2(final String records) {
        return GB.hexStringToByteArray("00000000000000" + "00" + records + "00000000");
    }

    @Test
    public void decodeVersion2() {
        final List<XiaomiManualSample> samples = ManualSamplesParser.decodeVersion2(version2(
                "8A90A965" + "12" + "63" +
                "C793A965" + "13" + "00" +
                "9698A965" + "13" + "1C" +
                "E79CA965" + "44" + "5A0E0000"));

        assertEquals(3, samples.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_SPO2, samples.get(0).getType().intValue());
        assertEquals(99, samples.get(0).getValue().intValue());
        assertEquals(0x65A9908AL * 1000, samples.get(0).getTimestamp());
        assertEquals(XiaomiManualSampleProvider.TYPE_STRESS, samples.get(1).getType().intValue());
        assertEquals(28, samples.get(1).getValue().intValue());
        assertEquals(XiaomiManualSampleProvider.TYPE_TEMPERATURE, samples.get(2).getType().intValue());
        assertEquals(0x0E5A, samples.get(2).getValue().intValue());
    }

    @Test
    public void decodeVersion2TwoByteValueIsStoredUnderTheOneByteType() {
        final List<XiaomiManualSample> samples = ManualSamplesParser.decodeVersion2(version2(
                "8A90A965" + "21" + "4800"));

        assertEquals(1, samples.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_HR, samples.get(0).getType().intValue());
        assertEquals(72, samples.get(0).getValue().intValue());
    }

    @Test
    public void decodeVersion2SkipsUnknownTypesByTheirLength() {
        final List<XiaomiManualSample> samples = ManualSamplesParser.decodeVersion2(version2(
                "8A90A965" + "65" + "780050003C00" +
                "9698A965" + "12" + "62"));

        assertEquals(1, samples.size());
        assertEquals(XiaomiManualSampleProvider.TYPE_SPO2, samples.get(0).getType().intValue());
        assertEquals(98, samples.get(0).getValue().intValue());
    }

    @Test
    public void decodeVersion2KeepsSamplesBeforeATruncatedRecord() {
        final List<XiaomiManualSample> samples = ManualSamplesParser.decodeVersion2(version2(
                "8A90A965" + "12" + "63" +
                "9698A965" + "44" + "5A0E"));

        assertEquals(1, samples.size());
        assertEquals(99, samples.get(0).getValue().intValue());
    }

    private static List<XiaomiManualSample> decode(final String hex) {
        return decode(GB.hexStringToByteArray(hex));
    }

    private static List<XiaomiManualSample> decode(final byte[] packet) {
        final XiaomiActivityFileId fileId = XiaomiActivityFileId.from(Arrays.copyOfRange(packet, 0, 7));
        return ManualSamplesParser.decodeVersion1(fileId, packet);
    }
}
