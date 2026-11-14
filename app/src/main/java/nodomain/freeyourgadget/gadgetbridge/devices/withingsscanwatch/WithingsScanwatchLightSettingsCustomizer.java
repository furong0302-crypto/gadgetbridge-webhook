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
package nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch;

import android.os.Parcel;

import androidx.preference.Preference;
import androidx.preference.ListPreference;

import com.mobeta.android.dslv.DragSortListPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ShortcutAction;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class WithingsScanwatchLightSettingsCustomizer extends WithingsScanwatchSettingsCustomizer {
    private static final String[] UNSUPPORTED_HEALTH_PREFERENCES = {
            PREF_ECG_ENABLE,
            PREF_SPO2_ENABLE,
            PREF_SPO2_MODE,
            PREF_RESPIRATORY_SCAN,
            PREF_AFIB_DAY_ENABLED,
            PREF_AFIB_NIGHT_ENABLED
    };

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs, final String rootKey) {
        super.customizeSettings(handler, prefs, rootKey);
        for (final String key : UNSUPPORTED_HEALTH_PREFERENCES) {
            final Preference preference = handler.findPreference(key);
            if (preference != null) {
                preference.setVisible(false);
            }
        }

        final DragSortListPreference screensPref = handler.findPreference(PREF_SCREENS_SORTABLE);
        if (screensPref != null) {
            removeUnsupportedChoices(screensPref, "ecg", "spo2");
            final String normalised = normaliseLightScreens(screensPref.getValue());
            if (!normalised.equals(screensPref.getValue())) {
                screensPref.setValue(normalised);
            }
        }
        final ListPreference shortcutPref = handler.findPreference(PREF_SHORTCUT_ACTION);
        if (shortcutPref != null) {
            removeUnsupportedChoices(shortcutPref,
                    Byte.toString(ShortcutAction.ACTION_ECG_MEAS),
                    Byte.toString(ShortcutAction.ACTION_SPO2_MEAS));
            final String normalised = normaliseLightShortcut(shortcutPref.getValue());
            if (normalised != null && !normalised.equals(shortcutPref.getValue())) {
                shortcutPref.setValue(normalised);
            }
        }
    }

    static String normaliseLightScreens(final String raw) {
        final List<String> screens = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            screens.addAll(Arrays.asList(raw.split(",")));
        }
        screens.removeIf(screen -> "ecg".equals(screen) || "spo2".equals(screen) || "date".equals(screen));
        screens.add(0, "date");
        return String.join(",", screens);
    }

    private static boolean isUnsupportedShortcut(final String value) {
        return Byte.toString(ShortcutAction.ACTION_ECG_MEAS).equals(value)
                || Byte.toString(ShortcutAction.ACTION_SPO2_MEAS).equals(value);
    }

    static String normaliseLightShortcut(final String value) {
        return isUnsupportedShortcut(value) ? Byte.toString(ShortcutAction.ACTION_NONE) : value;
    }

    private static void removeUnsupportedChoices(final ListPreference preference, final String... unsupportedValues) {
        final List<CharSequence> entries = new ArrayList<>();
        final List<CharSequence> values = new ArrayList<>();
        for (int i = 0; i < preference.getEntryValues().length; i++) {
            final CharSequence value = preference.getEntryValues()[i];
            if (!Arrays.asList(unsupportedValues).contains(value.toString())) {
                entries.add(preference.getEntries()[i]);
                values.add(value);
            }
        }
        preference.setEntries(entries.toArray(new CharSequence[0]));
        preference.setEntryValues(values.toArray(new CharSequence[0]));
    }

    public static final Creator<WithingsScanwatchLightSettingsCustomizer> CREATOR = new Creator<WithingsScanwatchLightSettingsCustomizer>() {
        @Override
        public WithingsScanwatchLightSettingsCustomizer createFromParcel(final Parcel in) {
            return new WithingsScanwatchLightSettingsCustomizer();
        }

        @Override
        public WithingsScanwatchLightSettingsCustomizer[] newArray(final int size) {
            return new WithingsScanwatchLightSettingsCustomizer[size];
        }
    };
}
