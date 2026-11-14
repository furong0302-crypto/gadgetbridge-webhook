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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.HuamiWorkoutScreenActivityType;

public enum WithingsActivityType {

    WALKING(1, (byte) 2, (short) 0x0000, ActivityKind.WALKING),
    RUNNING(2, (byte) 2, (short) 0x0000, ActivityKind.RUNNING),
    HIKING(3, (byte) 2, (short) 0x0000, ActivityKind.HIKING),
    BMX(5, (byte) 1, (short) 0x0001, ActivityKind.BMX),
    BIKING(6, (byte) 3, (short) 0x0000, ActivityKind.CYCLING),
    SWIMMING(7, (byte) 1, (short) 0x0001, ActivityKind.SWIMMING),
    SURFING(8, (byte) 1, (short) 0x0001, ActivityKind.SURFING),
    KITESURFING(9, (byte) 1, (short) 0x0001, ActivityKind.KITESURFING),
    WINDSURFING(10, (byte) 3, (short) 0x0001, ActivityKind.WINDSURFING),
    BODYBOARDING(11, (byte) 1, (short) 0x0001, ActivityKind.SURFING),
    TENNIS(12, (byte) 1, (short) 0x0001, ActivityKind.TENNIS),
    PINGPONG(13, (byte) 1, (short) 0x0001, ActivityKind.PINGPONG),
    SQUASH(14, (byte) 1, (short) 0x0001, ActivityKind.SQUASH),
    BADMINTON(15, (byte) 1, (short) 0x0001, ActivityKind.BADMINTON),
    WEIGHTLIFTING(16, (byte) 1, (short) 0x0001, ActivityKind.WEIGHTLIFTING),
    GYMNASTICS(17, (byte) 1, (short) 0x0001, ActivityKind.GYMNASTICS),
    ELLIPTICAL(18, (byte) 1, (short) 0x0000, ActivityKind.ELLIPTICAL_TRAINER),
    PILATES(19, (byte) 1, (short) 0x0001, ActivityKind.PILATES),
    BASKETBALL(20, (byte) 1, (short) 0x0001, ActivityKind.BASKETBALL),
    SOCCER(21, (byte) 1, (short) 0x0001, ActivityKind.SOCCER),
    FOOTBALL(22, (byte) 1, (short) 0x0001, ActivityKind.AMERICAN_FOOTBALL),
    RUGBY(23, (byte) 1, (short) 0x0001, ActivityKind.RUGBY),
    VOLLEYBALL(24, (byte) 1, (short) 0x0001, ActivityKind.VOLLEYBALL),
    WATER_POLO(25, (byte) 1, (short) 0x0001, ActivityKind.WATER_POLO),
    RIDING(26, (byte) 2, (short) 0x0000, ActivityKind.HORSE_RIDING),
    GOLFING(27, (byte) 1, (short) 0x0001, ActivityKind.GOLF),
    YOGA(28, (byte) 1, (short) 0x0001, ActivityKind.YOGA),
    DANCING(29, (byte) 1, (short) 0x0001, ActivityKind.DANCE),
    BOXING(30, (byte) 1, (short) 0x0001, ActivityKind.BOXING),
    FENCING(31, (byte) 1, (short) 0x0001, ActivityKind.FENCING),
    WRESTLING(32, (byte) 1, (short) 0x0001, ActivityKind.WRESTLING),
    MARTIAL_ARTS(33, (byte) 1, (short) 0x0001, ActivityKind.MARTIAL_ARTS),
    SKIING(34, (byte) 3, (short) 0x0000, ActivityKind.SKIING),
    SNOWBOARDING(35, (byte) 3, (short) 0x0000, ActivityKind.SNOWBOARDING),
    OTHER(36, (byte) 3, (short) 0x0000, ActivityKind.ACTIVITY),
    ZUMBA(188, (byte) 1, (short) 0x0001, ActivityKind.ZUMBA),
    BASEBALL(191, (byte) 1, (short) 0x0001, ActivityKind.BASEBALL),
    HANDBALL(192, (byte) 1, (short) 0x0001, ActivityKind.HANDBALL),
    HOCKEY(193, (byte) 1, (short) 0x0001, ActivityKind.HOCKEY),
    ICEHOCKEY(194, (byte) 1, (short) 0x0001, ActivityKind.ICE_HOCKEY),
    CLIMBING(195, (byte) 1, (short) 0x0001, ActivityKind.CLIMBING),
    ICESKATING(196, (byte) 1, (short) 0x0001, ActivityKind.ICE_SKATING),
    INDOOR_WALKING(306, (byte) 2, (short) 0x0001, ActivityKind.INDOOR_WALKING),
    INDOOR_RUNNING(307, (byte) 1, (short) 0x0000, ActivityKind.INDOOR_RUNNING),
    INDOOR_CYCLING(308, (byte) 3, (short) 0x0001, ActivityKind.INDOOR_CYCLING),
    PADDLEBOARDING(455, (byte) 2, (short) 0x0000, ActivityKind.STAND_UP_PADDLEBOARDING),
    PADEL(456, (byte) 1, (short) 0x0000, ActivityKind.PADEL),
    GAMING(457, (byte) 1, (short) 0x0000, ActivityKind.VIDEO_GAMING),
    STAIR_STEPPER(491, (byte) 1, (short) 0x0000, ActivityKind.STAIR_STEPPER),
    SKATEBOARDING(492, (byte) 3, (short) 0x0000, ActivityKind.SKATEBOARDING),
    PARKOUR(493, (byte) 3, (short) 0x0000, ActivityKind.PARKOUR),
    KAYAKING(494, (byte) 3, (short) 0x0000, ActivityKind.KAYAKING),
    CANOEING(495, (byte) 3, (short) 0x0000, ActivityKind.CANOEING),
    FISHING(497, (byte) 1, (short) 0x0000, ActivityKind.FISHING),
    TRAIL_RUNNING(498, (byte) 3, (short) 0x0000, ActivityKind.TRAIL_RUN),
    SNOWSHOEING(499, (byte) 3, (short) 0x0000, ActivityKind.SNOWSHOE),
    PAINTBALL(500, (byte) 3, (short) 0x0000, ActivityKind.TEAM_SPORT),
    ARCHERY(501, (byte) 1, (short) 0x0000, ActivityKind.ARCHERY),
    BIATHLON(504, (byte) 3, (short) 0x0000, ActivityKind.BIATHLON),
    BOCCE(505, (byte) 1, (short) 0x0000, ActivityKind.BOCCE),
    PETANQUE(506, (byte) 1, (short) 0x0000, ActivityKind.BOCCE),
    PARAGLIDING(507, (byte) 1, (short) 0x0000, ActivityKind.PARAGLIDING),
    FRISBEE(508, (byte) 1, (short) 0x0000, ActivityKind.FRISBEE),
    SKYDIVING(509, (byte) 1, (short) 0x0000, ActivityKind.SKY_DIVING),
    PICKLEBALL(510, (byte) 1, (short) 0x0000, ActivityKind.PICKLEBALL),
    CORNHOLE(511, (byte) 1, (short) 0x0000, ActivityKind.ACTIVITY),
    DODGEBALL(512, (byte) 1, (short) 0x0000, ActivityKind.DODGEBALL),
    ULTIMATE(513, (byte) 1, (short) 0x0000, ActivityKind.ULTIMATE_DISC),
    TEQBALL(514, (byte) 1, (short) 0x0000, ActivityKind.ACTIVITY),
    PUSHING_WHEELCHAIR_FAST(515, (byte) 3, (short) 0x0000, ActivityKind.PUSH_RUN_SPEED),
    PUSHING_WHEELCHAIR_REGULAR(516, (byte) 3, (short) 0x0000, ActivityKind.PUSH_WALK_SPEED),
    TRACK_AND_FIELD(517, (byte) 3, (short) 0x0000, ActivityKind.TRACK_AND_FIELD),
    TRACK_CYCLING(518, (byte) 3, (short) 0x0000, ActivityKind.CYCLING),
    PENTATHLON(519, (byte) 3, (short) 0x0000, ActivityKind.MULTISPORT),
    SPORT_SHOOTING(520, (byte) 1, (short) 0x0000, ActivityKind.SHOOTING),
    TRIATHLON(521, (byte) 3, (short) 0x0000, ActivityKind.TRIATHLON),
    PLATFORM_DIVING(522, (byte) 3, (short) 0x0000, ActivityKind.DIVING),
    MOUNTAIN_BIKING(523, (byte) 3, (short) 0x0000, ActivityKind.MOUNTAIN_BIKE),
    GRAVEL_BIKING(524, (byte) 3, (short) 0x0000, ActivityKind.GRAVEL_BIKE),
    E_BIKING(525, (byte) 3, (short) 0x0000, ActivityKind.E_BIKE),
    E_MOUNTAIN_BIKING(526, (byte) 3, (short) 0x0000, ActivityKind.E_MOUNTAIN_BIKE),
    VELOMOBILE(528, (byte) 3, (short) 0x0000, ActivityKind.CYCLING),
    NORDIC_SKIING(530, (byte) 3, (short) 0x0000, ActivityKind.CROSS_COUNTRY_SKIING),
    ROLLER_SKIING(531, (byte) 3, (short) 0x0000, ActivityKind.ROLLER_SKATING),
    RACQUETBALL(532, (byte) 1, (short) 0x0000, ActivityKind.RACQUETBALL),
    MUAY_THAI(535, (byte) 1, (short) 0x0001, ActivityKind.MUAY_THAI),
    TAEKWONDO(536, (byte) 1, (short) 0x0001, ActivityKind.TAEKWONDO),
    JUDO(537, (byte) 1, (short) 0x0001, ActivityKind.JUDO),
    TRAMPOLINE(538, (byte) 1, (short) 0x0001, ActivityKind.TRAMPOLINE),
    STANDING_FRAME(539, (byte) 1, (short) 0x0001, ActivityKind.ACTIVITY),
    WALKING_WITH_WALKER(542, (byte) 2, (short) 0x0000, ActivityKind.WALKING),
    WALKING_WITH_CANE(543, (byte) 2, (short) 0x0000, ActivityKind.WALKING),
    BREAKING(544, (byte) 1, (short) 0x0001, ActivityKind.BREAKING),
    CROSSFIT(546, (byte) 1, (short) 0x0001, ActivityKind.CROSSFIT),
    SPIN_CLASS(547, (byte) 1, (short) 0x0001, ActivityKind.SPINNING),
    CRICKET(548, (byte) 1, (short) 0x0001, ActivityKind.CRICKET),
    FLAMENCO_DANCING(549, (byte) 1, (short) 0x0001, ActivityKind.DANCE),
    HIIT(550, (byte) 1, (short) 0x0001, ActivityKind.HIIT),
    MEDITATION(551, (byte) 1, (short) 0x0001, ActivityKind.MEDITATION),
    STRETCHING(552, (byte) 1, (short) 0x0001, ActivityKind.STRETCHING),
    YARD_WORK_GARDENING(553, (byte) 1, (short) 0x0001, ActivityKind.ACTIVITY),
    PUBLIC_SPEAKING(555, (byte) 1, (short) 0x0001, ActivityKind.ACTIVITY),
    SPIKEBALL(556, (byte) 1, (short) 0x0001, ActivityKind.TEAM_SPORT),
    LACROSSE(557, (byte) 1, (short) 0x0001, ActivityKind.LACROSSE),
    DOG_WALKING(559, (byte) 2, (short) 0x0000, ActivityKind.OUTDOOR_WALKING),
    BREATHING_EXERCISES(560, (byte) 1, (short) 0x0001, ActivityKind.BREATHWORK),
    PUSHING_STROLLER(562, (byte) 2, (short) 0x0000, ActivityKind.OUTDOOR_WALKING),
    TODDLER_WEARING(563, (byte) 2, (short) 0x0000, ActivityKind.OUTDOOR_WALKING),
    BOWLING(564, (byte) 1, (short) 0x0000, ActivityKind.BOWLING),
    LASER_TAG(565, (byte) 1, (short) 0x0000, ActivityKind.LASER_TAG),
    NORDIC_WALKING(566, (byte) 2, (short) 0x0000, ActivityKind.WALKING),
    SUMO_WRESTLING(567, (byte) 1, (short) 0x0000, ActivityKind.WRESTLING),
    COOKING(568, (byte) 1, (short) 0x0000, ActivityKind.ACTIVITY),
    ROWING(0, (byte) 1, (short) 0x0001, ActivityKind.ROWING_MACHINE); // legacy fallback, no capture code yet

    private static final Logger LOG = LoggerFactory.getLogger(WithingsActivityType.class);
    private static final Map<String, WithingsActivityType> PREF_LOOKUP = new HashMap<>();

    static {
        for (final WithingsActivityType type : values()) {
            PREF_LOOKUP.put(normalize(type.name()), type);
        }

        PREF_LOOKUP.put(normalize("golf"), GOLFING);
        PREF_LOOKUP.put(normalize("rowing_machine"), ROWING);
    }

    private final int code;
    private final byte workoutMode;
    private final short workoutFlags;
    private final ActivityKind activityKind;

    WithingsActivityType(final int typeCode,
                         final byte workoutMode,
                         final short workoutFlags,
                         final ActivityKind activityKind) {
        this.code = typeCode;
        this.workoutMode = workoutMode;
        this.workoutFlags = workoutFlags;
        this.activityKind = activityKind;
    }

    public static WithingsActivityType fromCode(int withingsCode) {
        for (WithingsActivityType type : values()) {
            if (type.code == withingsCode) {
                return type;
            }
        }
        LOG.warn("No matching WithingsActivityType for code: {}, falling back to OTHER", withingsCode);
        return OTHER;
    }

    public int getCode() {
        return code;
    }

    public ActivityKind toActivityKind() {
        return activityKind;
    }

    /**
     * Returns the workout mode byte used in the WORKOUT_SCREEN_SETTINGS TLV.
     * <ul>
     *   <li>1 = Indoor / pool (no GPS, indoor-specific UI)</li>
     *   <li>2 = GPS tracking (outdoor with GPS)</li>
     *   <li>3 = No GPS (outdoor/generic without GPS)</li>
     * </ul>
     */
    public byte getWorkoutMode() {
        return workoutMode;
    }

    /**
     * Returns the flags short for the WORKOUT_SCREEN_SETTINGS TLV.
     * Indoor/pool types use 0x0001; all others use 0x0000.
     */
    public short getWorkoutFlags() {
        return workoutFlags;
    }

    public static WithingsActivityType fromPrefValue(final String prefValue) {
        final WithingsActivityType type = PREF_LOOKUP.get(normalize(prefValue));
        if (type != null) {
            return type;
        }
        throw new RuntimeException("No matching WithingsActivityType for pref value: " + prefValue);
    }

    private static String normalize(final String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
