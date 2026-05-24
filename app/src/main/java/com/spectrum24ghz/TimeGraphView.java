package com.spectrum24ghz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.spectrum24ghz.models.ScannedNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TimeGraphView extends View {

    private final List<List<ScannedNetwork>> history = new ArrayList<>();
    
    private Paint gridPaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint legendPaint;
    
    private final int[] colors = {
            Color.parseColor("#00E5FF"), // Cyan
            Color.parseColor("#00E676"), // Green
            Color.parseColor("#FFC107"), // Yellow
            Color.parseColor("#D500F9"), // Purple
            Color.parseColor("#FF5252"), // Red
            Color.parseColor("#FF9100"), // Orange
            Color.parseColor("#448AFF")  // Blue
    };

    public TimeGraphView(Context context) {
        super(context);
        init();
    }

    public TimeGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimeGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#E0E0E0"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.5f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#757575"));
        textPaint.setTextSize(26);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4.5f);

        legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendPaint.setStyle(Paint.Style.FILL);
    }

    public void updateHistory(List<List<ScannedNetwork>> newHistory) {
        this.history.clear();
        this.history.addAll(newHistory);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Padding
        int paddingLeft = 100;
        int paddingRight = 60;
        int paddingTop = 80;
        int paddingBottom = 100;

        int graphWidth = width - paddingLeft - paddingRight;
        int graphHeight = height - paddingTop - paddingBottom;

        // Draw horizontal grid lines (Y-axis: signal from -100 to -20 dBm)
        int minDbm = -100;
        int maxDbm = -20;
        int dbmStep = 10;
        
        for (int dbm = minDbm; dbm <= maxDbm; dbm += dbmStep) {
            float y = paddingTop + graphHeight * (1 - (float)(dbm - minDbm) / (maxDbm - minDbm));
            
            gridPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);
            
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(dbm + "", paddingLeft - 15, y + 8, textPaint);
        }

        // Draw vertical grid lines representing the sliding window of latest N cycles
        int maxCycles = 10; // Only display last 10 points on screen to prevent clutter
        int historySize = history.size();
        
        int startIndex = 0;
        int pointsCount = historySize;
        if (historySize > maxCycles) {
            startIndex = historySize - maxCycles;
            pointsCount = maxCycles;
        } else {
            pointsCount = Math.max(maxCycles, historySize);
        }

        for (int step = 0; step < pointsCount; step++) {
            float x = paddingLeft + graphWidth * (float)step / (pointsCount - 1);
            
            gridPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
            canvas.drawLine(x, paddingTop, x, height - paddingBottom, gridPaint);
            
            // X-axis label displays the sliding scan number index
            int labelIndex = step + 1 + startIndex;
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(labelIndex + "", x, height - paddingBottom + 35, textPaint);
        }

        // Label axis title
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.parseColor("#333333"));
        textPaint.setTextSize(28);
        canvas.drawText("Scan Count", paddingLeft + graphWidth / 2f, height - paddingBottom + 75, textPaint);

        if (historySize == 0) return;

        // Group history by network
        Map<String, List<Integer>> networkData = new LinkedHashMap<>();
        Map<String, String> networkNames = new HashMap<>(); // BSSID -> SSID + CH

        for (int cycle = 0; cycle < historySize; cycle++) {
            List<ScannedNetwork> scan = history.get(cycle);
            Map<String, ScannedNetwork> scanMap = new HashMap<>();
            for (ScannedNetwork net : scan) {
                scanMap.put(net.getBssid(), net);
                networkNames.put(net.getBssid(), net.getSsid() + " " + net.getChannel());
            }

            for (String bssid : networkNames.keySet()) {
                if (!networkData.containsKey(bssid)) {
                    List<Integer> list = new ArrayList<>();
                    for (int k = 0; k < cycle; k++) list.add(-100);
                    networkData.put(bssid, list);
                }
                
                ScannedNetwork net = scanMap.get(bssid);
                if (net != null) {
                    networkData.get(bssid).add(net.getRssi());
                } else {
                    networkData.get(bssid).add(-100);
                }
            }
        }

        // Draw line graph for each active network inside sliding window range
        int index = 0;
        List<String> visibleLegend = new ArrayList<>();
        List<Integer> legendColors = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : networkData.entrySet()) {
            String bssid = entry.getKey();
            List<Integer> readings = entry.getValue();
            String label = networkNames.get(bssid);

            int color = colors[index % colors.length];
            linePaint.setColor(color);

            Path linePath = new Path();
            boolean firstPoint = true;

            for (int step = 0; step < pointsCount; step++) {
                int cycleIndex = startIndex + step;
                if (cycleIndex >= readings.size()) break;
                
                int rssi = readings.get(cycleIndex);
                float x = paddingLeft + graphWidth * (float)step / (pointsCount - 1);
                float y = paddingTop + graphHeight * (1 - (float)(rssi - minDbm) / (maxDbm - minDbm));

                if (firstPoint) {
                    linePath.moveTo(x, y);
                    firstPoint = false;
                } else {
                    linePath.lineTo(x, y);
                }
            }

            canvas.drawPath(linePath, linePaint);

            // Record legend
            if (index < 6) {
                visibleLegend.add(label);
                legendColors.add(color);
            }
            index++;
        }

        // Draw Legend Overlay box at upper left
        if (!visibleLegend.isEmpty()) {
            float boxLeft = paddingLeft + 30;
            float boxTop = paddingTop + 30;
            float boxRight = boxLeft + 320;
            float boxBottom = boxTop + (visibleLegend.size() * 32) + 20;

            legendPaint.setColor(Color.parseColor("#CC2E2E2E"));
            canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, 12, 12, legendPaint);

            for (int k = 0; k < visibleLegend.size(); k++) {
                float yItem = boxTop + 24 + (k * 32);
                
                legendPaint.setColor(legendColors.get(k));
                canvas.drawRect(boxLeft + 15, yItem - 14, boxLeft + 29, yItem, legendPaint);
                
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(20);
                textPaint.setTextAlign(Paint.Align.LEFT);
                String labelText = visibleLegend.get(k);
                if (labelText.length() > 22) labelText = labelText.substring(0, 20) + "...";
                canvas.drawText(labelText, boxLeft + 42, yItem - 1, textPaint);
            }
        }

        textPaint.setColor(Color.parseColor("#757575"));
        textPaint.setTextSize(26);
    }
}
