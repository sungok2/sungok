package com.galaxyuniversalio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

/** An explicit, non-exported receiver for mutable USB permission PendingIntents. */
public final class UsbPermissionReceiver extends BroadcastReceiver {
    public static final String ACTION_PERMISSION = "com.galaxyuniversalio.action.USB_PERMISSION";
    public static final String ACTION_RESULT = "com.galaxyuniversalio.action.USB_PERMISSION_RESULT";
    public static final String EXTRA_DEVICE_NAME = "device_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_PERMISSION.equals(intent.getAction())) return;
        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
        UsbDevice device;
        if (Build.VERSION.SDK_INT >= 33) {
            device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        } else {
            device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }
        Intent result = new Intent(ACTION_RESULT);
        result.setPackage(context.getPackageName());
        result.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, granted);
        if (device != null) result.putExtra(EXTRA_DEVICE_NAME, device.getDeviceName());
        context.sendBroadcast(result);
    }
}
