package com.spectrum24ghz;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.spectrum24ghz.databinding.ItemNetworkBinding;
import com.spectrum24ghz.models.ScannedNetwork;

import java.util.List;

public class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.NetworkViewHolder> {

    public interface OnNetworkClickListener {
        void onNetworkClick(ScannedNetwork network);
    }

    private final List<ScannedNetwork> networks;
    private final OnNetworkClickListener listener;

    public NetworkAdapter(List<ScannedNetwork> networks, OnNetworkClickListener listener) {
        this.networks = networks;
        this.listener = listener;
    }

    class NetworkViewHolder extends RecyclerView.ViewHolder {

        private final ItemNetworkBinding nb;

        NetworkViewHolder(ItemNetworkBinding binding) {
            super(binding.getRoot());
            this.nb = binding;
        }

        void bind(ScannedNetwork net) {
            nb.tvSsid.setText(net.getSsid());
            nb.tvBssid.setText(net.getBssid());
            nb.tvRssi.setText(net.getRssi() + " dBm");
            nb.progressSignal.setProgress(net.getSignalPercent());

            // Determine dynamic WiFi signal strength icon and color tint
            int percent = net.getSignalPercent();
            int wifiIconRes;
            int sigColorRes;

            if (percent >= 85) {
                wifiIconRes = R.drawable.ic_wifi_4;
                sigColorRes = R.color.sig_strong;
            } else if (percent >= 60) {
                wifiIconRes = R.drawable.ic_wifi_3;
                sigColorRes = R.color.sig_good;
            } else if (percent >= 35) {
                wifiIconRes = R.drawable.ic_wifi_2;
                sigColorRes = R.color.sig_medium;
            } else if (percent >= 15) {
                wifiIconRes = R.drawable.ic_wifi_1;
                sigColorRes = R.color.sig_medium;
            } else {
                wifiIconRes = R.drawable.ic_wifi_0;
                sigColorRes = R.color.sig_weak;
            }

            nb.ivSignalIcon.setImageResource(wifiIconRes);
            nb.ivSignalIcon.setColorFilter(
                    ContextCompat.getColor(nb.getRoot().getContext(), sigColorRes)
            );

            nb.progressSignal.setProgressTintList(
                    ContextCompat.getColorStateList(nb.getRoot().getContext(), sigColorRes)
            );

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNetworkClick(net);
                }
            });
        }
    }

    @NonNull
    @Override
    public NetworkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNetworkBinding binding = ItemNetworkBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new NetworkViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NetworkViewHolder holder, int position) {
        holder.bind(networks.get(position));
    }

    @Override
    public int getItemCount() {
        return networks.size();
    }
}
