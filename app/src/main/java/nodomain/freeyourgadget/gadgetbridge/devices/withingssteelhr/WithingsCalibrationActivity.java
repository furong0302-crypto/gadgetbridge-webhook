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
package nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch.WithingsScanwatchDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;

public class WithingsCalibrationActivity extends AbstractGBActivity {

    enum Hands {
        HOURS((short)1),
        MINUTES((short)0),
        ACTIVITY_TARGET((short)2);

        private final short code;
        Hands(short code) {
            this.code = code;
        }

    }

    private GBDevice device;
    private LocalBroadcastManager localBroadcastManager;
    private final String[] appDialAdvices = new String[3];
    private final String[] crownAdvices = new String[3];
    private final Hands[] hands = new Hands[]{Hands.HOURS, Hands.MINUTES, Hands.ACTIVITY_TARGET};
    private short handIndex = 0;
    private Button previousButton;
    private Button nextButton;
    private Button okButton;
    private Button confirmButton;
    private RotaryControl rotaryControl;
    private TextView textView;
    private boolean crownMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_withings_calibration);
        List<GBDevice> devices = GBApplication.app().getDeviceManager().getSelectedDevices();
        for(GBDevice device : devices){
            if (device.getType() == DeviceType.WITHINGS_STEEL_HR
                    || device.getDeviceCoordinator() instanceof WithingsScanwatchDeviceCoordinator) {
                this.device = device;
                break;
            }
        }

        if (device == null) {
            Toast.makeText(this, R.string.watch_not_connected, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initView();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.sendBroadcast(new Intent(WithingsBaseDeviceSupport.START_HANDS_CALIBRATION_CMD));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localBroadcastManager != null) {
            localBroadcastManager.sendBroadcast(new Intent(WithingsBaseDeviceSupport.STOP_HANDS_CALIBRATION_CMD));
        }
    }

    private void initView() {
        appDialAdvices[0] = getString(R.string.withings_calibration_text_hours);
        appDialAdvices[1] = getString(R.string.withings_calibration_text_minutes);
        appDialAdvices[2] = getString(R.string.withings_calibration_text_activity_target);

        crownAdvices[0] = getString(R.string.withings_calibration_crown_text_hours);
        crownAdvices[1] = getString(R.string.withings_calibration_crown_text_minutes);
        crownAdvices[2] = getString(R.string.withings_calibration_crown_text_activity_target);

        textView = findViewById(R.id.withings_calibration_textview);
        rotaryControl = findViewById(R.id.rotary_control);
        confirmButton = findViewById(R.id.withings_calibration_button_confirm);

        rotaryControl.setRotationListener(new RotaryControl.RotationListener() {
            @Override
            public void onRotation(short movementAmount) {
                Intent calibration = new Intent(WithingsBaseDeviceSupport.HANDS_CALIBRATION_CMD);
                calibration.putExtra("hand", hands[handIndex].code);
                calibration.putExtra("movementAmount", movementAmount);
                localBroadcastManager.sendBroadcast(calibration);
            }
        });

        // Crown mode: confirm button registers current position as 12 o'clock
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent calibration = new Intent(WithingsBaseDeviceSupport.HANDS_CALIBRATION_CMD);
                calibration.putExtra("hand", hands[handIndex].code);
                calibration.putExtra("movementAmount", (short) 0);
                localBroadcastManager.sendBroadcast(calibration);
                // Auto-advance to next hand or finish
                if (handIndex < 2) {
                    handIndex++;
                    updateUI();
                } else {
                    finish();
                }
            }
        });

        previousButton = findViewById(R.id.withings_calibration_button_previous);
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handIndex--;
                updateUI();
            }
        });

        nextButton = findViewById(R.id.withings_calibration_button_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handIndex++;
                updateUI();
            }
        });

        okButton = findViewById(R.id.withings_calibration_button_ok);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Mode toggle
        RadioGroup modeGroup = findViewById(R.id.withings_calibration_mode_group);

        // Steel HR has no crown
        if (device.getType() == DeviceType.WITHINGS_STEEL_HR) {
            modeGroup.setVisibility(View.GONE);
        } else {
            modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    crownMode = (checkedId == R.id.withings_calibration_mode_crown);
                    updateUI();
                }
            });
        }

        updateUI();
    }

    private void updateUI() {
        if (crownMode) {
            rotaryControl.setVisibility(View.GONE);
            confirmButton.setVisibility(View.VISIBLE);
            // Hide Previous/Next/OK - crown mode auto-advances on Confirm
            previousButton.setVisibility(View.GONE);
            nextButton.setVisibility(View.GONE);
            okButton.setVisibility(View.GONE);
            textView.setText(crownAdvices[handIndex]);
            // Send a tiny movement to tell the watch which hand the crown should
            // control.  Without this the firmware doesn't know which hand is active
            // and the crown may move the wrong hand (or none at all).
            selectHandForCrown();
        } else {
            rotaryControl.setVisibility(View.VISIBLE);
            confirmButton.setVisibility(View.GONE);
            previousButton.setVisibility(View.VISIBLE);
            nextButton.setVisibility(View.VISIBLE);
            okButton.setVisibility(View.VISIBLE);
            textView.setText(appDialAdvices[handIndex]);
            rotaryControl.reset();
            enableButtons();
        }
    }

    /**
     * Sends a minimal MOVE_HAND command to activate the current hand for crown
     * input.  The watch firmware only routes crown rotation to a hand after it
     * has received at least one MOVE_HAND for that hand.
     */
    private void selectHandForCrown() {
        if (localBroadcastManager == null) return;
        Intent calibration = new Intent(WithingsBaseDeviceSupport.HANDS_CALIBRATION_CMD);
        calibration.putExtra("hand", hands[handIndex].code);
        calibration.putExtra("movementAmount", (short) 1);
        localBroadcastManager.sendBroadcast(calibration);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void enableButtons() {
        nextButton.setEnabled(handIndex < 2);
        previousButton.setEnabled(handIndex > 0);
        okButton.setEnabled(handIndex == 2);
    }
}
