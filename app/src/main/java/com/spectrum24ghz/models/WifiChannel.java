package com.spectrum24ghz.models;

import java.util.ArrayList;
import java.util.List;

public class WifiChannel {

    private final int channel;
    private final int frequencyMhz;
    private final String regionLabel;
    private final boolean isRestricted;
    private final boolean isPrime;
    private final List<ScannedNetwork> networks;
    private boolean isExpanded;

    public WifiChannel(int channel, int frequencyMhz, String regionLabel,
                       boolean isRestricted, boolean isPrime) {
        this.channel = channel;
        this.frequencyMhz = frequencyMhz;
        this.regionLabel = regionLabel;
        this.isRestricted = isRestricted;
        this.isPrime = isPrime;
        this.networks = new ArrayList<>();
        this.isExpanded = false;
    }

    public int getChannel()          { return channel; }
    public int getFrequencyMhz()     { return frequencyMhz; }
    public String getRegionLabel()   { return regionLabel; }
    public boolean isRestricted()    { return isRestricted; }
    public boolean isPrime()         { return isPrime; }
    public List<ScannedNetwork> getNetworks() { return networks; }
    public boolean isExpanded()      { return isExpanded; }
    public void setExpanded(boolean expanded) { isExpanded = expanded; }
}