package com.spectrum24ghz.models;

public class ScannedNetwork {

    private final String ssid;
    private final String bssid;
    private final int rssi;
    private final int signalPercent;

    public ScannedNetwork(String ssid, String bssid, int rssi, int signalPercent) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.signalPercent = signalPercent;
    }

    public String getSsid()        { return ssid; }
    public String getBssid()       { return bssid; }
    public int getRssi()           { return rssi; }
    public int getSignalPercent()  { return signalPercent; }
}