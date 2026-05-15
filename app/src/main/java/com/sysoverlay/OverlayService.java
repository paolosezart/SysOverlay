package com.sysoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class OverlayService extends Service {

    public static volatile boolean isRunning = false;

    private static final String CHANNEL_ID = "sysoverlay_ch";
    private static final int    NOTIF_ID   = 42;
    private static final long   INTERVAL   = 2000; // update every 2 sec

    private WindowManager         wm;
    private View                  overlayView;
    private WindowManager.LayoutParams params;
    private Handler               handler;
    private Runnable              updateTask;

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        buildOverlay();
        scheduleUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null)      handler.removeCallbacks(updateTask);
        if (overlayView != null)  {
            try { wm.removeView(overlayView); } catch (Exception ignored) {}
        }
    }

    // ─── Overlay ───────────────────────────────────────────────────────────────

    private void buildOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_widget, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 100;

        setupDrag();

        ImageButton btnClose = overlayView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> stopSelf());

        wm.addView(overlayView, params);
    }

    private void setupDrag() {
        View handle = overlayView.findViewById(R.id.drag_handle);

        final int[] downX   = {0};
        final int[] downY   = {0};
        final int[] initPX  = {0};
        final int[] initPY  = {0};

        handle.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0]  = (int) ev.getRawX();
                    downY[0]  = (int) ev.getRawY();
                    initPX[0] = params.x;
                    initPY[0] = params.y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    params.x = initPX[0] + (int)(ev.getRawX() - downX[0]);
                    params.y = initPY[0] + (int)(ev.getRawY() - downY[0]);
                    wm.updateViewLayout(overlayView, params);
                    return true;
            }
            return false;
        });
    }

    // ─── Updates ───────────────────────────────────────────────────────────────

    private void scheduleUpdates() {
        handler = new Handler(Looper.getMainLooper());
        updateTask = new Runnable() {
            @Override public void run() {
                refreshData();
                handler.postDelayed(this, INTERVAL);
            }
        };
        handler.post(updateTask);
    }

    private void refreshData() {
        WifiInfo info = null;
        try {
            WifiManager wifiMgr = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiMgr != null && wifiMgr.isWifiEnabled()) {
                info = wifiMgr.getConnectionInfo();
            }
        } catch (Exception ignored) {}

        // ── SSID ────────────────────────────────────────────────────────────────
        String ssid = "—";
        if (info != null) {
            String raw = info.getSSID();
            // Android wraps SSID in quotes; strip them
            if (raw != null && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            if (raw != null && !raw.equals("<unknown ssid>") && !raw.isEmpty()) {
                ssid = raw;
            }
        }

        // ── Band (2.4 / 5 GHz) ──────────────────────────────────────────────────
        String band = "—";
        if (info != null) {
            int freq = info.getFrequency(); // MHz
            if (freq > 0) {
                if (freq < 3000) {
                    band = "2.4 GHz (" + freq + " MHz)";
                } else if (freq < 6000) {
                    band = "5 GHz (" + freq + " MHz)";
                } else {
                    band = "6 GHz (" + freq + " MHz)";
                }
            }
        }

        // ── IP ──────────────────────────────────────────────────────────────────
        String ip = "—";
        if (info != null) {
            int ipInt = info.getIpAddress();
            if (ipInt != 0) {
                ip = String.format("%d.%d.%d.%d",
                        (ipInt & 0xFF),
                        (ipInt >> 8  & 0xFF),
                        (ipInt >> 16 & 0xFF),
                        (ipInt >> 24 & 0xFF));
            }
        }
        // Fallback via NetworkInterface
        if (ip.equals("—")) ip = getLocalIp();

        // ── Link speed (current) ─────────────────────────────────────────────────
        String linkSpeed = "—";
        if (info != null) {
            int speed = info.getLinkSpeed(); // Mbps
            if (speed > 0) linkSpeed = speed + " Mbps";
        }

        // ── Max supported speed (Android 10+) ────────────────────────────────────
        String maxSpeed = "—";
        if (info != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            int max = info.getMaxSupportedTxLinkSpeedMbps();
            if (max > 0) maxSpeed = max + " Mbps";
        }

        // ── Uptime ────────────────────────────────────────────────────────────────
        long uptimeSec  = SystemClock.elapsedRealtime() / 1000;
        long h   = uptimeSec / 3600;
        long m   = (uptimeSec % 3600) / 60;
        long s   = uptimeSec % 60;
        String uptime = String.format("%02d:%02d:%02d", h, m, s);

        // ── Push to views ─────────────────────────────────────────────────────────
        setField(R.id.tv_ssid,       "📶 " + ssid);
        setField(R.id.tv_band,       "📡 " + band);
        setField(R.id.tv_ip,         "🌐 " + ip);
        setField(R.id.tv_link_speed, "⚡ " + linkSpeed);
        setField(R.id.tv_max_speed,  "🚀 " + maxSpeed);
        setField(R.id.tv_uptime,     "⏱ " + uptime);
    }

    private void setField(int id, String text) {
        TextView tv = overlayView.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "—";
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "SysOverlay", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Системный оверлей активен");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SysOverlay активен")
                .setContentText("Нажмите для управления")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
