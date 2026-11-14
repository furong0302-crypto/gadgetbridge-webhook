/*  Copyright (C) 2015-2024 0nse, Andreas Shimokawa, Anemograph, Arjan
    Schrijver, Carsten Pfeiffer, Daniel Dakhno, Daniele Gobbetti, Felix Konstantin
    Maurer, José Rebelo, Martin, Normano64, Pavel Elagin, Petr Vaněk, Sebastian
    Kranz, Taavi Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;
import com.jaredrummler.android.colorpicker.MaterialColorPickerDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dagger.hilt.android.AndroidEntryPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.Logging;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.automations.AutomationsSettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsPreferencesActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryPairingPreferenceActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.elevation.ElevationSettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.externalservices.OnlineFitnessTrackersPreferencesActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.maps.MapsSettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.preferences.HealthConnectPreferencesActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.quicksettings.QuickSettingsPreferencesActivity;
import nodomain.freeyourgadget.gadgetbridge.externalevents.TimeChangeReceiver;
import nodomain.freeyourgadget.gadgetbridge.externalevents.comaps.CoMapsNavigationReceiverFactory;
import nodomain.freeyourgadget.gadgetbridge.externalevents.opentracks.OpenTracksController;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.SubtitleListPreference;
import nodomain.freeyourgadget.gadgetbridge.util.ui.AccentColors;
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AccentColor;
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AppTheme;
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeRepository;
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettingsDataSource;

@AndroidEntryPoint
public class SettingsActivity extends AbstractSettingsActivityV2 implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String PREF_LANGUAGE = "language";
    public static final String PREF_UNIT_WEIGHT = "unit_weight";
    public static final String PREF_UNIT_TEMPERATURE = "unit_temperature";
    public static final String PREF_UNIT_DISTANCE = "unit_distance";

    public static final int COMAPS_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected PreferenceFragmentCompat newFragment() {
        return new SettingsFragment();
    }

    @Override
    public void onSearchResultClicked(final SearchPreferenceResult result) {
        if (result.getResourceFile() == R.xml.dashboard_preferences) {
            open(DashboardPreferencesActivity.class, result);
        } else if (result.getResourceFile() == R.xml.about_user) {
            open(AboutUserPreferencesActivity.class, result);
        } else if (result.getResourceFile() == R.xml.charts_preferences) {
            open(ChartsPreferencesActivity.class, result);
        } else if (result.getResourceFile() == R.xml.sleepasandroid_preferences) {
            open(SleepAsAndroidPreferencesActivity.class, result);
        } else if (result.getResourceFile() == R.xml.discovery_pairing_preferences) {
            open(DiscoveryPairingPreferenceActivity.class, result);
        } else if (result.getResourceFile() == R.xml.notifications_preferences) {
            open(NotificationManagementActivity.class, result);
        } else if (result.getResourceFile() == R.xml.map_settings) {
            open(MapsSettingsActivity.class, result);
        } else if (result.getResourceFile() == R.xml.elevation_settings) {
            open(ElevationSettingsActivity.class, result);
        } else if (result.getResourceFile() == R.xml.automations_settings) {
            open(AutomationsSettingsActivity.class, result);
        } else if (result.getResourceFile() == R.xml.internethelper_preferences) {
            open(InternetHelperPreferencesActivity.class, result);
        } else {
            super.onSearchResultClicked(result);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != COMAPS_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (Arrays.stream(grantResults).anyMatch(it -> it == PackageManager.PERMISSION_GRANTED)) {
            GBApplication.getPrefs().getPreferences()
                    .edit()
                    .putBoolean(GBPrefs.NAVIGATION_APP_COMAPS, true)
                    .apply();
        }
    }

    @AndroidEntryPoint
    public static class SettingsFragment extends AbstractPreferenceFragment {
        private static final Logger LOG = LoggerFactory.getLogger(SettingsActivity.class);

        @Inject
        ThemeRepository themeRepository;

        /** Whether this screen is themed dark, under this activity's own configuration. */
        private boolean isDark() {
            return themeRepository.getThemeSettings().getValue().isDark(getResources().getConfiguration());
        }

        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            index(R.xml.preferences);
            index(R.xml.dashboard_preferences, R.string.bottom_nav_dashboard);
            index(R.xml.about_user, R.string.activity_prefs_about_you);
            index(R.xml.charts_preferences, R.string.activity_prefs_charts);
            index(R.xml.sleepasandroid_preferences, R.string.sleepasandroid_settings);
            index(R.xml.discovery_pairing_preferences, R.string.activity_prefs_discovery_pairing);
            index(R.xml.notifications_preferences, R.string.pref_header_notifications);
            index(R.xml.map_settings, R.string.maps_settings);
            index(R.xml.elevation_settings, R.string.elevation_correction);
            index(R.xml.automations_settings, R.string.pref_header_automations);
            if (!GBApplication.hasDirectInternetAccess())
                index(R.xml.internethelper_preferences, R.string.prefs_internet_helper_title);

            setInputTypeFor("rtl_max_line_length", InputType.TYPE_CLASS_NUMBER);
            setInputTypeFor("location_latitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            setInputTypeFor("location_longitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED  | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            Preference pref = findPreference("pref_category_activity_personal");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), AboutUserPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_charts");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), ChartsPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("datetime_synconconnect");
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newVal) -> {
                    if (Boolean.TRUE.equals(newVal)) {
                        TimeChangeReceiver.scheduleNextDstChangeOrPeriodicSync(requireContext());
                        GBApplication.deviceService().onSetTime();
                    }
                    return true;
                });
            }

            final SwitchPreferenceCompat logToFilePreference = findPreference("log_to_file");
            if (logToFilePreference != null) {
                logToFilePreference.setOnPreferenceChangeListener((preference, newVal) -> {
                    boolean doEnable = Boolean.TRUE.equals(newVal);
                    try {
                        if (doEnable) {
                            FileUtils.getExternalFilesDir(); // ensures that it is created
                        }
                        Logging.getInstance().setFileLoggingEnabled(doEnable);
                    } catch (IOException ex) {
                        GB.toast(requireContext().getApplicationContext(),
                                getString(R.string.error_creating_directory_for_logfiles, ex.getLocalizedMessage()),
                                Toast.LENGTH_LONG,
                                GB.ERROR,
                                ex);
                    }
                    return true;
                });

                // If we didn't manage to initialize file logging, disable the preference and show the button to initialize again
                if (!Logging.getInstance().isFileLoggerInitialized()) {
                    logToFilePreference.setEnabled(false);
                    logToFilePreference.setSummary(R.string.pref_write_logfiles_not_available);
                    final Preference logRestart = findPreference("log_restart");
                    if (logRestart != null) {
                        logRestart.setVisible(true);
                        logRestart.setOnPreferenceClickListener(preference -> {
                            Logging.getInstance().setFileLoggingEnabled(logToFilePreference.isChecked());
                            if (Logging.getInstance().isFileLoggerInitialized()) {
                                logToFilePreference.setEnabled(true);
                                logToFilePreference.setSummary(null);
                                logRestart.setVisible(false);

                            }
                            return true;
                        });
                    }
                }

                final SwitchPreferenceCompat logLevelTrace = findPreference("log_level_trace");
                logLevelTrace.setOnPreferenceChangeListener((preference, newVal) -> {
                    final boolean traceEnabled = Boolean.TRUE.equals(newVal);
                    Logging.getInstance().setTraceLogging(traceEnabled);
                    return true;
                });
            }

            pref = findPreference(PREF_LANGUAGE);
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newVal) -> {
                    String newLang = newVal.toString();
                    try {
                        GBApplication.setLanguage(newLang);
                        requireActivity().recreate();
                        invokeLater(() -> GBApplication.deviceService().onSendConfiguration(PREF_LANGUAGE));
                    } catch (Exception ex) {
                        GB.toast(requireContext().getApplicationContext(),
                                "Error setting language: " + ex.getLocalizedMessage(),
                                Toast.LENGTH_LONG,
                                GB.ERROR,
                                ex);
                    }
                    return true;
                });
            }

            final Preference unitDistance = findPreference(PREF_UNIT_DISTANCE);
            if (unitDistance != null) {
                unitDistance.setOnPreferenceChangeListener((preference, newVal) -> {
                    invokeLater(() -> GBApplication.deviceService().onSendConfiguration(PREF_UNIT_DISTANCE));
                    return true;
                });
            }
            final Preference unitTemperature = findPreference(PREF_UNIT_TEMPERATURE);
            if (unitTemperature != null) {
                unitTemperature.setOnPreferenceChangeListener((preference, newVal) -> {
                    invokeLater(() -> GBApplication.deviceService().onSendConfiguration(PREF_UNIT_TEMPERATURE));
                    return true;
                });
            }
            final Preference unitWeight = findPreference(PREF_UNIT_WEIGHT);
            if (unitWeight != null) {
                unitWeight.setOnPreferenceChangeListener((preference, newVal) -> {
                    invokeLater(() -> GBApplication.deviceService().onSendConfiguration(PREF_UNIT_WEIGHT));
                    return true;
                });
            }

            pref = findPreference("location_aquire");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    if (ActivityCompat.checkSelfPermission(requireContext().getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                    }

                    LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
                    Criteria criteria = new Criteria();
                    String provider = locationManager.getBestProvider(criteria, false);
                    if (provider != null) {
                        Location location = locationManager.getLastKnownLocation(provider);
                        if (location != null) {
                            setLocationPreferences(location);
                        } else {
                            locationManager.requestSingleUpdate(provider, new LocationListener() {
                                @Override
                                public void onLocationChanged(Location location) {
                                    setLocationPreferences(location);
                                }

                                @Override
                                public void onStatusChanged(String provider, int status, Bundle extras) {
                                    LOG.info("provider status changed to " + status + " (" + provider + ")");
                                }

                                @Override
                                public void onProviderEnabled(String provider) {
                                    LOG.info("provider enabled (" + provider + ")");
                                }

                                @Override
                                public void onProviderDisabled(String provider) {
                                    LOG.info("provider disabled (" + provider + ")");
                                    GB.toast(requireContext(), getString(R.string.toast_enable_networklocationprovider), 3000, 0);
                                }
                            }, null);
                        }
                    } else {
                        LOG.warn("No location provider found, did you deny location permission?");
                    }
                    return true;
                });
            }

            pref = findPreference("use_updated_location_if_available");
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newVal) -> {
                    if (Boolean.TRUE.equals(newVal) &&
                            ActivityCompat.checkSelfPermission(requireContext().getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.warning)
                                .setMessage(R.string.location_permission_required)
                                .setIcon(R.drawable.ic_warning)
                                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                                    // highlight the permissions entry on supported devices
                                    final Bundle fragmentArgs = new Bundle();
                                    fragmentArgs.putString(":settings:fragment_args_key", "permission_settings");
                                    intent.putExtra(":settings:show_fragment_args", fragmentArgs);
                                    startActivity(intent);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                    return true;
                });
            }

            pref = findPreference("weather_city");
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newVal) -> {
                    // reset city id and force a new lookup
                    GBApplication.getPrefs().getPreferences().edit().putString("weather_cityid", null).apply();
                    Intent intent = new Intent("GB_UPDATE_WEATHER");
                    intent.setPackage(BuildConfig.APPLICATION_ID);
                    requireContext().sendBroadcast(intent);
                    return true;
                });
            }

            final ListPreference audioPlayer = findPreference("audio_player");
            if (audioPlayer != null) {
                // Get all receivers of Media Buttons
                Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

                PackageManager pm = requireContext().getPackageManager();
                List<ResolveInfo> mediaReceivers = pm.queryBroadcastReceivers(mediaButtonIntent,
                        PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER);

                CharSequence[] newEntries = new CharSequence[mediaReceivers.size() + 1];
                CharSequence[] newValues = new CharSequence[mediaReceivers.size() + 1];
                newEntries[0] = getString(R.string.pref_default);
                newValues[0] = "default";

                int i = 1;
                Set<String> existingNames = new HashSet<>();
                for (ResolveInfo resolveInfo : mediaReceivers) {
                    newEntries[i] = resolveInfo.activityInfo.loadLabel(pm) + " (" + resolveInfo.activityInfo.packageName + ")";
                    if (existingNames.contains(newEntries[i].toString().trim())) {
                        newEntries[i] = resolveInfo.activityInfo.loadLabel(pm) + " (" + resolveInfo.activityInfo.name + ")";
                    } else {
                        existingNames.add(newEntries[i].toString().trim());
                    }
                    newValues[i] = resolveInfo.activityInfo.packageName;
                    i++;
                }

                audioPlayer.setEntries(newEntries);
                audioPlayer.setEntryValues(newValues);
                audioPlayer.setDefaultValue(newValues[0]);
            }

            pref = findPreference("pref_category_dashboard");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), DashboardPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_category_maps");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), MapsSettingsActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_category_elevation_correction");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(requireContext(), ElevationSettingsActivity.class));
                    return true;
                });
            }

            pref = findPreference("pref_screen_automations");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), AutomationsSettingsActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_screen_quick_settings");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), QuickSettingsPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_category_sleepasandroid");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), SleepAsAndroidPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_category_internethelper");
            if (pref != null) {
                if (GBApplication.hasDirectInternetAccess()) {
                    pref.setVisible(false);
                } else {
                    pref.setOnPreferenceClickListener(preference -> {
                        Intent enableIntent = new Intent(requireContext(), InternetHelperPreferencesActivity.class);
                        startActivity(enableIntent);
                        return true;
                    });
                }
            }

            pref = findPreference("pref_category_healthconnect");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), HealthConnectPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_category_online_fitness_trackers");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), OnlineFitnessTrackersPreferencesActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            pref = findPreference("pref_category_notifications");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), NotificationManagementActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            final Preference theme = findPreference(ThemeSettingsDataSource.PREF_THEME);
            final Preference amoled_black = findPreference(ThemeSettingsDataSource.PREF_AMOLED_BLACK);
            final Preference accentColor = findPreference(ThemeSettingsDataSource.PREF_ACCENT_COLOR);
            final SwitchPreferenceCompat contrastingSurfaces = findPreference(ThemeSettingsDataSource.PREF_CONTRASTING_SURFACES);

            if (amoled_black != null) {
                if (themeRepository.getSelectedTheme() == AppTheme.LIGHT)
                    amoled_black.setEnabled(false);
                else
                    amoled_black.setEnabled(true);
            }

            if (accentColor != null) {
                accentColor.setEnabled(!themeRepository.areDynamicColorsEnabled());
                updateAccentColorSummary(accentColor);
                accentColor.setOnPreferenceClickListener(preference -> {
                    showAccentColorPicker(accentColor);
                    return true;
                });
            }

            if (contrastingSurfaces != null) {
                contrastingSurfaces.setChecked(themeRepository.areContrastingSurfacesEnabled(isDark()));
                contrastingSurfaces.setOnPreferenceChangeListener((preference, newVal) -> {
                    themeRepository.setContrastingSurfacesEnabled((Boolean) newVal);
                    return true;
                });
            }

            if (theme != null) {
                theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newVal) {
                        final AppTheme val = AppTheme.fromValue(newVal.toString());
                        // Warn user if dynamic colors are not available, and only apply the theme once they confirm.
                        // Storing it now would recreate the activity and dismiss the warning.
                        if (val == AppTheme.DYNAMIC && !DynamicColors.isDynamicColorAvailable()) {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.warning)
                                    .setMessage(R.string.pref_theme_dynamic_colors_not_available_warning)
                                    .setIcon(R.drawable.ic_warning)
                                    .setPositiveButton(R.string.ok, (dialog, whichButton) ->
                                            themeRepository.setSelectedTheme(AppTheme.DYNAMIC))
                                    .show();
                            return false;
                        }
                        if (amoled_black != null) {
                            if (val == AppTheme.LIGHT)
                                amoled_black.setEnabled(false);
                            else
                                amoled_black.setEnabled(true);
                        }
                        if (accentColor != null) {
                            accentColor.setEnabled(val != AppTheme.DYNAMIC);
                            if (val == AppTheme.LIGHT && !themeRepository.getAccentColor().isAvailable(false)) {
                                themeRepository.setAccentColor(AccentColor.DEFAULT);
                            }
                            updateAccentColorSummary(accentColor);
                        }
                        return true;
                    }
                });
            }

            pref = findPreference("pref_discovery_pairing");
            if (pref != null) {
                pref.setOnPreferenceClickListener(preference -> {
                    Intent enableIntent = new Intent(requireContext(), DiscoveryPairingPreferenceActivity.class);
                    startActivity(enableIntent);
                    return true;
                });
            }

            //fitness app (OpenTracks) package name selection for OpenTracks observer
            final SubtitleListPreference opentracksPref = findPreference("opentracks_packagename");
            if (opentracksPref != null) {
                final List<String> installedPackages = OpenTracksController.findInstalledPackages();
                if (installedPackages.isEmpty()) {
                    opentracksPref.setUnavailable(getString(R.string.pref_summary_opentracks_packagename_not_installed));
                } else {
                    opentracksPref.setUnavailable(null);
                    final CharSequence[] entries = new CharSequence[installedPackages.size()];
                    final CharSequence[] entryValues = new CharSequence[installedPackages.size()];
                    for (int i = 0; i < installedPackages.size(); i++) {
                        final String packageName = installedPackages.get(i);
                        final String label = NotificationUtils.getApplicationLabel(requireContext(), packageName);
                        entries[i] = label != null ? label : packageName;
                        entryValues[i] = packageName;
                    }
                    opentracksPref.setEntries(entries);
                    opentracksPref.setEntryValues(entryValues);
                    opentracksPref.setEntrySubtitles(entryValues);
                }
            }

            pref = findPreference(GBPrefs.NAVIGATION_APP_COMAPS);
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newValue) ->  {
                    if (!(boolean) newValue) {
                        return true;
                    }

                    Activity activity = requireActivity();
                    List<String> allPermissions = CoMapsNavigationReceiverFactory.discoverInstalledVersions(activity.getPackageManager());
                    List<String> neededPermissions = allPermissions.stream()
                            .map(app -> app + CoMapsNavigationReceiverFactory.PERMISSION_SUFFIX)
                            .filter(it -> activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED)
                            .toList();

                    if (neededPermissions.isEmpty()) {
                        return true;
                    }

                    ActivityCompat.requestPermissions(activity, neededPermissions.toArray(String[]::new), COMAPS_PERMISSION_REQUEST_CODE);

                    if (neededPermissions.stream().anyMatch(activity::shouldShowRequestPermissionRationale)) {
                        new MaterialAlertDialogBuilder(activity)
                                .setMessage(activity.getString(R.string.permission_navigation_comaps, activity.getString(R.string.app_name), activity.getString(android.R.string.ok)))
                                .setPositiveButton(android.R.string.ok, (d, w) -> {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                                    activity.startActivity(intent);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }

                    // In the niche case where the user happens to have several versions of CoMaps
                    // installed, and only grants permission to one, we still enable the option
                    return neededPermissions.size() < allPermissions.size();
                });
            }

            pref = findPreference(GBPrefs.DEPRECATED_SMS_RECEIVER);
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newValue) -> {
                    if ((boolean) newValue && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECEIVE_SMS}, 0);
                        return false;
                    }
                    return true;
                });
            }
        }

        /*
         * delayed execution so that the preferences are applied first
         */
        private void invokeLater(Runnable runnable) {
            getListView().post(runnable);
        }

        private void setLocationPreferences(Location location) {
            String latitude = String.format(Locale.US, "%.6g", location.getLatitude());
            String longitude = String.format(Locale.US, "%.6g", location.getLongitude());
            LOG.info("got location. Lat: {} Lng: {}", latitude, longitude);
            GB.toast(requireContext(), getString(R.string.toast_aqurired_networklocation), 2000, 0);
            GBApplication.getPrefs().getPreferences()
                    .edit()
                    .putString("location_latitude", latitude)
                    .putString("location_longitude", longitude)
                    .apply();
        }

        private void showAccentColorPicker(final Preference accentColor) {
            final boolean dark = isDark();
            final List<AccentColor> available = new ArrayList<>();
            for (final AccentColor color : AccentColor.getEntries()) {
                if (color.isAvailable(dark)) {
                    available.add(color);
                }
            }
            final int[] swatches = new int[available.size()];
            for (int i = 0; i < swatches.length; i++) {
                swatches[i] = AccentColors.swatch(available.get(i), dark);
            }
            final int currentIndex = Math.max(0, available.indexOf(themeRepository.getAccentColor()));
            final ColorPickerDialog.Builder builder = ColorPickerDialog.newBuilder();
            builder.setDialogTitle(R.string.pref_title_accent_color);
            builder.setColor(swatches[currentIndex]);
            builder.setShowAlphaSlider(false);
            builder.setShowColorShades(false);
            builder.setAllowCustom(false);
            builder.setAllowPresets(true);
            builder.setPresets(swatches);

            final ColorPickerDialog dialog = MaterialColorPickerDialog.create(builder);
            dialog.setColorPickerDialogListener(new ColorPickerDialogListener() {
                @Override
                public void onColorSelected(int dialogId, int color) {
                    int index = -1;
                    for (int i = 0; i < swatches.length; i++) {
                        if (swatches[i] == color) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        return;
                    }
                    themeRepository.setAccentColor(available.get(index));
                    updateAccentColorSummary(accentColor);
                }

                @Override
                public void onDialogDismissed(int dialogId) {
                    // Nothing to do
                }
            });
            dialog.show(getParentFragmentManager(), "accent-color-picker-dialog");
        }

        private void updateAccentColorSummary(final Preference accentColor) {
            if (!accentColor.isEnabled()) {
                accentColor.setSummary(R.string.pref_accent_color_dynamic_disabled_summary);
                return;
            }
            accentColor.setSummary(AccentColors.nameRes(themeRepository.getAccentColor()));
        }
    }
}
