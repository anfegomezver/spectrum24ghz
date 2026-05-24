package com.spectrum24ghz.models;

public class ScannedNetwork {

    private final String ssid;
    private final String bssid;
    private final int rssi;
    private final int signalPercent;
    private final int frequency;
    private final int channel;
    private final String capabilities;

    public ScannedNetwork(String ssid, String bssid, int rssi, int signalPercent,
                          int frequency, int channel, String capabilities) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.signalPercent = signalPercent;
        this.frequency = frequency;
        this.channel = channel;
        this.capabilities = capabilities;
    }

    public String getSsid() {
        return ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public int getRssi() {
        return rssi;
    }

    public int getSignalPercent() {
        return signalPercent;
    }

    public int getFrequency() {
        return frequency;
    }

    public int getChannel() {
        return channel;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public String getSecurityLabel() {
        if (capabilities == null || capabilities.isEmpty()) {
            return "Open";
        }
        
        String capsUpper = capabilities.toUpperCase();
        
        if (capsUpper.contains("WPA3")) {
            if (capsUpper.contains("EAP")) return "WPA3-Ent (Enterprise)";
            return "WPA3-SAE (Personal)";
        }
        
        if (capsUpper.contains("WPA2")) {
            if (capsUpper.contains("EAP")) return "WPA2-EAP (Enterprise)";
            if (capsUpper.contains("PSK")) return "WPA2-PSK (Personal)";
            return "WPA2";
        }
        
        if (capsUpper.contains("WPA")) {
            if (capsUpper.contains("EAP")) return "WPA-EAP (Enterprise)";
            if (capsUpper.contains("PSK")) return "WPA-PSK (Personal)";
            return "WPA";
        }
        
        if (capsUpper.contains("WEP")) {
            return "WEP (Legacy)";
        }
        
        if (capsUpper.contains("OWE")) {
            return "Enhanced Open (OWE)";
        }
        
        return "Open (No Security)";
    }
}