package com.celltrace.logger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机自启（需用户在「⋯」里打开开关） */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String a = intent == null ? null : intent.getAction();
            boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                    || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                    || "android.intent.action.HTC_QUICKBOOT_POWERON".equals(a);
            if (!boot) return;
            if (!TraceService.shouldRecordOnBoot(context)) return;

            Intent svc = new Intent(context, TraceService.class).setAction(TraceService.ACTION_START_RECORD);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Throwable ignored) {
        }
    }
}
