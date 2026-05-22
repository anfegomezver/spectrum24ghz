package com.spectrum24ghz;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spectrum24ghz.databinding.ActivityMainBinding;
import com.spectrum24ghz.databinding.DialogAboutBinding;
import com.spectrum24ghz.models.ScannedNetwork;
import com.spectrum24ghz.models.WifiChannel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final long SCAN_COOLDOWN_MS = 30_000L;
    private static final long SCAN_TIMEOUT_MS  = 12_000L;

    // CH1 = 2412 MHz, CH14 = 2484 MHz. +-2 MHz de margen por chipsets fuera de spec
    private static final int FREQ_MIN = 2410;
    private static final int FREQ_MAX = 2486;

    private long lastScanStartMs = 0L;

    private ActivityMainBinding binding;
    private WifiManager wifiManager;
    private ChannelAdapter channelAdapter;

    private final List<WifiChannel> channels = buildAllChannels();
    private final Handler scanTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Handler countdownHandler   = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (lastScanStartMs == 0L) return;
            long remaining = (SCAN_COOLDOWN_MS - (SystemClock.elapsedRealtime() - lastScanStartMs)) / 1000;
            if (remaining > 0) {
                showStatus("Próximo escaneo en " + remaining + "s");
                countdownHandler.postDelayed(this, 1_000L);
            } else {
                binding.tvScanStatus.setVisibility(View.GONE);
            }
        }
    };

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                boolean fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                handleScanResults(fresh);
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    grants -> {
                        boolean allGranted = true;
                        for (Boolean g : grants.values()) {
                            if (!g) { allGranted = false; break; }
                        }
                        if (allGranted) startScanFlow();
                        else showPermissionDeniedDialog();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        channelAdapter = new ChannelAdapter(channels);
        binding.rvChannels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChannels.setAdapter(channelAdapter);
        binding.rvChannels.setHasFixedSize(false);

        binding.btnScan.setOnClickListener(v -> requestPermissionsAndScan());
        binding.btnAbout.setOnClickListener(v -> showAboutDialog());

        requestPermissionsAndScan();
    }

    private List<String> requiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        return perms;
    }

    private void requestPermissionsAndScan() {
        List<String> missing = new ArrayList<>();
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                missing.add(p);
        }
        if (missing.isEmpty()) startScanFlow();
        else permissionLauncher.launch(missing.toArray(new String[0]));
    }

    private void startScanFlow() {
        long elapsed = SystemClock.elapsedRealtime() - lastScanStartMs;
        if (lastScanStartMs > 0 && elapsed < SCAN_COOLDOWN_MS) {
            countdownHandler.removeCallbacks(countdownTick);
            countdownHandler.post(countdownTick);
            loadCachedResults();
            return;
        }

        if (wifiManager.isWifiEnabled() || wifiManager.isScanAlwaysAvailable()) {
            initiateWifiScan();
        } else {
            showEnableWifiDialog();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void initiateWifiScan() {
        lastScanStartMs = SystemClock.elapsedRealtime();
        setScanningState(true);
        clearScanResults();

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
        receiverRegistered = true;

        if (!wifiManager.startScan()) {
            // El sistema bloqueó el scan (throttle o radio ocupado), se usa cache
            handleScanResults(false);
            return;
        }

        // Fallback por si el broadcast nunca llega.
        scanTimeoutHandler.postDelayed(() -> {
            if (receiverRegistered) handleScanResults(false);
        }, SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    private void handleScanResults(boolean fresh) {
        safeUnregisterReceiver();
        scanTimeoutHandler.removeCallbacksAndMessages(null);

        clearScanResults();
        populateChannels();
        channelAdapter.notifyDataSetChanged();
        setScanningState(false);

        String hora  = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        int    total = countNetworks();
        String estado = "Escaneo: " + hora + "  ·  "
                + (total > 0 ? total + " redes" : "sin redes detectadas")
                + (!fresh ? " · caché" : "");

        showStatus(estado);
    }

    @SuppressLint("MissingPermission")
    private void loadCachedResults() {
        clearScanResults();
        populateChannels();
        channelAdapter.notifyDataSetChanged();
    }

    @SuppressLint("MissingPermission")
    private void populateChannels() {
        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
            if (results == null) results = new ArrayList<>();
        } catch (SecurityException e) {
            results = new ArrayList<>();
        }

        for (ScanResult result : results) {
            int freq = result.frequency;
            if (freq < FREQ_MIN || freq > FREQ_MAX) continue;

            Integer canal = frecuenciaACanal(freq);
            if (canal == null) continue;

            WifiChannel channelEntry = null;
            for (WifiChannel ch : channels) {
                if (ch.getChannel() == canal) { channelEntry = ch; break; }
            }
            if (channelEntry == null) continue;

            String ssid = (result.SSID == null || result.SSID.isEmpty()) ? "<oculto>" : result.SSID;
            channelEntry.getNetworks().add(
                    new ScannedNetwork(ssid, result.BSSID, result.level, calcSignalPercent(result.level)));
        }

        for (WifiChannel ch : channels) {
            Collections.sort(ch.getNetworks(), (a, b) -> Integer.compare(b.getRssi(), a.getRssi()));
        }
    }

    private int calcSignalPercent(int rssi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int max = wifiManager.getMaxSignalLevel();
            return Math.max(0, Math.min(WifiManager.calculateSignalLevel(rssi, max + 1) * 100 / max, 100));
        }
        return WifiManager.calculateSignalLevel(rssi, 100);
    }

    private void clearScanResults() {
        for (WifiChannel ch : channels) ch.getNetworks().clear();
    }

    private int countNetworks() {
        int total = 0;
        for (WifiChannel ch : channels) total += ch.getNetworks().size();
        return total;
    }

    private void showStatus(String msg) {
        binding.tvScanStatus.setText(msg);
        binding.tvScanStatus.setVisibility(View.VISIBLE);
    }

    private void setScanningState(boolean scanning) {
        binding.progressBar.setVisibility(scanning ? View.VISIBLE : View.GONE);
        binding.btnScan.setEnabled(!scanning);
        binding.btnScan.setText(scanning ? getString(R.string.scanning) : getString(R.string.scan));
    }

    private void safeUnregisterReceiver() {
        if (receiverRegistered) {
            try { unregisterReceiver(scanReceiver); } catch (IllegalArgumentException ignored) {}
            receiverRegistered = false;
        }
    }

    private Integer frecuenciaACanal(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2407) / 5;
        return null;
    }

    // Canales 1,6,11 son los no solapados (prime). La tabla existe sin radio WiFi activo
    private List<WifiChannel> buildAllChannels() {
        class Def {
            final int ch, freq; final String region; final boolean restricted;
            Def(int ch, int freq, String region, boolean restricted) {
                this.ch = ch; this.freq = freq; this.region = region; this.restricted = restricted;
            }
        }
        List<Def> defs = new ArrayList<>(Arrays.asList(
                new Def(1,  2412, "Universal",              false),
                new Def(2,  2417, "Universal",              false),
                new Def(3,  2422, "Universal",              false),
                new Def(4,  2427, "Universal",              false),
                new Def(5,  2432, "Universal",              false),
                new Def(6,  2437, "Universal",              false),
                new Def(7,  2442, "Universal",              false),
                new Def(8,  2447, "Universal",              false),
                new Def(9,  2452, "Universal",              false),
                new Def(10, 2457, "Universal",              false),
                new Def(11, 2462, "Universal",              false),
                new Def(12, 2467, "Restringido (no US/CA)", true),
                new Def(13, 2472, "Restringido (no US/CA)", true),
                new Def(14, 2484, "Solo Japón — 802.11b",   true)
        ));
        Set<Integer> prime = new HashSet<>(Arrays.asList(1, 6, 11));
        List<WifiChannel> list = new ArrayList<>();
        for (Def d : defs) {
            list.add(new WifiChannel(d.ch, d.freq, d.region, d.restricted, prime.contains(d.ch)));
        }
        return list;
    }

    private void showAboutDialog() {
        DialogAboutBinding db = DialogAboutBinding.inflate(LayoutInflater.from(this));
        new AlertDialog.Builder(this)
                .setView(db.getRoot())
                .setPositiveButton(getString(R.string.about_close), null)
                .show();
    }

    private void showEnableWifiDialog() {
        new AlertDialog.Builder(this)
                .setTitle("WiFi desactivado")
                .setMessage("Para detectar redes necesitas WiFi activo, o activar " +
                        "\"Escaneo WiFi\" en Ajustes → Ubicación → Avanzado.")
                .setPositiveButton("Ajustes WiFi", (d, w) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .setNegativeButton("Ubicación", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNeutralButton("Cerrar", null)
                .setOnDismissListener(d -> {
                    boolean puedeEscanear = wifiManager.isWifiEnabled() || wifiManager.isScanAlwaysAvailable();
                    if (!puedeEscanear) showStatus("Activa WiFi o escaneo de ubicación para continuar");
                })
                .show();
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permisos requeridos")
                .setMessage("Sin permisos de ubicación el sistema no expone los resultados " +
                        "del escaneo WiFi. La tabla de canales sigue disponible.")
                .setPositiveButton("Reintentar", (d, w) -> requestPermissionsAndScan())
                .setNegativeButton("Continuar sin escaneo", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        safeUnregisterReceiver();
        scanTimeoutHandler.removeCallbacksAndMessages(null);
        countdownHandler.removeCallbacksAndMessages(null);
    }
}
