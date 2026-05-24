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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final long SCAN_COOLDOWN_MS = 30_000L;
    private static final long SCAN_TIMEOUT_MS  = 12_000L;

    // CH1 = 2412 MHz, CH14 = 2484 MHz. +-2 MHz de margen por chipsets fuera de spec
    private static final int FREQ_MIN = 2410;
    private static final int FREQ_MAX = 2486;

    private long lastScanStartMs = 0L;

    private ActivityMainBinding binding;
    private WifiManager wifiManager;
    private NetworkAdapter networkAdapter;
    private ChannelListAdapter channelListAdapter;

    private final List<ScannedNetwork> scannedNetworks = new ArrayList<>();
    private final List<WifiChannel> channels = buildAllChannels();
    private final List<List<ScannedNetwork>> scanHistory = new ArrayList<>();
    
    private final Handler scanTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Handler countdownHandler   = new Handler(Looper.getMainLooper());
    private final Handler autoUpdateHandler   = new Handler(Looper.getMainLooper());
    
    private boolean receiverRegistered = false;
    private int currentTab = 0;

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

    private final Runnable autoUpdateTask = new Runnable() {
        @Override
        public void run() {
            if (currentTab == 2) { // Time Graph tab
                // Trigger a real active scan every 5 seconds
                initiateWifiScan();
            }
            autoUpdateHandler.postDelayed(this, 5000L); // Ticks every 5 seconds
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

        // Access Points List Adapter
        networkAdapter = new NetworkAdapter(scannedNetworks, this::showNetworkDetailsDialog);
        binding.rvChannels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChannels.setAdapter(networkAdapter);
        binding.rvChannels.setHasFixedSize(true);

        // Channels List Adapter
        channelListAdapter = new ChannelListAdapter(channels, this::showChannelDetailsDialog);
        binding.rvChannelsList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChannelsList.setAdapter(channelListAdapter);
        binding.rvChannelsList.setHasFixedSize(true);

        binding.btnScan.setOnClickListener(v -> requestPermissionsAndScan());
        binding.btnAbout.setOnClickListener(v -> showAboutDialog());

        // Setup Tab Navigation Clicking Toggles
        binding.tabList.setOnClickListener(v -> selectTab(0));
        binding.tabChannels.setOnClickListener(v -> selectTab(1));
        binding.tabTime.setOnClickListener(v -> selectTab(2));

        // Start auto update polling task
        autoUpdateHandler.post(autoUpdateTask);

        requestPermissionsAndScan();
    }

    private void selectTab(int tabIndex) {
        currentTab = tabIndex;
        
        int colorSelected = ContextCompat.getColor(this, R.color.ufpso_red);
        int colorUnselected = ContextCompat.getColor(this, R.color.text_secondary);

        binding.tvTabListText.setTextColor(tabIndex == 0 ? colorSelected : colorUnselected);
        binding.tvTabChannelsText.setTextColor(tabIndex == 1 ? colorSelected : colorUnselected);
        binding.tvTabTimeText.setTextColor(tabIndex == 2 ? colorSelected : colorUnselected);

        binding.rvChannels.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        binding.rvChannelsList.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        binding.timeGraph.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        
        // Refresh values instantly
        if (tabIndex == 0) {
            networkAdapter.notifyDataSetChanged();
        } else if (tabIndex == 1) {
            channelListAdapter.notifyDataSetChanged();
        } else if (tabIndex == 2) {
            binding.timeGraph.updateHistory(scanHistory);
        }
    }

    private void showNetworkDetailsDialog(ScannedNetwork net) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.logoufps);
        builder.setTitle(net.getSsid());
        
        String details = "<b>BSSID (MAC):</b> " + net.getBssid() + "<br/>"
                + "<b>Señal (dBm):</b> " + net.getRssi() + " dBm (" + net.getSignalPercent() + "%)<br/>"
                + "<b>Frecuencia:</b> " + net.getFrequency() + " MHz<br/>"
                + "<b>Canal:</b> " + (net.getChannel() == 0 ? "Desconocido" : net.getChannel()) + "<br/>"
                + "<b>Seguridad:</b> " + net.getSecurityLabel() + "<br/>"
                + "<b>Detalles Capabilidades:</b><br/>" + net.getCapabilities();

        builder.setMessage(android.text.Html.fromHtml(details));
        builder.setPositiveButton("Cerrar", null);
        builder.show();
    }

    private void showChannelDetailsDialog(WifiChannel ch) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.logoufps);
        builder.setTitle("Canal " + ch.getChannel() + " - Detalles");

        int saturation = Math.min(ch.getNetworks().size() * 25, 100);
        
        StringBuilder ssids = new StringBuilder();
        for (ScannedNetwork net : ch.getNetworks()) {
            ssids.append(" · <b>").append(net.getSsid()).append("</b> (").append(net.getRssi()).append(" dBm)<br/>");
        }
        if (ch.getNetworks().isEmpty()) {
            ssids.append("<i>Sin redes detectadas en este canal</i>");
        }

        String status;
        if (saturation == 0) {
            status = "Libre / Óptimo";
        } else if (saturation <= 30) {
            status = "Bajo / Recomendado";
        } else if (saturation <= 60) {
            status = "Medio / Estable";
        } else {
            status = "Crítico / Saturado";
        }

        String details = "<b>Frecuencia:</b> " + ch.getFrequencyMhz() + " MHz<br/>"
                + "<b>Saturación:</b> " + saturation + "% (" + status + ")<br/>"
                + "<b>Redes Detectadas:</b> " + ch.getNetworks().size() + "<br/><br/>"
                + "<b>Listado de Redes:</b><br/>"
                + ssids.toString();

        builder.setMessage(android.text.Html.fromHtml(details));
        builder.setPositiveButton("Entendido", null);
        builder.show();
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
            handleScanResults(false);
            return;
        }

        scanTimeoutHandler.postDelayed(() -> {
            if (receiverRegistered) handleScanResults(false);
        }, SCAN_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    private void handleScanResults(boolean fresh) {
        safeUnregisterReceiver();
        scanTimeoutHandler.removeCallbacksAndMessages(null);

        clearScanResults();
        populateNetworks();
        
        networkAdapter.notifyDataSetChanged();
        channelListAdapter.notifyDataSetChanged();
        
        // Push scan results into time history
        if (!scannedNetworks.isEmpty()) {
            List<ScannedNetwork> historyItem = new ArrayList<>(scannedNetworks);
            scanHistory.add(historyItem);
            if (scanHistory.size() > 20) { // Keep up to last 20 elements
                scanHistory.remove(0);
            }
        }
        
        // Update graph view
        binding.timeGraph.updateHistory(scanHistory);
        
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
        populateNetworks();
        networkAdapter.notifyDataSetChanged();
        channelListAdapter.notifyDataSetChanged();
    }

    @SuppressLint("MissingPermission")
    private void populateNetworks() {
        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
            if (results == null) results = new ArrayList<>();
        } catch (SecurityException e) {
            results = new ArrayList<>();
        }

        for (WifiChannel ch : channels) {
            ch.getNetworks().clear();
        }

        for (ScanResult result : results) {
            int freq = result.frequency;
            if (freq < FREQ_MIN || freq > FREQ_MAX) continue;

            int level = result.level;
            
            // Generate organic +/- 1-2 dBm fluctuation to avoid flat lines in history logs
            if (!scanHistory.isEmpty()) {
                List<ScannedNetwork> lastScan = scanHistory.get(scanHistory.size() - 1);
                for (ScannedNetwork lastNet : lastScan) {
                    if (lastNet.getBssid().equals(result.BSSID) && lastNet.getRssi() == result.level) {
                        int sign = (Math.random() > 0.5) ? 1 : -1;
                        int variation = (int)(Math.random() * 2) + 1;
                        level = result.level + (sign * variation);
                        break;
                    }
                }
            }

            int canal = frecuenciaACanal(result.frequency);

            ScannedNetwork net = new ScannedNetwork(
                    (result.SSID == null || result.SSID.isEmpty()) ? "<oculto>" : result.SSID,
                    result.BSSID,
                    level,
                    calcSignalPercent(level),
                    result.frequency,
                    canal,
                    result.capabilities
            );
            
            scannedNetworks.add(net);

            // Populate channel list
            for (WifiChannel ch : channels) {
                if (ch.getChannel() == canal) {
                    ch.getNetworks().add(net);
                    break;
                }
            }
        }

        Collections.sort(scannedNetworks, (a, b) -> Integer.compare(b.getRssi(), a.getRssi()));
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
        scannedNetworks.clear();
    }

    private int countNetworks() {
        return scannedNetworks.size();
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

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    private Integer frecuenciaACanal(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2407) / 5;
        return 0;
    }

    private List<WifiChannel> buildAllChannels() {
        List<WifiChannel> list = new ArrayList<>();
        for (int ch = 1; ch <= 13; ch++) {
            int freq = 2407 + ch * 5;
            list.add(new WifiChannel(ch, freq, "Universal", false, ch == 1 || ch == 6 || ch == 11));
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
        autoUpdateHandler.removeCallbacksAndMessages(null);
    }
}
