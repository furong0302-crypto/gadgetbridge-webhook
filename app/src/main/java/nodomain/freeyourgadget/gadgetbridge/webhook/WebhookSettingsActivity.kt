/*  Copyright (C) 2026 gadgetbridge-webhook contributors

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
package nodomain.freeyourgadget.gadgetbridge.webhook

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Date

class WebhookSettingsActivity : AbstractSettingsActivityV2() {
    override fun newFragment(): PreferenceFragmentCompat {
        return WebhookSettingsFragment()
    }

    class WebhookSettingsFragment : AbstractPreferenceFragment() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.webhook_settings, rootKey)

            // First visit: persist the default "upload everything" selection so the
            // multi-select list shows all categories checked instead of an empty list.
            if (!GBApplication.getPrefs().preferences.contains(WebhookConfig.PREF_DATA_TYPES)) {
                GBApplication.getPrefs().preferences.edit {
                    putStringSet(WebhookConfig.PREF_DATA_TYPES, WebhookConfig.ALL_DATA_TYPES)
                }
            }

            val prefRunNow = findPreference<Preference>(WebhookConfig.PREF_RUN_NOW)
            prefRunNow?.setOnPreferenceClickListener {
                // Estimate the backlog: if any device has more than the default
                // range (7 days) of unsent data, ask the user how much to upload.
                val backlogDays = WebhookUploader.estimateBacklogDays()
                if (backlogDays > WebhookConfig.MAX_RANGE_SECONDS / 86400) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.webhook_upload_large_title)
                        .setMessage(getString(R.string.webhook_upload_large_confirm, backlogDays))
                        .setPositiveButton(R.string.webhook_upload_large_full) { _, _ ->
                            runUpload(Long.MAX_VALUE / 2)
                        }
                        .setNeutralButton(R.string.webhook_upload_large_partial) { _, _ ->
                            runUpload(WebhookConfig.MAX_RANGE_SECONDS)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    runUpload(WebhookConfig.MAX_RANGE_SECONDS)
                }
                true
            }

            val prefResetCursor = findPreference<Preference>(WebhookConfig.PREF_RESET_CURSOR)
            prefResetCursor?.setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.webhook_pref_reset_cursor)
                    .setMessage(R.string.webhook_reset_cursor_confirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        WebhookConfig.resetCursors()
                        Toast.makeText(requireContext(), R.string.webhook_reset_cursor_done, Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }

            val prefEnabled = findPreference<Preference>(WebhookConfig.PREF_ENABLED)
            prefEnabled?.setOnPreferenceChangeListener { _: Preference?, _: Any? ->
                scheduleDelayed()
                true
            }

            val prefDataTypes = findPreference<Preference>(WebhookConfig.PREF_DATA_TYPES)
            prefDataTypes?.setOnPreferenceChangeListener { _: Preference?, _: Any? ->
                updateDataTypesSummary()
                true
            }
            updateDataTypesSummary()

            val prefInterval = findPreference<Preference>(WebhookConfig.PREF_INTERVAL_MINUTES)
            prefInterval?.setOnPreferenceChangeListener { _: Preference?, newValue: Any? ->
                updateIntervalSummary(newValue.toString().toIntOrNull() ?: 0)
                scheduleDelayed()
                true
            }
            updateIntervalSummary(WebhookConfig.getIntervalMinutes())
            val prefPreSync = findPreference<Preference>(WebhookConfig.PREF_PRE_SYNC)
            prefPreSync?.setOnPreferenceChangeListener { _: Preference?, _: Any? ->
                scheduleDelayed()
                true
            }
            updatePreSyncSummary()

            updateStatusRows()
        }

        override fun onResume() {
            super.onResume()
            updateStatusRows()
        }

        private fun scheduleDelayed() {
            lifecycleScope.launch(Dispatchers.Main) {
                delay(200)
                WebhookScheduler.schedule(requireContext())
            }
        }

        /** Executes the upload with a Material spinner dialog, then shows the result. */
        private fun runUpload(maxRangeSeconds: Long) {
            val density = requireContext().resources.displayMetrics.density
            val spinnerSize = (28 * density).toInt()
            val spinner = CircularProgressIndicator(requireContext()).apply {
                isIndeterminate = true
            }
            // "Uploading… ○" on one centered row.
            val row = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                val pad = (20 * density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(
                    android.widget.TextView(requireContext()).apply {
                        text = getString(R.string.webhook_upload_started)
                        textSize = 16f
                    },
                    android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    spinner,
                    android.widget.LinearLayout.LayoutParams(spinnerSize, spinnerSize).apply {
                        marginStart = (16 * density).toInt()
                    },
                )
            }
            val progress = AlertDialog.Builder(requireContext())
                .setView(row)
                .setCancelable(false)
                .show()
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { WebhookUploader.uploadAll(maxRangeSeconds) }
                progress.dismiss()
                val text = when {
                    result.pendingBind -> result.message
                    result.success -> getString(R.string.webhook_upload_done, result.uploadedSamples)
                    else -> getString(R.string.webhook_upload_failed, result.message)
                }
                Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
                updateStatusRows()
            }
        }

        private fun updateIntervalSummary(intervalMinutes: Int) {
            val summary = if (intervalMinutes > 0) {
                getString(R.string.webhook_pref_interval_summary, intervalMinutes)
            } else {
                getString(R.string.webhook_pref_interval_summary, WebhookConfig.DEFAULT_INTERVAL_MINUTES)
            }
            findPreference<Preference>(WebhookConfig.PREF_INTERVAL_MINUTES)?.summary = summary
        }

        private fun updatePreSyncSummary() {
            findPreference<Preference>(WebhookConfig.PREF_PRE_SYNC)?.summary =
                getString(R.string.webhook_pref_pre_sync_summary, WebhookConfig.PRE_SYNC_MIN_INTERVAL_MINUTES)
        }

        private fun updateDataTypesSummary() {
            val enabled = WebhookConfig.getEnabledDataTypes()
            val summary = if (enabled.size == WebhookConfig.ALL_DATA_TYPES.size) {
                getString(R.string.webhook_pref_data_types_summary_all)
            } else {
                getString(R.string.webhook_pref_data_types_summary_count, enabled.size, WebhookConfig.ALL_DATA_TYPES.size)
            }
            findPreference<Preference>(WebhookConfig.PREF_DATA_TYPES)?.summary = summary
        }

        private fun updateStatusRows() {
            val pairStatus = WebhookConfig.getPairStatus()
            val pairSummary = when (pairStatus) {
                WebhookConfig.PAIR_STATUS_OK -> getString(R.string.webhook_pair_status_ok)
                WebhookConfig.PAIR_STATUS_PENDING -> getString(R.string.webhook_pair_status_pending, "")
                WebhookConfig.PAIR_STATUS_FAILED -> getString(R.string.webhook_pair_status_failed)
                else -> getString(R.string.webhook_pair_status_unknown)
            }
            findPreference<Preference>(WebhookConfig.PREF_PAIR_STATUS)?.summary = pairSummary

            val lastExecution = WebhookConfig.getLastExecution()
            val prefLastExecution = findPreference<Preference>(WebhookConfig.PREF_LAST_EXECUTION)
            prefLastExecution?.summary = if (lastExecution > 0) {
                DateTimeUtils.formatDateTime(Date(lastExecution))
            } else {
                getString(R.string.unknown)
            }
            val lastError = WebhookConfig.getLastError()
            val prefLastStatus = findPreference<Preference>(WebhookConfig.PREF_LAST_STATUS)
            prefLastStatus?.summary = if (lastError.isNotEmpty()) {
                WebhookConfig.getLastStatus().ifEmpty { getString(R.string.unknown) } + "\n" + lastError
            } else {
                WebhookConfig.getLastStatus().ifEmpty { getString(R.string.unknown) }
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WebhookSettingsActivity::class.java)
    }
}
