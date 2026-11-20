/*  Copyright (C) 2026 Will

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.v2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.CobsCoDec;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class CommunicatorV2Test extends TestBase {
    private GarminSupport support;
    private TransactionBuilder builder;
    private CommunicatorV2 communicator;

    @Before
    public void setUpCommunicator() {
        support = mock(GarminSupport.class);
        builder = mock(TransactionBuilder.class, RETURNS_SELF);
        when(support.createTransactionBuilder(anyString())).thenReturn(builder);
        communicator = new CommunicatorV2(support);
    }

    @Test
    public void singleCharacteristicWithWriteAndNotify() {
        final BluetoothGattCharacteristic characteristic = addReceive(0,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);

        assertTrue(communicator.initializeDevice(builder));
        verifyInitialization(characteristic, characteristic);
    }

    @Test
    public void singleCharacteristicWithWriteWithoutResponseAndNotify() {
        final BluetoothGattCharacteristic characteristic = addReceive(0,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);

        assertTrue(communicator.initializeDevice(builder));
        verifyInitialization(characteristic, characteristic);
    }

    @Test
    public void pairedCharacteristicsTakePriorityOverEarlierBidirectionalCharacteristic() {
        addReceive(0, BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        final BluetoothGattCharacteristic receive = addReceive(1, BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        final UUID sendUuid = CommunicatorV2.UUID_CHARACTERISITC_GARMIN_ML_TX[1];
        final BluetoothGattCharacteristic send = new BluetoothGattCharacteristic(sendUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE, 0);
        when(support.getCharacteristic(sendUuid)).thenReturn(send);

        assertTrue(communicator.initializeDevice(builder));
        verifyInitialization(receive, send);
    }

    @Test
    public void skipsReceiveOnlyCharacteristic() {
        addReceive(0, BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        final BluetoothGattCharacteristic characteristic = addReceive(1,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);

        assertTrue(communicator.initializeDevice(builder));
        verifyInitialization(characteristic, characteristic);
    }

    @Test
    public void receiveOnlyCharacteristicAllowsV1Fallback() {
        addReceive(0, BluetoothGattCharacteristic.PROPERTY_NOTIFY);

        assertFalse(communicator.initializeDevice(builder));
        verifyNoInteractions(builder);
    }

    @Test
    public void writeOnlyCharacteristicAllowsV1Fallback() {
        addReceive(0, BluetoothGattCharacteristic.PROPERTY_WRITE);

        assertFalse(communicator.initializeDevice(builder));
        verifyNoInteractions(builder);
    }

    @Test
    public void missingCharacteristicsAllowV1Fallback() {
        assertFalse(communicator.initializeDevice(builder));
        verifyNoInteractions(builder);
    }

    @Test
    public void shortGfdiRegisterResponseRoutesOutgoingMessages() {
        final BluetoothGattCharacteristic characteristic = addReceive(0,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        assertTrue(communicator.initializeDevice(builder));
        clearInvocations(builder);

        // Captured on the Forerunner 235: 14 bytes, without the reliable flag.
        assertTrue(communicator.onCharacteristicChanged(null, characteristic,
                GB.hexStringToByteArray("0001020000000000000001000002")));
        final byte[] payload = new byte[]{1, 2, 3};
        communicator.sendMessage("test GFDI", payload);

        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(builder).write(eq(characteristic), written.capture());
        final byte[] encoded = CobsCoDec.encode(payload);
        final byte[] expected = new byte[encoded.length + 1];
        expected[0] = 2;
        System.arraycopy(encoded, 0, expected, 1, encoded.length);
        assertArrayEquals(expected, written.getValue());
        verify(builder).queue();
    }

    @Test
    public void shortRegisterResponseUsesPlainMlDespiteReliableRequest() {
        assertTransferWriter("0001020000000000000018200002", true, CommunicatorV2.MlServiceWriter.class);
    }

    @Test
    public void explicitUnreliableRegisterResponseUsesPlainMl() {
        assertTransferWriter("000102000000000000001820000200", false, CommunicatorV2.MlServiceWriter.class);
    }

    @Test
    public void reliableRegisterResponseStillUsesMlr() {
        assertTransferWriter("000102000000000000001820008201", true, CommunicatorV2.MlrServiceWriter.class);
    }

    private void assertTransferWriter(final String response, final boolean reliable,
                                      final Class<? extends CommunicatorV2.ServiceWriter> writerClass) {
        final BluetoothGattCharacteristic characteristic = addReceive(0,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        assertTrue(communicator.initializeDevice(builder));
        final CommunicatorV2.ServiceCallback callback = mock(CommunicatorV2.ServiceCallback.class);
        communicator.startTransfer(callback, reliable);

        assertTrue(communicator.onCharacteristicChanged(null, characteristic, GB.hexStringToByteArray(response)));

        final ArgumentCaptor<CommunicatorV2.ServiceWriter> writer = ArgumentCaptor.forClass(CommunicatorV2.ServiceWriter.class);
        verify(callback).onConnect(writer.capture());
        assertTrue(writerClass.isInstance(writer.getValue()));
        communicator.dispose();
    }

    private BluetoothGattCharacteristic addReceive(final int index, final int properties) {
        final UUID uuid = CommunicatorV2.UUID_CHARACTERISITC_GARMIN_ML_RX[index];
        final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(uuid, properties, 0);
        when(support.getCharacteristic(uuid)).thenReturn(characteristic);
        return characteristic;
    }

    private void verifyInitialization(final BluetoothGattCharacteristic receive,
                                      final BluetoothGattCharacteristic send) {
        verify(builder).notify(receive, true);
        final ArgumentCaptor<byte[]> written = ArgumentCaptor.forClass(byte[].class);
        verify(builder).write(eq(send), written.capture());
        assertArrayEquals(GB.hexStringToByteArray("00050200000000000000000000"), written.getValue());
    }
}
