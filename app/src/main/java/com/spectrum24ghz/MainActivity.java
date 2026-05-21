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

    private ActivityMainBinding binding;
    private WifiManager wifiManager;
    private ChannelAdapter channelAdapter;

    private final List<WifiChannel> channels = buildAllChannels();

    private final Handler scanTimeoutHandler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                boolean fresh = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false
                );
                handleScanResults(fresh);
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    grants -> {
                        boolean allGranted = true;
                        for (Boolean granted : grants.values()) {
                            if (!granted) { allGranted = false; break; }
                        }
                        if (allGranted) {
                            startScanFlow();
                        } else {
                            showPermissionDeniedDialog();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        setupRecyclerView();

        binding.btnScan.setOnClickListener(v -> requestPermissionsAndScan());
        binding.btnAbout.setOnClickListener(v -> showAboutDialog());

        requestPermissionsAndScan();
    }

    private void setupRecyclerView() {
        channelAdapter = new ChannelAdapter(channels);
        binding.rvChannels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChannels.setAdapter(channelAdapter);
        binding.rvChannels.setHasFixedSize(false);
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
        for (String perm : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }
        if (missing.isEmpty()) {
            startScanFlow();
        } else {
            permissionLauncher.launch(missing.toArray(new String[0]));
        }
    }

    private void startScanFlow() {
        if (isAirplaneModeOn()) {
            binding.tvScanStatus.setText(
                    "Modo avión activo — tabla de canales disponible sin escaneo"
            );
            binding.tvScanStatus.setVisibility(View.VISIBLE);
            return;
        }

        boolean wifiEncendido = wifiManager.isWifiEnabled();

        // isScanAlwaysAvailable permite escanear aunque el WiFi esté apagado,
        // siempre que el usuario tenga activo "Escaneo WiFi" en ajustes de ubicación
        boolean scanSinWifi = wifiManager.isScanAlwaysAvailable();

        if (wifiEncendido || scanSinWifi) {
            initiateWifiScan();
        } else {
            showEnableWifiDialog();
        }
    }

    /**
     * Detecta si el modo avión está activo.
     * En modo avión el radio WiFi se apaga a nivel de hardware y no es posible escanear.
     */
    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(
                getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0
        ) == 1;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void initiateWifiScan() {
        setScanningState(true);
        clearScanResults();

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanReceiver, filter);
        }
        receiverRegistered = true;

        boolean scanIniciado = wifiManager.startScan();

        if (!scanIniciado) {
            // El sistema bloqueó el scan — usamos los resultados cacheados
            handleScanResults(false);
            return;
        }

        // Fallback por si el broadcast no llega
        scanTimeoutHandler.postDelayed(() -> {
            if (receiverRegistered) handleScanResults(false);
        }, 12_000L);
    }

    @SuppressLint("MissingPermission")
    private void handleScanResults(boolean fresh) {
        safeUnregisterReceiver();
        scanTimeoutHandler.removeCallbacksAndMessages(null);

        clearScanResults();

        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
            if (results == null) results = new ArrayList<>();
        } catch (SecurityException e) {
            results = new ArrayList<>();
        }

        for (ScanResult result : results) {
            int freq = result.frequency;

            // Filtro de frecuencia: rango 2.4 GHz
            if (freq < 2400 || freq > 2500) continue;

            Integer canal = frecuenciaACanal(freq);
            if (canal == null) continue;

            WifiChannel channelEntry = null;
            for (WifiChannel ch : channels) {
                if (ch.getChannel() == canal) { channelEntry = ch; break; }
            }
            if (channelEntry == null) continue;

            int pct;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int max = wifiManager.getMaxSignalLevel();
                pct = WifiManager.calculateSignalLevel(result.level, max + 1) * 100 / max;
                pct = Math.max(0, Math.min(pct, 100));
            } else {
                pct = WifiManager.calculateSignalLevel(result.level, 100);
            }

            String ssid = (result.SSID == null || result.SSID.isEmpty())
                    ? "<oculto>"
                    : result.SSID;

            channelEntry.getNetworks().add(
                    new ScannedNetwork(ssid, result.BSSID, result.level, pct)
            );
        }

        for (WifiChannel ch : channels) {
            Collections.sort(ch.getNetworks(),
                    (a, b) -> Integer.compare(b.getRssi(), a.getRssi()));
        }

        channelAdapter.notifyDataSetChanged();
        setScanningState(false);

        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        int total = 0;
        for (WifiChannel ch : channels) total += ch.getNetworks().size();

        String estado = total > 0
                ? "Último escaneo: " + hora + "  ·  " + total + " redes encontradas"
                : "Último escaneo: " + hora + "  ·  sin redes detectadas";

        binding.tvScanStatus.setText(estado);
        binding.tvScanStatus.setVisibility(View.VISIBLE);
    }

    private void clearScanResults() {
        for (WifiChannel ch : channels) ch.getNetworks().clear();
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

    /**
     * Lista fija de los 14 canales del estándar 802.11 en 2.4 GHz.
     * Los canales 1, 6 y 11 son los no solapados; se marcan como "prime".
     * Esta tabla existe independientemente del radio WiFi, por eso la app
     * sigue siendo útil incluso con modo avión activo.
     */
    private List<WifiChannel> buildAllChannels() {

        class Def {
            final int ch, freq;
            final String region;
            final boolean restricted;

            Def(int ch, int freq, String region, boolean restricted) {
                this.ch = ch; this.freq = freq;
                this.region = region; this.restricted = restricted;
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

        // HashSet compatible con minSdk 23; Set.of() requeriría API 26
        Set<Integer> primeChannels = new HashSet<>(Arrays.asList(1, 6, 11));

        List<WifiChannel> list = new ArrayList<>();
        for (Def d : defs) {
            list.add(new WifiChannel(
                    d.ch, d.freq, d.region, d.restricted, primeChannels.contains(d.ch)
            ));
        }
        return list;
    }

    /** Diálogo de información: universidad, materia, docente e integrantes del grupo. */
    private void showAboutDialog() {
        DialogAboutBinding dialogBinding = DialogAboutBinding.inflate(LayoutInflater.from(this));
        new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(getString(R.string.about_close), null)
                .show();
    }

    private void showEnableWifiDialog() {
        new AlertDialog.Builder(this)
                .setTitle("WiFi desactivado")
                .setMessage(
                        "Para detectar redes en los canales se necesita WiFi activo, o bien " +
                                "activar \"Escaneo WiFi\" en Ajustes → Ubicación → Avanzado.\n\n" +
                                "La tabla de canales sigue disponible sin conexión."
                )
                .setPositiveButton("Ajustes WiFi", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .setNeutralButton("Ver tabla", (d, w) -> d.dismiss())
                .show();
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permisos requeridos")
                .setMessage(
                        "Sin permisos de ubicación el sistema no expone los resultados del escaneo WiFi. " +
                                "La tabla de todos los canales sigue disponible."
                )
                .setPositiveButton("Reintentar", (d, w) -> requestPermissionsAndScan())
                .setNegativeButton("Continuar sin escaneo", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        safeUnregisterReceiver();
        scanTimeoutHandler.removeCallbacksAndMessages(null);
    }
}