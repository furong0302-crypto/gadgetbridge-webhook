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

public class ActivityTarget extends WithingsStructure {

    public static final int GOAL_TYPE_STEPS = 0;
    public static final int GOAL_TYPE_SLEEP = 1;
    public static final int GOAL_TYPE_SWIM = 2;

    private int goalType;
    private int value;

    public ActivityTarget(int goalType, int value) {
        this.goalType = goalType;
        this.value = value;
    }

    public int getGoalType() {
        return goalType;
    }

    public int getValue() {
        return value;
    }

    @Override
    public short getLength() {
        return 12;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer rawDataBuffer) {
        rawDataBuffer.putInt(goalType);
        rawDataBuffer.putInt(value);
    }

    @Override
    public short getType() {
        return WithingsStructureType.ACTIVITY_TARGET;
    }
}
