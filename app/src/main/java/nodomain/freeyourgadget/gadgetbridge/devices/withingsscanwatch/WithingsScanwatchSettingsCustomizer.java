/*  Copyright (C) 2024 Gadgetbridge contributors

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

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class WithingsScanwatchSettingsCustomizer implements DeviceSpecificSettingsCustomizer {

    static final String PREF_WORKOUT_TYPES_SORTABLE = "workout_activity_types_sortable";
    static final String PREF_SCREENS_SORTABLE  = "withings_scanwatch_screens_sortable";
    static final String PREF_SHORTCUT_ACTION   = "withings_scanwatch_shortcut_action";
    static final String PREF_ECG_ENABLE        = "withings_scanwatch_ecg_enable";
    static final String PREF_ECG_ACTIVATED     = "withings_scanwatch_ecg_activated";
    static final String PREF_SPO2_ENABLE       = "withings_scanwatch_spo2_enable";
    static final String PREF_SPO2_ACTIVATED    = "withings_scanwatch_spo2_activated";
    static final String PREF_SPO2_MODE         = "withings_scanwatch_spo2_mode";
    static final String PREF_RESPIRATORY_SCAN  = "withings_scanwatch_respiratory_scan";
    static final String PREF_AFIB_DAY_ENABLED  = "withings_scanwatch_afib_day_enabled";
    static final String PREF_AFIB_NIGHT_ENABLED= "withings_scanwatch_afib_night_enabled";
    static final String PREF_QUICKLOOK         = "withings_scanwatch_quicklook";
    static final String PREF_AUTO_BRIGHTNESS    = "withings_scanwatch_auto_brightness";
    static final String PREF_BRIGHTNESS_LEVEL   = "withings_scanwatch_brightness_level";
    static final String PREF_MOVE_HANDS         = "withings_scanwatch_move_hands";
    static final String PREF_ACTIVITY_REMINDER  = "withings_scanwatch_activity_reminder";
    static final String PREF_HR_ALERT_MODE      = "withings_scanwatch_hr_alert_mode";
    static final String PREF_HR_ALERT_LOW       = "withings_scanwatch_hr_alert_low";
    static final String PREF_HR_ALERT_HIGH      = "withings_scanwatch_hr_alert_high";

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs, final String rootKey) {
        handler.addPreferenceHandlerFor(PREF_WORKOUT_TYPES_SORTABLE);
        handler.addPreferenceHandlerFor(PREF_SCREENS_SORTABLE);
        handler.addPreferenceHandlerFor(PREF_SHORTCUT_ACTION);
        handler.addPreferenceHandlerFor(PREF_ECG_ENABLE);
        handler.addPreferenceHandlerFor(PREF_SPO2_ENABLE);
        handler.addPreferenceHandlerFor(PREF_SPO2_MODE);
        handler.addPreferenceHandlerFor(PREF_RESPIRATORY_SCAN);
        handler.addPreferenceHandlerFor(PREF_AFIB_DAY_ENABLED);
        handler.addPreferenceHandlerFor(PREF_AFIB_NIGHT_ENABLED);
        handler.addPreferenceHandlerFor(PREF_QUICKLOOK);
        handler.addPreferenceHandlerFor(PREF_AUTO_BRIGHTNESS);
        handler.addPreferenceHandlerFor(PREF_MOVE_HANDS);
        handler.addPreferenceHandlerFor(PREF_ACTIVITY_REMINDER);
        handler.addPreferenceHandlerFor(PREF_HR_ALERT_MODE);
        handler.addPreferenceHandlerFor(PREF_HR_ALERT_LOW);
        handler.addPreferenceHandlerFor(PREF_HR_ALERT_HIGH);

        // "Watch face (Date)" must always be pinned at position 0.
        // Intercept changes to the screens pref and normalise the stored value so that
        // "date" is always present and always first before it is written to SharedPreferences.
        // The service layer also enforces this at send time, but correcting the stored value
        // here keeps the UI consistent with what will actually be sent to the watch.
        handler.addPreferenceHandlerFor(PREF_SCREENS_SORTABLE, (pref, newValue) -> {
            if (!(newValue instanceof String)) {
                return true; // nothing to normalise; let the default handler proceed
            }
            final String raw = (String) newValue;
            final List<String> screens = new ArrayList<>();
            if (!raw.isEmpty()) {
                screens.addAll(Arrays.asList(raw.split(",")));
            }
            // Move "date" to position 0 (or add it if it was removed)
            screens.remove("date");
            screens.add(0, "date");
            final String normalised = android.text.TextUtils.join(",", screens);
            if (!normalised.equals(raw)) {
                // Persist the corrected value ourselves and reject the original so that the
                // preference does not store the out-of-order / missing-date value.
                if (pref.getSharedPreferences() != null) {
                    pref.getSharedPreferences().edit().putString(PREF_SCREENS_SORTABLE, normalised).apply();
                }
                return false;
            }
            return true;
        });

        // Brightness level is only meaningful when auto-brightness is OFF (inverse dependency)
        final EditTextPreference brightnessPref = handler.findPreference(PREF_BRIGHTNESS_LEVEL);
        if (brightnessPref != null) {
            final boolean autoOn = prefs.getBoolean(PREF_AUTO_BRIGHTNESS, true);
            brightnessPref.setEnabled(!autoOn);
        }

        // Register brightness level handler with validation: integer only, clamped to 0-100
        handler.addPreferenceHandlerFor(PREF_BRIGHTNESS_LEVEL, (pref, newValue) -> {
            final String raw = String.valueOf(newValue).trim();
            try {
                final int val = Integer.parseInt(raw);
                final int clamped = Math.max(0, Math.min(100, val));
                if (clamped != val && pref instanceof EditTextPreference) {
                    // Persist the clamped value instead of the out-of-range one
                    ((EditTextPreference) pref).setText(String.valueOf(clamped));
                    return false; // reject original; clamped value already persisted
                }
            } catch (NumberFormatException e) {
                return false; // reject non-integer input
            }
            return true;
        });

        final ListPreference shortcutPref = handler.findPreference(PREF_SHORTCUT_ACTION);
        if (shortcutPref != null) {
            shortcutPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }

        final ListPreference spo2ModePref = handler.findPreference(PREF_SPO2_MODE);
        if (spo2ModePref != null) {
            updateSpo2ModeSummary(spo2ModePref);
        }

        updateOneWayHealthFeatureUi(handler, prefs);

        final ListPreference respiratoryPref = handler.findPreference(PREF_RESPIRATORY_SCAN);
        if (respiratoryPref != null) {
            respiratoryPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }

        // HR alert mode summary
        final ListPreference hrAlertModePref = handler.findPreference(PREF_HR_ALERT_MODE);
        if (hrAlertModePref != null) {
            hrAlertModePref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }

        // Show threshold prefs only when mode is "custom"
        final boolean isCustom = "custom".equals(prefs.getString(PREF_HR_ALERT_MODE, "off"));
        final ListPreference hrLowPref = handler.findPreference(PREF_HR_ALERT_LOW);
        if (hrLowPref != null) {
            hrLowPref.setEnabled(isCustom);
            hrLowPref.setVisible(isCustom);
            hrLowPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
        final ListPreference hrHighPref = handler.findPreference(PREF_HR_ALERT_HIGH);
        if (hrHighPref != null) {
            hrHighPref.setEnabled(isCustom);
            hrHighPref.setVisible(isCustom);
            hrHighPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }

    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {
        if (PREF_AUTO_BRIGHTNESS.equals(preference.getKey())) {
            final EditTextPreference brightnessPref = handler.findPreference(PREF_BRIGHTNESS_LEVEL);
            if (brightnessPref != null) {
                // preference value has already been persisted at this point
                final boolean autoOn = preference.getSharedPreferences().getBoolean(PREF_AUTO_BRIGHTNESS, true);
                brightnessPref.setEnabled(!autoOn);
            }
        }

        if (PREF_HR_ALERT_MODE.equals(preference.getKey())) {
            // The new value has already been persisted; read it back to update visibility
            final String newMode = preference.getSharedPreferences().getString(PREF_HR_ALERT_MODE, "off");
            final boolean isCustom = "custom".equals(newMode);
            final ListPreference hrLowPref = handler.findPreference(PREF_HR_ALERT_LOW);
            if (hrLowPref != null) {
                hrLowPref.setEnabled(isCustom);
                hrLowPref.setVisible(isCustom);
            }
            final ListPreference hrHighPref = handler.findPreference(PREF_HR_ALERT_HIGH);
            if (hrHighPref != null) {
                hrHighPref.setEnabled(isCustom);
                hrHighPref.setVisible(isCustom);
            }
        }

        if (PREF_ECG_ENABLE.equals(preference.getKey()) || PREF_SPO2_ENABLE.equals(preference.getKey())) {
            final Prefs prefs = new Prefs(preference.getSharedPreferences());
            updateOneWayHealthFeatureUi(handler, prefs);
        }

        if (PREF_SPO2_MODE.equals(preference.getKey()) && preference instanceof ListPreference spo2ModePref) {
            updateSpo2ModeSummary(spo2ModePref);
        }
    }

    private static void updateSpo2ModeSummary(final ListPreference preference) {
        final CharSequence entry = preference.getEntry();
        if (entry == null) {
            preference.setSummary(R.string.withings_scanwatch_pref_spo2_mode_sleep_summary);
            return;
        }

        preference.setSummary(entry + "\n" + preference.getContext().getString(R.string.withings_scanwatch_pref_spo2_mode_sleep_summary));
    }

    private void updateOneWayHealthFeatureUi(final DeviceSpecificSettingsHandler handler, final Prefs prefs) {
        final boolean ecgActivated = prefs.getBoolean(PREF_ECG_ACTIVATED, false);
        final boolean ecgEnabled = prefs.getBoolean(PREF_ECG_ENABLE, false);
        final Preference ecgPref = handler.findPreference(PREF_ECG_ENABLE);
        if (ecgPref != null) {
            ecgPref.setEnabled(!(ecgActivated || ecgEnabled));
            if (ecgActivated || ecgEnabled) {
                ecgPref.setSummary(R.string.withings_scanwatch_pref_enable_once_locked_summary);
                if (ecgPref instanceof SwitchPreferenceCompat) {
                    ((SwitchPreferenceCompat) ecgPref).setChecked(true);
                }
            }
        }

        final boolean spo2Activated = prefs.getBoolean(PREF_SPO2_ACTIVATED, false);
        final boolean spo2Enabled = prefs.getBoolean(PREF_SPO2_ENABLE, false);
        final Preference spo2EnablePref = handler.findPreference(PREF_SPO2_ENABLE);
        if (spo2EnablePref != null) {
            spo2EnablePref.setEnabled(!(spo2Activated || spo2Enabled));
            if (spo2Activated || spo2Enabled) {
                spo2EnablePref.setSummary(R.string.withings_scanwatch_pref_enable_once_locked_summary);
                if (spo2EnablePref instanceof SwitchPreferenceCompat) {
                    ((SwitchPreferenceCompat) spo2EnablePref).setChecked(true);
                }
            }
        }

        final ListPreference spo2ModePref = handler.findPreference(PREF_SPO2_MODE);
        if (spo2ModePref != null) {
            spo2ModePref.setEnabled(spo2Activated || spo2Enabled);
            spo2ModePref.setVisible(spo2Activated || spo2Enabled);
        }
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    public static final Creator<WithingsScanwatchSettingsCustomizer> CREATOR = new Creator<WithingsScanwatchSettingsCustomizer>() {
        @Override
        public WithingsScanwatchSettingsCustomizer createFromParcel(final Parcel in) {
            return new WithingsScanwatchSettingsCustomizer();
        }

        @Override
        public WithingsScanwatchSettingsCustomizer[] newArray(final int size) {
            return new WithingsScanwatchSettingsCustomizer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
    }
}
