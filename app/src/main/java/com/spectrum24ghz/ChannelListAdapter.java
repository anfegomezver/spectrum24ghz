package com.spectrum24ghz;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.spectrum24ghz.databinding.ItemChannelListBinding;
import com.spectrum24ghz.models.WifiChannel;

import java.util.List;

public class ChannelListAdapter extends RecyclerView.Adapter<ChannelListAdapter.ChannelViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(WifiChannel channel);
    }

    private final List<WifiChannel> channels;
    private final OnChannelClickListener listener;

    public ChannelListAdapter(List<WifiChannel> channels, OnChannelClickListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    class ChannelViewHolder extends RecyclerView.ViewHolder {

        private final ItemChannelListBinding cb;

        ChannelViewHolder(ItemChannelListBinding binding) {
            super(binding.getRoot());
            this.cb = binding;
        }

        void bind(WifiChannel ch) {
            cb.tvChannelBadge.setText(String.valueOf(ch.getChannel()));
            cb.tvChannelTitle.setText("Canal " + ch.getChannel());
            cb.tvChannelFreq.setText(ch.getFrequencyMhz() + " MHz");

            int count = ch.getNetworks().size();
            if (count == 0) {
                cb.tvChannelNetCount.setText("Sin redes");
            } else if (count == 1) {
                cb.tvChannelNetCount.setText("1 red");
            } else {
                cb.tvChannelNetCount.setText(count + " redes");
            }

            // Saturation calculations:
            // 0 networks: 0%
            // 1 network: 20%
            // 2 networks: 45%
            // 3 networks: 75%
            // >3 networks: 100%
            int saturation = Math.min(count * 25, 100);
            cb.tvChannelSaturation.setText("Saturación: " + saturation + "%");

            // Dynamic color coding based on saturation level
            int satColorRes;
            if (saturation == 0) {
                satColorRes = R.color.sig_strong; // Green
            } else if (saturation <= 30) {
                satColorRes = R.color.sig_strong; // Green
            } else if (saturation <= 60) {
                satColorRes = R.color.sig_medium; // Yellow/Orange
            } else {
                satColorRes = R.color.sig_weak; // Red
            }

            int color = ContextCompat.getColor(cb.getRoot().getContext(), satColorRes);
            cb.tvChannelSaturation.setTextColor(color);
            cb.tvChannelBadge.setBackgroundTintList(ColorStateList.valueOf(color));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChannelClick(ch);
                }
            });
        }
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChannelListBinding binding = ItemChannelListBinding.inflate(
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
