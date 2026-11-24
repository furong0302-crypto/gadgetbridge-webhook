/*  Copyright (C) 2024-2026 José Rebelo, Johannes Krude, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiDataTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment;
import nodomain.freeyourgadget.gadgetbridge.adapter.SimpleIconListAdapter;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.RunnableListIconItem;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiRealtimeSettings;
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;
import nodomain.freeyourgadget.gadgetbridge.util.XDatePreference;
import nodomain.freeyourgadget.gadgetbridge.util.XTimePreference;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.MinMaxFloatWatcher;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.MinMaxTextWatcher;

public class GarminRealtimeSettingsFragment extends AbstractPreferenceFragment {
    private static final Logger LOG = LoggerFactory.getLogger(GarminRealtimeSettingsFragment.class);

    public static final String EXTRA_SCREEN_ID = "screenId";
    public static final String PREF_DEBUG = "garmin_rt_debug_mode";

    public static final int ROOT_SCREEN_ID = GdiRealtimeSettings.WellKnownCompositionId.DEVICE_SETTINGS_HOME_VALUE;

    private GBDevice device;
    private int screenId = ROOT_SCREEN_ID;

    private GdiRealtimeSettings.Composition screenDefinition;
    private GdiRealtimeSettings.Settings screenState;

    public static final String EXTRA_PROTOBUF = "protobuf";

    public static final String ACTION_SCREEN_DEFINITION = "nodomain.freeyourgadget.gadgetbridge.garmin.realtime_settings.screen_definition";
    public static final String ACTION_SCREEN_STATE = "nodomain.freeyourgadget.gadgetbridge.garmin.realtime_settings.screen_state";
    public static final String ACTION_CHANGE = "nodomain.freeyourgadget.gadgetbridge.garmin.realtime_settings.change";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                LOG.error("Got null action");
                return;
            }

            switch (action) {
                case ACTION_SCREEN_DEFINITION:
                    final GdiRealtimeSettings.Composition incomingScreen;
                    try {
                        incomingScreen = GdiRealtimeSettings.Composition.parseFrom(intent.getByteArrayExtra(EXTRA_PROTOBUF));
                    } catch (final InvalidProtocolBufferException e) {
                        // should never happen
                        LOG.error("Failed to parse protobuf for screen definition on {}", screenId, e);
                        return;
                    }
                    if (incomingScreen.getCompositionId() != screenId) {
                        return;
                    }
                    LOG.debug("Got screen definition for screenId={}", screenId);
                    screenDefinition = incomingScreen;
                    break;
                case ACTION_SCREEN_STATE:
                    final GdiRealtimeSettings.Settings incomingState;
                    try {
                        incomingState = GdiRealtimeSettings.Settings.parseFrom(intent.getByteArrayExtra(EXTRA_PROTOBUF));
                    } catch (final InvalidProtocolBufferException e) {
                        // should never happen
                        LOG.error("Failed to parse protobuf for screen state on {}", screenId, e);
                        return;
                    }
                    if (incomingState.getCompositionId() != screenId) {
                        return;
                    }
                    LOG.debug("Got screen state for screenId={}", screenId);
                    screenState = incomingState;
                    break;
                case ACTION_CHANGE:
                    final GdiRealtimeSettings.SettingsChangeResponse incomingChange;
                    try {
                        incomingChange = GdiRealtimeSettings.SettingsChangeResponse.parseFrom(intent.getByteArrayExtra(EXTRA_PROTOBUF));
                    } catch (final InvalidProtocolBufferException e) {
                        // should never happen
                        LOG.error("Failed to parse protobuf for change", e);
                        return;
                    }
                    if (incomingChange.getUpdatedSettings().getCompositionId() != screenId) {
                        return;
                    }

                    if (incomingChange.getIsExitInstance()) {
                        LOG.debug("Returning from {}", screenId);
                        requireActivity().finish();
                        return;
                    }

                    LOG.debug("Got screen change for screenId={}", screenId);

                    GBApplication.deviceService(device).onReadConfiguration("screenId:" + screenId);
                    return;
                default:
                    LOG.error("Unknown action {}", action);
                    return;
            }

            reload();
        }
    };

    private void setDevice(final GBDevice device) {
        final Bundle args = getArguments() != null ? getArguments() : new Bundle();
        args.putParcelable("device", device);
        setArguments(args);
    }

    private void setScreenId(final int screenId) {
        final Bundle args = getArguments() != null ? getArguments() : new Bundle();
        args.putInt("screenId", screenId);
        setArguments(args);
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final Bundle arguments = getArguments();
        if (arguments == null) {
            return;
        }
        this.device = arguments.getParcelable(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            return;
        }
        this.screenId = arguments.getInt(EXTRA_SCREEN_ID, ROOT_SCREEN_ID);
        if (screenId == 0) {
            return;
        }

        LOG.info("Opened realtime preferences screen for {}", screenId);

        getPreferenceManager().setSharedPreferencesName("garmin_rt_" + device.getAddress());
        setPreferencesFromResource(R.xml.garmin_realtime_settings, rootKey);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SCREEN_DEFINITION);
        filter.addAction(ACTION_SCREEN_STATE);
        filter.addAction(ACTION_CHANGE);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver, filter);

        GBApplication.deviceService(device).onReadConfiguration("screenId:" + screenId);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver);
        super.onDestroyView();
    }

    static GarminRealtimeSettingsFragment newInstance(final GBDevice device, final int screenId) {
        final GarminRealtimeSettingsFragment fragment = new GarminRealtimeSettingsFragment();
        fragment.setDevice(device);
        fragment.setScreenId(screenId);

        return fragment;
    }

    void refreshFromDevice() {
        screenDefinition = null;
        screenState = null;
        reload();
        GBApplication.deviceService(device).onReadConfiguration("screenId:" + screenId);
    }

    void reload() {
        final boolean debug = GBApplication.getDevicePrefs(device).getBoolean(PREF_DEBUG, BuildConfig.DEBUG);

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            LOG.error("Activity is null");
            return;
        }

        activity.invalidateOptionsMenu();

        final PreferenceScreen prefScreen = findPreference(GarminPreferences.PREF_GARMIN_REALTIME_SETTINGS);
        if (prefScreen == null) {
            LOG.error("Preference screen for {} is null", GarminPreferences.PREF_GARMIN_REALTIME_SETTINGS);
            activity.finish();
            return;
        }

        if (screenDefinition == null || screenState == null) {
            ((GarminRealtimeSettingsActivity) activity).setActionBarTitle(activity.getString(R.string.loading));

            // Disable all existing preferences while loading
            for (int i = 0; i < prefScreen.getPreferenceCount(); i++) {
                prefScreen.getPreference(i).setEnabled(false);
            }

            return;
        }

        prefScreen.removeAll();

        if (debug) {
            final Preference pref = new PreferenceCategory(activity);
            pref.setIconSpaceReserved(false);
            pref.setTitle("Screen ID: " + screenId);
            pref.setPersistent(false);
            pref.setKey("rt_pref_header_" + screenId);
            prefScreen.addPreference(pref);
        }

        // Update the screen title, if any
        if (screenDefinition.hasTitle()) {
            final String title = screenDefinition.getTitle().getText();

            ((GarminRealtimeSettingsActivity) activity).setActionBarTitle(title);
        }

        final Map<Integer, GdiRealtimeSettings.RowValue> stateById = new HashMap<>();
        for (final GdiRealtimeSettings.RowValue state : screenState.getRowsList()) {
            stateById.put(state.getRowId(), state);
        }

        for (final GdiRealtimeSettings.CompositionRow entry : screenDefinition.getRowsList()) {
            final GdiRealtimeSettings.RowValue state = stateById.get(entry.getRowId());

            final Preference pref;
            boolean supported = true;

            if (entry.hasLinkOptions()) {
                switch (entry.getLinkOptions().getLinkType()) {
                    case INTERNAL: // subscreen
                    case INTERNAL_REINSTANTIATE: // subscreen with options for a specific preference
                        pref = new Preference(activity);
                        pref.setOnPreferenceClickListener(preference -> {
                            final Intent newIntent = new Intent(requireContext(), GarminRealtimeSettingsActivity.class);
                            newIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
                            newIntent.putExtra(GarminRealtimeSettingsActivity.EXTRA_SCREEN_ID, entry.getLinkOptions().getDestCompositionId());
                            activity.startActivityForResult(newIntent, 0);
                            return true;
                        });
                        break;
                    case SELECTION: // list preference
                        pref = new ListPreference(activity);
                        final CharSequence[] values = new String[entry.getLinkOptions().getSelectionOptions().getRowsList().size()];
                        int optionIndex = 0;
                        for (final GdiRealtimeSettings.SelectionItem option : entry.getLinkOptions().getSelectionOptions().getRowsList()) {
                            values[optionIndex] = option.getTitle().getText();
                            optionIndex++;
                        }
                        final ListPreference listPreference = (ListPreference) pref;
                        listPreference.setEntries(values); // replacing % -> %% will display "%%" and not "%"
                        listPreference.setEntryValues(values);

                        GdiRealtimeSettings.DoubleLabelSettings summary = Objects.requireNonNull(state).getDoubleLabelSettings();
                        if (summary.hasSelectionSettings()) {
                            // max+1 is used to encode that no list value has yet been set
                            GdiRealtimeSettings.SelectionSettings summaryList = summary.getSelectionSettings();
                            if (summaryList.hasSelectedIndex()) {
                                int index = summaryList.getSelectedIndex();
                                if (0 <= index && index < values.length) {
                                    CharSequence value = values[index];
                                    if (value != null) {
                                        listPreference.setValue(value.toString());
                                    }
                                }
                            }
                        }
                        listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                            int newValueIdx = -1;
                            for (int i = 0; i < values.length; i++) {
                                if (values[i].equals(newValue.toString())) {
                                    newValueIdx = i;
                                    break;
                                }
                            }
                            if (newValueIdx < 0) {
                                LOG.error("Failed to find index for {}", newValue);
                                return false;
                            }

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                                            .setSelectionChange(GdiRealtimeSettings.SelectionChange.newBuilder()
                                                    .setNewSelectedIndex(newValueIdx)
                                            )
                            );
                            return true;
                        });
                        break;

                    case TOD_PICKER: // time
                        pref = new XTimePreference(activity, null);
                        ((XTimePreference) pref).setValue(
                                Objects.requireNonNull(state).getDoubleLabelSettings().getTodPickerSettings().getSeconds() / 3600,
                                (Objects.requireNonNull(state).getDoubleLabelSettings().getTodPickerSettings().getSeconds() % 3600) / 60
                        );
                        if (state.getDoubleLabelSettings().getTodPickerSettings().hasTimeFormat()) {
                            switch (state.getDoubleLabelSettings().getTodPickerSettings().getTimeFormat()) {
                                case TWELVE_HOUR:
                                    ((XTimePreference) pref).setFormat(XTimePreference.Format.FORMAT_12H);
                                    break;
                                case TWENTY_FOUR_HOUR:
                                    ((XTimePreference) pref).setFormat(XTimePreference.Format.FORMAT_24H);
                                    break;
                            }
                        }
                        pref.setSummary(state.getDoubleLabelSettings().getDatePickerSettings().getFormattedValue().getText());
                        pref.setOnPreferenceChangeListener((preference, newValue) -> {
                            final String[] pieces = newValue.toString().split(":");

                            final int hour = Integer.parseInt(pieces[0]);
                            final int minute = Integer.parseInt(pieces[1]);

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                                            .setTodPickerChange(GdiRealtimeSettings.TODPickerChange.newBuilder()
                                                    .setNewSeconds(hour * 3600 + minute * 60)
                                            )
                            );
                            return true;
                        });
                        break;
                    case INTEGER_PICKER: {
                        final IntegerPicker integerPicker = new IntegerPicker(entry, screenId);
                        pref = integerPicker.createPreference(activity, state);
                        break;
                    }
                    case FLOAT_PICKER: {
                        final FloatPicker floatPicker = new FloatPicker(entry, screenId);
                        pref = floatPicker.createPreference(activity, state);
                        break;
                    }
                    case TIME_PICKER: {
                        final DurationPicker durationPicker = new DurationPicker(entry, screenId);
                        pref = durationPicker.createPreference(activity, state);
                        break;
                    }
                    case EXTERNAL: // activity
                        switch (entry.getLinkOptions().getDestPage()) {
                            case GARMIN_PAY_SETUP:
                            case TEXT_RESPONSE_SETUP:
                            case MUSIC_PROVIDERS:
                            case SOLAR_INTENSITY:
                            case ECG_SETUP:
                            case ECG_FEATURE:
                                pref = new Preference(activity);
                                pref.setVisible(debug);
                                pref.setEnabled(false);
                                break;
                            default:
                                LOG.info("unknown activity {}", entry.getLinkOptions().getDestPage());
                                supported = false;
                                pref = new Preference(activity);
                                break;
                        }

                        break;
                    case DEVICE_ONLY: // hidden?
                        pref = new Preference(activity);
                        pref.setVisible(debug);
                        pref.setEnabled(false);
                        break;
                    case DATE_PICKER: // date picker
                        pref = new XDatePreference(activity, null);
                        ((XDatePreference) pref).setValue(
                                Objects.requireNonNull(state).getDoubleLabelSettings().getDatePickerSettings().getSelectedDate().getYear(),
                                Objects.requireNonNull(state).getDoubleLabelSettings().getDatePickerSettings().getSelectedDate().getMonth(),
                                Objects.requireNonNull(state).getDoubleLabelSettings().getDatePickerSettings().getSelectedDate().getDay()
                        );
                        if (state.getDoubleLabelSettings().getDatePickerSettings().hasMinimumDate()) {
                            final Calendar calendar = GregorianCalendar.getInstance();
                            calendar.set(
                                    state.getDoubleLabelSettings().getDatePickerSettings().getMinimumDate().getYear(),
                                    state.getDoubleLabelSettings().getDatePickerSettings().getMinimumDate().getMonth() - 1,
                                    state.getDoubleLabelSettings().getDatePickerSettings().getMinimumDate().getDay()
                            );
                            ((XDatePreference) pref).setMinDate(calendar.getTimeInMillis());
                        }
                        if (state.getDoubleLabelSettings().getDatePickerSettings().hasMaximumDate()) {
                            final Calendar calendar = GregorianCalendar.getInstance();
                            calendar.set(
                                    state.getDoubleLabelSettings().getDatePickerSettings().getMaximumDate().getYear(),
                                    state.getDoubleLabelSettings().getDatePickerSettings().getMaximumDate().getMonth() - 1,
                                    state.getDoubleLabelSettings().getDatePickerSettings().getMaximumDate().getDay()
                            );
                            ((XDatePreference) pref).setMaxDate(calendar.getTimeInMillis());
                        }
                        pref.setSummary(state.getDoubleLabelSettings().getDatePickerSettings().getFormattedValue().getText());
                        pref.setOnPreferenceChangeListener((preference, newValue) -> {
                            final String[] pieces = newValue.toString().split("-");

                            final int year = Integer.parseInt(pieces[0]);
                            final int month = Integer.parseInt(pieces[1]);
                            final int day = Integer.parseInt(pieces[2]);

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                                            .setDatePickerChange(GdiRealtimeSettings.DatePickerChange.newBuilder()
                                                    .setNewDate(GdiRealtimeSettings.Date.newBuilder()
                                                            .setYear(year).setMonth(month).setDay(day)
                                                    )
                                            )
                            );

                            return true;
                        });
                        break;
                    case CIQ_LINK: // Connect IQ Store
                        pref = new Preference(activity);
                        pref.setVisible(debug);
                        pref.setEnabled(false);
                        break;
                    case HEIGHT_PICKER: // height
                        pref = new EditTextPreference(activity);
                        ((EditTextPreference) pref).setText(String.valueOf(state.getDoubleLabelSettings().getHeightPickerSettings().getHeightValue()));
                        ((EditTextPreference) pref).setSummary(state.getDoubleLabelSettings().getHeightPickerSettings().getFormattedValue().getText());
                        if (state.getDoubleLabelSettings().getHeightPickerSettings().getMeasurementUnit() == GdiDataTypes.MeasurementUnit.METRIC) {
                            ((EditTextPreference) pref).setDialogTitle(R.string.activity_prefs_height_cm);
                            ((EditTextPreference) pref).setTitle(R.string.activity_prefs_height_cm);
                        } else {
                            ((EditTextPreference) pref).setDialogTitle(R.string.activity_prefs_height_inches);
                            ((EditTextPreference) pref).setTitle(R.string.activity_prefs_height_inches);
                        }
                        ((EditTextPreference) pref).setOnBindEditTextListener(p -> {
                            p.setInputType(InputType.TYPE_CLASS_NUMBER);
                            p.addTextChangedListener(new MinMaxTextWatcher(p, 0, 300));
                            p.setSelection(p.getText().length());
                        });
                        ((EditTextPreference) pref).setOnPreferenceChangeListener((preference, newValue) -> {
                            final int newValueInt = Integer.parseInt(newValue.toString());

                            pref.setEnabled(false);
                            sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                                            .setHeightPickerValueChange(GdiRealtimeSettings.HeightPickerValueChange.newBuilder()
                                                    .setHeightValue(newValueInt)
                                                    .setMeasurementUnit(state.getDoubleLabelSettings().getHeightPickerSettings().getMeasurementUnit())
                                            )
                            );
                            return true;
                        });
                        break;
                    default:
                        LOG.info("unknown setting type {}", entry.getLinkOptions().getLinkType());
                        supported = false;
                        pref = new Preference(activity);
                }
            } else { // No target
                switch (entry.getType()) {
                    case TEXT: // notice
                        pref = new Preference(activity);
                        pref.setSummary(entry.getTitle().getText());
                        break;
                    case HEADER: // category
                        pref = new PreferenceCategory(activity);
                        break;
                    case SPACER: // space
                        pref = new PreferenceCategory(activity);
                        pref.setTitle("");
                        break;
                    case TOGGLE: // switch
                        pref = new SwitchPreferenceCompat(activity);
                        pref.setLayoutResource(R.layout.preference_checkbox);
                        ((SwitchPreferenceCompat) pref).setChecked(Objects.requireNonNull(state).getToggleSettings().getToggleValue());
                        ((SwitchPreferenceCompat) pref).setSummary(Objects.requireNonNull(state).getToggleSettings().getSubtext().getText());
                        pref.setOnPreferenceChangeListener((preference, newValue) -> {
                            pref.setEnabled(false);
                            sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                                            .setToggleChange(GdiRealtimeSettings.ToggleChange.newBuilder()
                                                    .setNewToggleValue((Boolean) newValue)
                                            )
                            );
                            return true;
                        });

                        break;
                    case SINGLE_LABEL: // single line + optional icon
                    case DOUBLE_LABEL: // double line
                        pref = new Preference(activity);
                        break;
                    case SINGLE_LABEL_WITH_ACTION_ICON: // single line with action (eg. glances)
                    case ACTION: // single line, normally in list for selection?
                        pref = new Preference(activity);
                        pref.setOnPreferenceClickListener(preference -> {
                            pref.setEnabled(false);
                            sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                            );
                            return true;
                        });
                        break;
                    case DEVICE_VIEW: // device + status?
                    case FEATURE_SETUP: // finish setup
                    case FIND_MY_DEVICE: // find my device
                    case PREFERRED_ACTIVITY_TRACKER: // preferred activity tracker
                    case HELP_AND_INFO: // help & info
                    case AVAILABLE_ACCESSORIES: // available accessories?
                        pref = new Preference(activity);
                        pref.setVisible(debug);
                        pref.setEnabled(false);
                        break;
                    case REORDER_ITEM: // sortable + delete
                        // Add all sortable items and then continue
                        for (int i = 0; i < entry.getReorderListOptions().getListItemsCount(); i++) {
                            final GdiRealtimeSettings.ReorderListItem sortEntry = entry.getReorderListOptions().getListItems(i);
                            final Preference sortPref = new Preference(activity);
                            final int iFinal = i;

                            final List<RunnableListIconItem> sortableOptions = new ArrayList<>(3);
                            if (i > 0) {
                                sortableOptions.add(new RunnableListIconItem(activity.getString(R.string.widget_move_up), R.drawable.ic_arrow_upward, () -> {
                                    sortPref.setEnabled(false);
                                    sendChangeRequest(
                                            GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                                    .setCompositionId(screenId)
                                                    .setRowId(sortEntry.getRowId())
                                                    .setReorderableItemChange(GdiRealtimeSettings.ReorderableItemChange.newBuilder()
                                                            .setNewListPosition(iFinal - 1)
                                                    )
                                    );
                                }));
                            }
                            if (i < entry.getReorderListOptions().getListItemsCount() - 1) {
                                sortableOptions.add(new RunnableListIconItem(activity.getString(R.string.widget_move_down), R.drawable.ic_arrow_downward, () -> {
                                    sortPref.setEnabled(false);
                                    sendChangeRequest(
                                            GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                                    .setCompositionId(screenId)
                                                    .setRowId(sortEntry.getRowId())
                                                    .setReorderableItemChange(GdiRealtimeSettings.ReorderableItemChange.newBuilder()
                                                            .setNewListPosition(iFinal + 1)
                                                    )
                                    );
                                }));
                            }
                            sortableOptions.add(new RunnableListIconItem(activity.getString(R.string.appmananger_app_delete), R.drawable.ic_delete, () -> {
                                sortPref.setEnabled(false);
                                sendChangeRequest(
                                    GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                                .setCompositionId(screenId)
                                                .setRowId(sortEntry.getRowId())
                                                .setReorderableItemChange(GdiRealtimeSettings.ReorderableItemChange.newBuilder()
                                                        .setIsDelete(true)
                                                )
                                );
                            }));
                            final SimpleIconListAdapter sortOptionsAdapter = new SimpleIconListAdapter(activity, sortableOptions);
                            sortPref.setTitle(sortEntry.getTitle().getText());
                            sortPref.setPersistent(false);
                            sortPref.setIconSpaceReserved(false);
                            sortPref.setKey("rt_pref_" + screenId + "_" + entry.getRowId() + "__" + sortEntry.getRowId());
                            sortPref.setOnPreferenceClickListener(preference -> {
                                new MaterialAlertDialogBuilder(activity)
                                        .setTitle(sortPref.getTitle())
                                        .setAdapter(sortOptionsAdapter, (dialogInterface, j) -> sortableOptions.get(j).getAction().run())
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .create().show();
                                return true;
                            });
                            prefScreen.addPreference(sortPref);
                        }

                        continue; // We already added all options above, continue
                    case EDIT_TEXT: // text
                        pref = new EditTextPreference(activity);

                        ((EditTextPreference) pref).setOnBindEditTextListener(p -> {
                            int maxValue = Integer.MAX_VALUE;
                            if (entry.getEditTextOptions().hasEditTextProperties() && entry.getEditTextOptions().getEditTextProperties().hasMaximumCodePointLength()) {
                                p.setFilters(new InputFilter[]{new InputFilter.LengthFilter(entry.getEditTextOptions().getEditTextProperties().getMaximumCodePointLength())});
                            }
                            p.setSelection(p.getText().length());
                        });
                        ((EditTextPreference) pref).setOnPreferenceChangeListener((preference, newValue) -> {
                            if (StringUtils.isNullOrEmpty(newValue.toString())) {
                                return true;
                            }
                            pref.setEnabled(false);
                            sendChangeRequest(
                                GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                                            .setCompositionId(screenId)
                                            .setRowId(entry.getRowId())
                                            .setEditTextValueChange(GdiRealtimeSettings.EditTextValueChange.newBuilder()
                                                    .setNewEditTextValue(newValue.toString())
                                            )
                            );
                            return true;
                        });
                        break;
                    default:
                        LOG.info("unknown row type {}", entry.getType());
                        supported = false;
                        pref = new Preference(activity);
                }
            }

            if (StringUtils.isNullOrEmpty(pref.getTitle())
                    && entry.getType() != GdiRealtimeSettings.RowType.TEXT
                    && entry.getType() != GdiRealtimeSettings.RowType.SPACER) {
                pref.setTitle(!StringUtils.isEmpty(entry.getTitle().getText()) ? entry.getTitle().getText() : activity.getString(R.string.unknown));

                if (pref instanceof DialogPreference) {
                    ((DialogPreference) pref).setDialogTitle(pref.getTitle());
                }
            }

            final int icon = getIcon(entry);
            if (icon != 0) {
                pref.setIcon(icon);
            } else {
                pref.setIconSpaceReserved(false);
            }

            if (state != null && !StringUtils.isEmpty(state.getDoubleLabelSettings().getSubtext().getText())) {
                pref.setSummary(state.getDoubleLabelSettings().getSubtext().getText().replace("%", "%%"));
            }

            if (state != null && state.hasViewState()) {
                switch (state.getViewState()) {
                    case HIDDEN:
                        pref.setVisible(false);
                        break;
                    case DISABLED:
                        pref.setEnabled(false);
                        break;
                    default:
                        LOG.warn("Unknown state value {}", state.getViewState());
                }
            }

            if (!supported) {
                pref.setEnabled(false);

                if (StringUtils.isNullOrEmpty(pref.getSummary())) {
                    pref.setSummary(R.string.unsupported);
                } else {
                    pref.setSummary(activity.getString(R.string.menuitem_unsupported, pref.getSummary()));
                }
            }

            if (debug) {
                final StringBuilder sb = new StringBuilder();

                if (pref.getSummary() != null && pref.getSummary().length() != 0) {
                    sb.append(pref.getSummary()).append("\n");
                }

                sb.append("id=").append(entry.getRowId());
                sb.append(", type=").append(entry.getType());

                if (icon == 0 && entry.hasIcon()) {
                    sb.append(", icon=").append(entry.getIcon());
                }

                if (entry.hasLinkOptions()) {
                    sb.append(", targetType=").append(entry.getLinkOptions().getLinkType());
                    if (entry.getLinkOptions().hasDestPage()) {
                        sb.append(", targetActivity=").append(entry.getLinkOptions().getDestPage());
                    }
                }

                // when using pref.setSummary(value), values containing percent signs (%) can
                // cause UnknownFormatConversionException in java.util.Formatter. For example:
                // setting System / Backlight / Brightness with values like "50%" and "75%"
                pref.setSummaryProvider(new PlainFormatter(sb.toString()));
            }

            pref.setPersistent(false);
            pref.setKey("rt_pref_" + screenId + "_" + entry.getRowId());
            prefScreen.addPreference(pref);
        }

        // If no preferences after the last visible preference category are visible, hide it
        boolean previousWasVisible = false;
        PreferenceCategory lastSeenCategory = null;
        for (int i = prefScreen.getPreferenceCount() - 1; i >= 0; i--) {
            final Preference pref = prefScreen.getPreference(i);
            if (pref instanceof PreferenceCategory) {
                lastSeenCategory = (PreferenceCategory) pref;

                if (!previousWasVisible) {
                    lastSeenCategory.setVisible(false);
                }

                previousWasVisible = false;
            } else {
                previousWasVisible |= pref.isVisible();
            }
        }

        if (!previousWasVisible && lastSeenCategory != null) {
            lastSeenCategory.setVisible(false);
        }
    }

    void populateMenu(final Menu menu) {
        if (screenDefinition == null) {
            return;
        }

        final boolean debug = GBApplication.getDevicePrefs(device).getBoolean(PREF_DEBUG, BuildConfig.DEBUG);

        for (final GdiRealtimeSettings.MenuItem menuEntry : screenDefinition.getMenuItemsList()) {
            final MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, menuEntry.getTitle().getText());
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            boolean supported = true;

            if (menuEntry.hasLinkOptions()) {
                switch (menuEntry.getLinkOptions().getLinkType()) {
                    case INTERNAL: // subscreen
                    case INTERNAL_REINSTANTIATE: // subscreen with options for a specific preference
                        menuItem.setOnMenuItemClickListener(item -> {
                            final Intent newIntent = new Intent(requireContext(), GarminRealtimeSettingsActivity.class);
                            newIntent.putExtra(GBDevice.EXTRA_DEVICE, device);
                            newIntent.putExtra(GarminRealtimeSettingsActivity.EXTRA_SCREEN_ID, menuEntry.getLinkOptions().getDestCompositionId());
                            requireActivity().startActivityForResult(newIntent, 0);
                            return true;
                        });
                        break;
                    default:
                        LOG.info("unknown menu target type {}", menuEntry.getLinkOptions().getLinkType());
                        supported = false;
                        break;
                }
            } else {
                LOG.info("Menu entry {} has no target", menuEntry.getTitle().getText());
                supported = false;
            }

            if (!supported) {
                menuItem.setEnabled(false);
                menuItem.setVisible(debug);
            }
        }
    }

    private final class IntegerPicker implements EditTextPreference.OnBindEditTextListener, Preference.OnPreferenceChangeListener {
        private final GdiRealtimeSettings.CompositionRow integerEntry;
        private final int integerScreenId;

        private IntegerPicker(@NonNull GdiRealtimeSettings.CompositionRow integerEntry, int integerScreenId) {
            this.integerEntry = integerEntry;
            this.integerScreenId = integerScreenId;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            int minValue = Integer.MIN_VALUE;
            int maxValue = Integer.MAX_VALUE;
            if (integerEntry.getLinkOptions().getIntegerPickerOptions().hasMinimum()) {
                minValue = integerEntry.getLinkOptions().getIntegerPickerOptions().getMinimum();
            }
            if (integerEntry.getLinkOptions().getIntegerPickerOptions().hasMaximum()) {
                maxValue = integerEntry.getLinkOptions().getIntegerPickerOptions().getMaximum();
            }
            editText.addTextChangedListener(new MinMaxTextWatcher(editText, minValue, maxValue));
            editText.setSelection(editText.getText().length());
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, @NonNull Object newValue) {
            final int newValueInt = Integer.parseInt(newValue.toString());

            preference.setEnabled(false);
            sendChangeRequest(
                GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                            .setCompositionId(integerScreenId)
                            .setRowId(integerEntry.getRowId())
                            .setIntegerPickerChange(GdiRealtimeSettings.IntegerPickerChange.newBuilder()
                                    .setNewIntegerValue(newValueInt)
                            )
            );
            return true;
        }

        Preference createPreference(FragmentActivity activity, GdiRealtimeSettings.RowValue state) {
            final EditTextPreference preference = new EditTextPreference(activity);
            final GdiRealtimeSettings.IntegerPickerSettings summaryValue = state.getDoubleLabelSettings().getIntegerPickerSettings();
            preference.setText(String.valueOf(summaryValue.getIntegerValue()));
            preference.setSummary(summaryValue.getFormattedValue().getText());
            preference.setOnBindEditTextListener(this);
            preference.setOnPreferenceChangeListener(this);
            return preference;
        }
    }

    private final class DurationPicker implements EditTextPreference.OnBindEditTextListener, Preference.OnPreferenceChangeListener {
        private final GdiRealtimeSettings.CompositionRow durationEntry;
        private final int durationScreenId;

        private DurationPicker(@NonNull GdiRealtimeSettings.CompositionRow durationEntry, int durationScreenId) {
            this.durationEntry = durationEntry;
            this.durationScreenId = durationScreenId;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            int minValue = 0;
            int maxValue = Integer.MAX_VALUE;
            if (durationEntry.getLinkOptions().getTimePickerOptions().hasMinTimeSeconds()) {
                minValue = durationEntry.getLinkOptions().getTimePickerOptions().getMinTimeSeconds();
            }
            if (durationEntry.getLinkOptions().getTimePickerOptions().hasMaxTimeSeconds()) {
                maxValue = durationEntry.getLinkOptions().getTimePickerOptions().getMaxTimeSeconds();
            }
            editText.addTextChangedListener(new MinMaxTextWatcher(editText, minValue, maxValue));
            editText.setSelection(editText.getText().length());
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, @NonNull Object newValue) {
            final int newSeconds = Integer.parseInt(newValue.toString());

            preference.setEnabled(false);
            sendChangeRequest(
                GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                            .setCompositionId(durationScreenId)
                            .setRowId(durationEntry.getRowId())
                            .setTimePickerChange(GdiRealtimeSettings.TimePickerChange.newBuilder()
                                    .setNewSeconds(newSeconds)
                            )
            );
            return true;
        }

        Preference createPreference(FragmentActivity activity, GdiRealtimeSettings.RowValue state) {
            final EditTextPreference preference = new EditTextPreference(activity);
            final GdiRealtimeSettings.TimePickerSettings summaryValue = state.getDoubleLabelSettings().getTimePickerSettings();
            preference.setText(String.valueOf(summaryValue.getSeconds()));
            preference.setSummary(summaryValue.getFormattedValue().getText());
            preference.setOnBindEditTextListener(this);
            preference.setOnPreferenceChangeListener(this);
            return preference;
        }
    }

    private final class FloatPicker implements EditTextPreference.OnBindEditTextListener, Preference.OnPreferenceChangeListener {
        private final GdiRealtimeSettings.CompositionRow floatEntry;
        private final int floatScreenId;

        private FloatPicker(@NonNull GdiRealtimeSettings.CompositionRow floatEntry, int floatScreenId) {
            this.floatEntry = floatEntry;
            this.floatScreenId = floatScreenId;
        }

        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            float minValue = Float.MIN_VALUE;
            float maxValue = Float.MAX_VALUE;
            if (floatEntry.getLinkOptions().getFloatPickerOptions().hasMinimum()) {
                minValue = floatEntry.getLinkOptions().getFloatPickerOptions().getMinimum();
            }
            if (floatEntry.getLinkOptions().getFloatPickerOptions().hasMaximum()) {
                maxValue = floatEntry.getLinkOptions().getFloatPickerOptions().getMaximum();
            }
            editText.addTextChangedListener(new MinMaxFloatWatcher(editText, minValue, maxValue));
            editText.setSelection(editText.getText().length());
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, @NonNull Object newValue) {
            final float newFloat = Float.parseFloat(newValue.toString());

            preference.setEnabled(false);
            sendChangeRequest(
                GdiRealtimeSettings.SettingsChangeRequest.newBuilder()
                            .setCompositionId(floatScreenId)
                            .setRowId(floatEntry.getRowId())
                            .setFloatPickerChange(GdiRealtimeSettings.FloatPickerChange.newBuilder()
                                    .setNewFloatValue(newFloat)
                            )
            );
            return true;
        }

        EditTextPreference createPreference(FragmentActivity activity, GdiRealtimeSettings.RowValue state) {
            final EditTextPreference preference = new EditTextPreference(activity);
            final GdiRealtimeSettings.FloatPickerSettings summaryValue = state.getDoubleLabelSettings().getFloatPickerSettings();
            preference.setText(String.valueOf(summaryValue.getFloatValue()));
            preference.setSummary(summaryValue.getFormattedValue().getText());
            preference.setOnBindEditTextListener(this);
            preference.setOnPreferenceChangeListener(this);
            return preference;
        }
    }

    @DrawableRes
    private int getIcon(final GdiRealtimeSettings.CompositionRow entry) {
        if (entry.hasIcon()) {
            switch (entry.getIcon()) {
                //
                // Main menu
                case GARMIN_PAY: // Garmin Pay
                    return R.drawable.ic_credit_card;
                case TEXT_RESPONSES: // Text Responses
                    return R.drawable.ic_reply;
                case WATCH_FACE: // Clocks
                    return R.drawable.ic_access_time;
                case WIDGETS: // Glances
                    return R.drawable.ic_widgets;
                case CONTROLS: // Controls
                    return R.drawable.ic_menu;
                case ACTIVITIES_AND_APPS: // Activities / Apps, have the same icon
                    return R.drawable.ic_activity_unknown_small;
                case SHORTCUT: // Shortcut
                    return R.drawable.ic_shortcut;
                case NOTIFICATIONS_AND_ALERTS: // Notifications & Alerts
                    return R.drawable.ic_notifications;
                case WRIST_HR: // Wrist heart rate frequency
                    return R.drawable.ic_heartrate;
                case ALARMS: // Alarms
                    return R.drawable.ic_access_alarms;
                case SENSORS_AND_ACCESSORIES: // Sensors & accessories
                case WATCH_SENSORS: // Watch Sensors
                    return R.drawable.ic_sensor_calibration;
                case ACCESSORIES: // Accessories
                    return R.drawable.ic_bluetooth_searching;
                case MAP: // Map
                    return R.drawable.ic_map;
                case MUSIC: // Music
                    return R.drawable.ic_music_note;
                case PHONE: // Phone
                    return R.drawable.ic_phone;
                case CONNECTIVITY: // Connectivity
                    return R.drawable.ic_bluetooth_searching;
                case AUDIO_PROMPTS: // Audio Prompts
                case SOUND_AND_VIB: // Sound & Vibe
                    return R.drawable.ic_volume_up;
                case DISPLAY_AND_BRIGHTNESS: // Display & Brightness
                    return R.drawable.ic_wb_sunny;
                case FOCUS_MODES: // Focus Modes
                    return R.drawable.ic_focus;
                case USER_PROFILE: // User Profile
                    return R.drawable.ic_person;
                case SAFETY: // Safety & Tracking
                    return R.drawable.ic_emergency;
                case ACTIVITY_TRACKING: // Activity Tracking
                    return R.drawable.ic_activity_unknown_small;
                case NAVIGATION: // Navigation
                    return R.drawable.ic_navigation;
                case POWER_SETTINGS: // Power manager
                    return R.drawable.ic_battery;
                case SYSTEM_SETTINGS: // System
                    return R.drawable.ic_settings;
                case SOLAR_OPTIONS: // Solar
                    return R.drawable.ic_wb_sunny;
                case APPEARANCE: // Appearance
                    return R.drawable.ic_paint;
                case HEALTH_AND_WELLNESS: // Health & wellness
                    return R.drawable.ic_health;
                case ACCESSIBILITY: // Accessibility
                    return R.drawable.ic_accessibility_new;

                //
                // Sortable screens (glances, apps, etc)
                case ADD_ACTION:
                    return R.drawable.ic_add_gray;
                case DELETE_ACTION:
                    return R.drawable.ic_remove;
                case INREACH_TRACKING: // inReach tracking
                    return R.drawable.ic_share_location;
                case INREACH_REMOTE: // inReach remote
                    return R.drawable.ic_settings_remote;
                case SOUND_SETTINGS: // sound settings
                    return R.drawable.ic_notifications_active;
                case DISPLAY:
                    return R.drawable.ic_device_display;
                default:
                    LOG.info("no icon mapping found for: {}", entry.getIcon());
                    return 0;
            }
        }

        return 0;
    }

    void toggleDebug() {
        final Prefs prefs = GBApplication.getDevicePrefs(device);
        prefs.getPreferences().edit()
                .putBoolean(PREF_DEBUG, !prefs.getBoolean(PREF_DEBUG, BuildConfig.DEBUG))
                .apply();

        reload();
    }

    void shareDebug() {
        final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");

        final StringBuilder sb = new StringBuilder();

        sb.append("screenId: ").append(screenId);
        sb.append("\n");

        sb.append("settingsScreen: ");
        if (screenDefinition != null) {
            sb.append(GB.hexdump(screenDefinition.toByteArray()));
        } else {
            sb.append("null");
        }
        sb.append("\n");

        sb.append("settingsState: ");
        if (screenState != null) {
            sb.append(GB.hexdump(screenState.toByteArray()));
        } else {
            sb.append("null");
        }
        sb.append("\n");

        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Garmin Settings Screen " + screenId);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());

        try {
            startActivity(Intent.createChooser(intent, "Share debug info"));
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "Failed to share text", Toast.LENGTH_LONG).show();
        }
    }

    private void sendChangeRequest(final GdiRealtimeSettings.SettingsChangeRequest.Builder changeRequest) {
        screenDefinition = null;
        screenState = null;
        final Smart smart = Smart.newBuilder()
                .setRealtimeSettingsService(GdiRealtimeSettings.RealtimeSettingsService.newBuilder()
                        .setSettingsChangeRequest(changeRequest)
                ).build();
        GBApplication.deviceService(device).onSendConfiguration("protobuf:" + GB.hexdump(smart.toByteArray()));
    }

    private static record PlainFormatter(CharSequence value) implements Preference.SummaryProvider {
        @Nullable
        @Override
        public CharSequence provideSummary(@NonNull Preference preference) {
            return value;
        }
    }
}
