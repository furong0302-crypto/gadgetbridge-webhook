/*  Copyright (C) 2023-2024 Frank Ertl

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures;

import java.nio.ByteBuffer;

public class AlarmSettings extends WithingsStructure {
    private short hour;
    private short minute;
    private short dayOfWeek;
    private short dayOfMonth;
    private short month;
    private short year;
    private short smartWakeupMinutes;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public AlarmSettings() {}

    /**
     * Returns whether this alarm is enabled (bit 7 of dayOfWeek flags).
     */
    public boolean isEnabled() {
        return (dayOfWeek & 0x80) != 0;
    }

    /**
     * Returns the repetition bitmask in Gadgetbridge format (MON=1..SUN=64),
     * extracted from the Withings flags byte (bits 0-6).
     * Returns 0 (ALARM_ONCE) if no day bits are set (one-time alarm).
     */
    public int getRepetitionMask() {
        int gbRepetition = 0;
        // Withings: bit0=Sun, bit1=Mon, bit2=Tue, bit3=Wed, bit4=Thu, bit5=Fri, bit6=Sat
        // GB:       SUN=64, MON=1, TUE=2, WED=4, THU=8, FRI=16, SAT=32
        if ((dayOfWeek & 0x01) != 0) gbRepetition |= 64;  // Sun
        if ((dayOfWeek & 0x02) != 0) gbRepetition |= 1;   // Mon
        if ((dayOfWeek & 0x04) != 0) gbRepetition |= 2;   // Tue
        if ((dayOfWeek & 0x08) != 0) gbRepetition |= 4;   // Wed
        if ((dayOfWeek & 0x10) != 0) gbRepetition |= 8;   // Thu
        if ((dayOfWeek & 0x20) != 0) gbRepetition |= 16;  // Fri
        if ((dayOfWeek & 0x40) != 0) gbRepetition |= 32;  // Sat
        return gbRepetition;
    }

    public short getSmartWakeupMinutes() {
        return smartWakeupMinutes;
    }

    public short getHour() {
        return hour;
    }

    public void setHour(short hour) {
        this.hour = hour;
    }

    public short getMinute() {
        return minute;
    }

    public void setMinute(short minute) {
        this.minute = minute;
    }

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(short dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public short getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(short dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public short getMonth() {
        return month;
    }

    public void setMonth(short month) {
        this.month = month;
    }

    public short getYear() {
        return year;
    }

    public void setYear(short year) {
        this.year = year;
    }

    public short getYetUnkown() {
        return smartWakeupMinutes;
    }

    public void setSmartWakeupMinutes(short smartWakeupMinutes) {
        this.smartWakeupMinutes = smartWakeupMinutes;
    }

    @Override
    public short getLength() {
        return 11;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put((byte)hour);
        buffer.put((byte)minute);
        buffer.put((byte)dayOfWeek);
        buffer.put((byte)dayOfMonth);
        buffer.put((byte)month);
        buffer.put((byte)year);
        buffer.put((byte)smartWakeupMinutes);
    }

    @Override
    public short getType() {
        return WithingsStructureType.ALARM;
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 7) {
            hour = (short) (rawDataBuffer.get() & 0xFF);
            minute = (short) (rawDataBuffer.get() & 0xFF);
            dayOfWeek = (short) (rawDataBuffer.get() & 0xFF);
            dayOfMonth = (short) (rawDataBuffer.get() & 0xFF);
            month = (short) (rawDataBuffer.get() & 0xFF);
            year = (short) (rawDataBuffer.get() & 0xFF);
            smartWakeupMinutes = (short) (rawDataBuffer.get() & 0xFF);
        }
    }

    @Override
    public boolean withEndOfMessage() {
        return true;
    }
}
