package com.spectrum24ghz;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.spectrum24ghz.databinding.ItemChannelBinding;
import com.spectrum24ghz.databinding.ItemNetworkBinding;
import com.spectrum24ghz.models.ScannedNetwork;
import com.spectrum24ghz.models.WifiChannel;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private final List<WifiChannel> channels;

    public ChannelAdapter(List<WifiChannel> channels) {
        this.channels = channels;
    }

    class ChannelViewHolder extends RecyclerView.ViewHolder {

        private final ItemChannelBinding b;

        ChannelViewHolder(ItemChannelBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(WifiChannel ch) {
            b.tvChannelNumber.setText("CH\n" + ch.getChannel());
            b.tvFrequency.setText(ch.getFrequencyMhz() + " MHz");
            b.tvRegion.setText(ch.getRegionLabel());

            b.tvRestricted.setVisibility(ch.isRestricted() ? View.VISIBLE : View.GONE);

            int badgeColor;
            if (ch.isRestricted()) {
                badgeColor = R.color.badge_restricted;
            } else if (ch.isPrime()) {
                badgeColor = R.color.badge_prime;
            } else {
                badgeColor = R.color.badge_normal;
            }
            b.tvChannelNumber.setBackgroundTintList(
                    ContextCompat.getColorStateList(b.getRoot().getContext(), badgeColor)
            );

            b.tvPrime.setVisibility(
                    ch.isPrime() && !ch.isRestricted() ? View.VISIBLE : View.GONE
            );

            int count = ch.getNetworks().size();
            if (count == 0) {
                b.tvNetworkCount.setText("Sin redes detectadas");
            } else if (count == 1) {
                b.tvNetworkCount.setText("1 red detectada");
            } else {
                b.tvNetworkCount.setText(count + " redes detectadas");
            }

            int congestion = Math.min(count * 10, 100);
            b.progressCongestion.setProgress(congestion);

            int barColor;
            if (congestion == 0) {
                barColor = R.color.bar_empty;
            } else if (congestion <= 30) {
                barColor = R.color.bar_low;
            } else if (congestion <= 60) {
                barColor = R.color.bar_medium;
            } else {
                barColor = R.color.bar_high;
            }
            b.progressCongestion.setProgressTintList(
                    ContextCompat.getColorStateList(b.getRoot().getContext(), barColor)
            );

            b.ivExpand.setRotation(ch.isExpanded() ? 180f : 0f);
            b.ivExpand.setVisibility(count > 0 ? View.VISIBLE : View.INVISIBLE);

            if (ch.isExpanded() && count > 0) {
                populateNetworks(ch.getNetworks());
                b.layoutNetworks.setVisibility(View.VISIBLE);
            } else {
                b.layoutNetworks.setVisibility(View.GONE);
            }

            b.getRoot().setOnClickListener(v -> {
                if (!ch.getNetworks().isEmpty()) {
                    ch.setExpanded(!ch.isExpanded());
                    notifyItemChanged(getAdapterPosition());
                }
            });
        }

        private void populateNetworks(List<ScannedNetwork> networks) {
            b.layoutNetworks.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(b.getRoot().getContext());

            for (ScannedNetwork net : networks) {
                ItemNetworkBinding nb = ItemNetworkBinding.inflate(
                        inflater, b.layoutNetworks, false
                );

                nb.tvSsid.setText(net.getSsid());
                nb.tvBssid.setText(net.getBssid());
                nb.tvRssi.setText(net.getRssi() + " dBm");
                nb.progressSignal.setProgress(net.getSignalPercent());

                int sigColor;
                if (net.getSignalPercent() >= 70) {
                    sigColor = R.color.sig_strong;
                } else if (net.getSignalPercent() >= 40) {
                    sigColor = R.color.sig_good;
                } else if (net.getSignalPercent() >= 20) {
                    sigColor = R.color.sig_medium;
                } else {
                    sigColor = R.color.sig_weak;
                }
                nb.progressSignal.setProgressTintList(
                        ContextCompat.getColorStateList(b.getRoot().getContext(), sigColor)
                );

                b.layoutNetworks.addView(nb.getRoot());
            }
        }
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChannelBinding binding = ItemChannelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ChannelViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        holder.bind(channels.get(position));
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }
}