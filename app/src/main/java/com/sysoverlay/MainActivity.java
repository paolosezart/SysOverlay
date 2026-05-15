package com.sysoverlay;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 100;
    private static final int REQ_LOCATION = 101;

    private TextView tvStatus;
    private Button btnToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus  = findViewById(R.id.tv_status);
        btnToggle = findViewById(R.id.btn_toggle);

        btnToggle.setOnClickListener(v -> handleToggle());
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    // ─── Toggle logic ──────────────────────────────────────────────────────────

    private void handleToggle() {
        if (!Settings.canDrawOverlays(this)) {
            askOverlayPermission();
            return;
        }
        if (!hasLocationPermission()) {
            askLocationPermission();
            return;
        }
        toggleService();
    }

    private void toggleService() {
        if (OverlayService.isRunning) {
            stopService(new Intent(this, OverlayService.class));
        } else {
            startForegroundService(new Intent(this, OverlayService.class));
        }
        new android.os.Handler().postDelayed(this::updateUI, 300);
    }

    // ─── UI ────────────────────────────────────────────────────────────────────

    private void updateUI() {
        boolean overlay  = Settings.canDrawOverlays(this);
        boolean location = hasLocationPermission();
        boolean running  = OverlayService.isRunning;

        if (!overlay) {
            tvStatus.setText("⚠️ Нужно разрешение «Поверх других приложений»");
            btnToggle.setText("Выдать разрешение");
        } else if (!location) {
            tvStatus.setText("⚠️ Нужно разрешение геолокации (для получения имени Wi-Fi)");
            btnToggle.setText("Выдать разрешение геолокации");
        } else if (running) {
            tvStatus.setText("✅ Оверлей работает");
            btnToggle.setText("Остановить");
        } else {
            tvStatus.setText("⏹ Оверлей остановлен");
            btnToggle.setText("Запустить оверлей");
        }
    }

    // ─── Permissions ───────────────────────────────────────────────────────────

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void askOverlayPermission() {
        new AlertDialog.Builder(this)
                .setTitle("Разрешение на оверлей")
                .setMessage("Найдите SysOverlay в списке и включите переключатель «Разрешить отображение поверх других приложений».")
                .setPositiveButton("Открыть настройки", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_OVERLAY);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void askLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                             Manifest.permission.ACCESS_COARSE_LOCATION},
                REQ_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_LOCATION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Геолокация разрешена", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Без геолокации SSID не доступен (ограничение Android)", Toast.LENGTH_LONG).show();
            }
            updateUI();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        updateUI();
    }
}
