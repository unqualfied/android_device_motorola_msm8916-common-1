/*
 * Copyright (c) 2015 The CyanogenMod Project
 * Copyright (c) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dirtyunicorns.settings.device;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.util.List;
import java.util.LinkedList;

import com.dirtyunicorns.settings.device.actions.CameraActivationSensor;
import com.dirtyunicorns.settings.device.actions.ChopChopSensor;
import com.dirtyunicorns.settings.device.actions.FlipToMute;
import com.dirtyunicorns.settings.device.actions.LiftToSilence;
import com.dirtyunicorns.settings.device.actions.ProximitySilencer;
import com.dirtyunicorns.settings.device.actions.UpdatedStateNotifier;

import com.dirtyunicorns.settings.device.doze.DozePulseAction;
import com.dirtyunicorns.settings.device.doze.FlatUpSensor;
import com.dirtyunicorns.settings.device.doze.ProximitySensor;
import com.dirtyunicorns.settings.device.doze.ScreenReceiver;
import com.dirtyunicorns.settings.device.doze.ScreenStateNotifier;
import com.dirtyunicorns.settings.device.doze.StowSensor;

public class MotoActionsService extends IntentService implements ScreenStateNotifier,
        UpdatedStateNotifier {
    private static final String TAG = "MotoActions";

    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;

    private final List<ScreenStateNotifier> mScreenStateNotifiers = new LinkedList<>();
    private final List<UpdatedStateNotifier> mUpdatedStateNotifiers = new LinkedList<>();

    public MotoActionsService(Context context) {
        super("MotoActionservice");

        Log.d(TAG, "Starting");

        MotoActionsSettings MotoActionsSettings = new MotoActionsSettings(context, this);
        SensorHelper sensorHelper = new SensorHelper(context);
        new ScreenReceiver(context, this);

        DozePulseAction dozePulseAction = new DozePulseAction(context);
        mScreenStateNotifiers.add(dozePulseAction);

        // Actionable sensors get screen on/off notifications
        mScreenStateNotifiers.add(new FlatUpSensor(MotoActionsSettings, sensorHelper, dozePulseAction));
        mScreenStateNotifiers.add(new ProximitySensor(MotoActionsSettings, sensorHelper, dozePulseAction));
        mScreenStateNotifiers.add(new StowSensor(MotoActionsSettings, sensorHelper, dozePulseAction));

        // Other actions that are always enabled
        mUpdatedStateNotifiers.add(new CameraActivationSensor(MotoActionsSettings, sensorHelper));
        if (!Device.isSurnia()){
            mUpdatedStateNotifiers.add(new ChopChopSensor(MotoActionsSettings, sensorHelper));
        } else {
            Log.d(TAG, "No ChopChop");
        }

        mUpdatedStateNotifiers.add(new ProximitySilencer(MotoActionsSettings, context, sensorHelper));
        mUpdatedStateNotifiers.add(new FlipToMute(MotoActionsSettings, context, sensorHelper));
        mUpdatedStateNotifiers.add(new LiftToSilence(MotoActionsSettings, context, sensorHelper));

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        String tag = context.getPackageName() + ":ServiceWakeLock";
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        updateState();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    }

    @Override
    public void screenTurnedOn() {
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOn();
        }
    }

    @Override
    public void screenTurnedOff() {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOff();
        }
    }

    public void updateState() {
        if (mPowerManager.isInteractive()) {
            screenTurnedOn();
        } else {
            screenTurnedOff();
        }
        for (UpdatedStateNotifier notifier : mUpdatedStateNotifiers) {
            notifier.updateState();
        }
    }
}
