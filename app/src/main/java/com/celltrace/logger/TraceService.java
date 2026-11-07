package com.celltrace.logger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

/**
 * 前台服务：定时采样、写入 CSV、更新通知。
 * 只要服务活着，界面就能拿到实时快照；是否落盘由 AppState.recording 决定。
 */
public class TraceService extends Service {

    public static final String ACTION_START_RECORD = "com.celltrace.logger.action.START_RECORD";
    public static final String ACTION_STOP_RECORD = "com.celltrace.logger.action.STOP_RECORD";
    public static final String ACTION_QUIT = "com.celltrace.logger.action.QUIT";
    public static final String ACTION_INTERVAL = "com.celltrace.logger.action.INTERVAL";

    public static final String PREFS = "celltrace";
    public static final String KEY_INTERVAL = "interval_ms";

    private static final String CHANNEL_ID = "trace";
    private static final int NOTIF_ID = 4711;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private CellSampler sampler;
    private CsvStore csv;
    private LocationManager lm;
    private GnssStatus.Callback gnssCallback;
    private PowerManager.WakeLock wakeLock;
    private boolean running;
    private long startedAt;
    private long rows;
    private int intervalMs = 1000;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                tick();
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, intervalMs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sampler = new CellSampler(this);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        intervalMs = Math.max(500, sp.getInt(KEY_INTERVAL, 1000));
        createChannel();
        startLocation();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_QUIT.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_INTERVAL.equals(action) && intent != null) {
            intervalMs = Math.max(500, intent.getIntExtra("ms", 1000));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_INTERVAL, intervalMs).apply();
        }

        // 5 秒内必须进入前台
        startForegroundSafe(buildNotification(null));

        if (ACTION_START_RECORD.equals(action)) startRecording();
        else if (ACTION_STOP_RECORD.equals(action)) stopRecording();

        if (!running) {
            running = true;
            startedAt = System.currentTimeMillis();
            handler.post(ticker);
        }
        return START_STICKY;
    }

    private void startRecording() {
        if (AppState.recording) return;
        if (csv == null) csv = new CsvStore();
        if (!csv.open(this)) {
            toast("无法创建记录文件");
            return;
        }
        rows = 0;
        startedAt = System.currentTimeMillis();
        AppState.recording = true;
        acquireWakeLock();
    }

    private void stopRecording() {
        if (csv != null) csv.close();
        AppState.recording = false;
        releaseWakeLock();
    }

    private void tick() {
        Snapshot s = sampler.sample();
        s.recording = AppState.recording;
        s.intervalMs = intervalMs;

        if (s.recording) {
            if (csv == null) csv = new CsvStore();
            if (!csv.isOpen()) csv.open(this);
            if (s.primary != null) {
                csv.append(s.primary);
                rows = csv.rows();
            }
            s.rows = rows;
            s.startMs = startedAt;
            s.csvPath = csv.path();
        } else {
            s.rows = csv != null ? csv.rows() : 0;
            s.startMs = 0;
            s.csvPath = csv == null ? null : csv.path();
        }

        AppState.publish(s);
        updateNotification(s);
    }

    // ---------------- 定位 ----------------

    private void startLocation() {
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                sampler.updateLocation(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper());
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener, Looper.getMainLooper());
            }
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) sampler.updateLocation(last);
        } catch (SecurityException ignored) {
        }

        try {
            gnssCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    int used = 0;
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        if (status.usedInFix(i)) used++;
                    }
                    sampler.updateSatellites(used);
                }
            };
            lm.registerGnssStatusCallback(gnssCallback, handler);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 通知 ----------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "路测记录", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("持续采集蜂窝小区信息");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(Snapshot s) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent content = PendingIntent.getActivity(this, 0, open, flags);

        PendingIntent stopPi = PendingIntent.getService(this, 1,
                new Intent(this, TraceService.class).setAction(ACTION_STOP_RECORD), flags);

        String title = "CellTraceLogger";
        String text = "等待采集…";
        if (s != null && s.primary != null) {
            CellRecord p = s.primary;
            text = p.rat + " " + (p.band.isEmpty() ? "" : p.band + " ")
                    + "PCI " + p.pciText() + " · "
                    + (p.rsrp == CellRecord.INVALID ? "--" : p.rsrp + " dBm");
            if (s.recording) text += " · 记录中 " + s.rows + " 行";
        }

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
            b.setPriority(Notification.PRIORITY_LOW);
        }
        b.setSmallIcon(R.drawable.ic_stat_trace)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (s != null && s.recording) {
            b.addAction(new Notification.Action.Builder(R.drawable.ic_stat_trace, "停止记录", stopPi).build());
        }
        return b.build();
    }

    private void updateNotification(Snapshot s) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(s));
        } catch (Throwable ignored) {
        }
    }

    private void startForegroundSafe(Notification n) {
        try {
            startForeground(NOTIF_ID, n);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 电源 ----------------

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CellTraceLogger:record");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(8 * 60 * 60 * 1000L);
        } catch (Throwable ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        wakeLock = null;
    }

    // ---------------- 生命周期 ----------------

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(ticker);
        stopRecording();
        try {
            if (lm != null) {
                if (gnssCallback != null) lm.unregisterGnssStatusCallback(gnssCallback);
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void toast(String msg) {
        try {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
