package com.fedorizvekov.soundbrowser.service.analysis;

import java.util.ArrayList;
import java.util.List;

import com.fedorizvekov.soundbrowser.model.analysis.RhythmMetrics;

public final class MusicRhythmAnalyzer {

    private static final double WINDOW_SECONDS = 0.01;

    private static final double MIN_TEMPO_BPM = 50.0;
    private static final double MAX_TEMPO_BPM = 200.0;

    private static final double ONSET_THRESHOLD_STD_FACTOR = 0.5;

    private static final int MIN_TEMPO_ONSETS = 4;
    private static final double MIN_TEMPO_CORRELATION = 0.3;
    private static final double MIN_TEMPO_GRID_SUPPORT = 0.35;
    private static final double TEMPO_GRID_TOLERANCE_SECONDS = 0.02;

    private final double sampleRate;
    private final int windowFrames;
    private final List<Double> windowRms = new ArrayList<>();

    private long totalFrames;
    private int currentWindowFrames;
    private double currentWindowSquareSum;


    public MusicRhythmAnalyzer(double sampleRate) {

        if (!Double.isFinite(sampleRate) || sampleRate <= 0.0) {
            throw new IllegalArgumentException("Sample rate must be greater than zero");
        }

        this.sampleRate = sampleRate;
        this.windowFrames = Math.max(1, (int) Math.round(sampleRate * WINDOW_SECONDS));
    }


    public void accept(double frameAmplitude) {

        currentWindowSquareSum += frameAmplitude * frameAmplitude;
        currentWindowFrames++;
        totalFrames++;

        if (currentWindowFrames == windowFrames) {
            flushWindow();
        }
    }


    public RhythmMetrics finish() {

        if (currentWindowFrames > 0) {
            flushWindow();
        }

        if (windowRms.isEmpty() || totalFrames == 0) {
            return new RhythmMetrics(0.0, 0.0, 0.0);
        }

        var energyVariation = calculateEnergyVariation();
        var onsetEnvelope = createOnsetEnvelope();
        var detectedOnsets = detectOnsets(onsetEnvelope);

        var onsetRate = calculateOnsetRate(detectedOnsets);
        var tempoBpm = estimateTempo(detectedOnsets);

        return new RhythmMetrics(tempoBpm, onsetRate, energyVariation);
    }


    private void flushWindow() {

        windowRms.add(Math.sqrt(currentWindowSquareSum / currentWindowFrames));

        currentWindowSquareSum = 0.0;
        currentWindowFrames = 0;
    }


    private double calculateEnergyVariation() {

        var mean = windowRms.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        if (mean <= 0.0) {
            return 0.0;
        }

        var variance = 0.0;

        for (var value : windowRms) {
            var difference = value - mean;
            variance += difference * difference;
        }

        variance /= windowRms.size();

        return Math.sqrt(variance) / mean;
    }


    private double[] createOnsetEnvelope() {

        var onsetEnvelope = new double[windowRms.size()];

        if (windowRms.isEmpty()) {
            return onsetEnvelope;
        }

        onsetEnvelope[0] = windowRms.get(0);

        for (var index = 1; index < windowRms.size(); index++) {
            onsetEnvelope[index] = Math.max(0.0, windowRms.get(index) - windowRms.get(index - 1));
        }

        return onsetEnvelope;
    }


    private double[] detectOnsets(double[] onsetEnvelope) {

        var detectedOnsets = new double[onsetEnvelope.length];

        if (onsetEnvelope.length == 0) {
            return detectedOnsets;
        }

        var mean = mean(onsetEnvelope);
        var standardDeviation = standardDeviation(onsetEnvelope, mean);
        var threshold = mean + standardDeviation * ONSET_THRESHOLD_STD_FACTOR;

        for (var index = 0; index < onsetEnvelope.length; index++) {

            var value = onsetEnvelope[index];
            var left = index > 0 ? onsetEnvelope[index - 1] : 0.0;
            var right = index + 1 < onsetEnvelope.length ? onsetEnvelope[index + 1] : 0.0;

            if (value > threshold && value >= left && value > right) {
                detectedOnsets[index] = value;
            }
        }

        return detectedOnsets;
    }


    private double calculateOnsetRate(double[] detectedOnsets) {

        var onsetCount = countOnsets(detectedOnsets);

        if (onsetCount < 2) {
            return 0.0;
        }

        var durationSeconds = totalFrames / sampleRate;

        return durationSeconds > 0.0 ? onsetCount / durationSeconds : 0.0;
    }


    private double estimateTempo(double[] detectedOnsets) {

        if (countOnsets(detectedOnsets) < MIN_TEMPO_ONSETS) {
            return 0.0;
        }

        var minLag = Math.max(1, (int) Math.round(60.0 / (MAX_TEMPO_BPM * WINDOW_SECONDS)));

        var maxLag = Math.min(detectedOnsets.length - 1, (int) Math.round(60.0 / (MIN_TEMPO_BPM * WINDOW_SECONDS)));

        if (minLag > maxLag) {
            return 0.0;
        }

        var toleranceWindows = Math.max(1, (int) Math.round(TEMPO_GRID_TOLERANCE_SECONDS / WINDOW_SECONDS));

        var bestLag = 0;
        var bestScore = 0.0;

        for (var lag = minLag; lag <= maxLag; lag++) {

            var correlation = calculateOnsetCorrelation(detectedOnsets, lag);

            if (correlation < MIN_TEMPO_CORRELATION) {
                continue;
            }

            var gridSupport = calculateGridSupport(detectedOnsets, lag, toleranceWindows);

            if (gridSupport < MIN_TEMPO_GRID_SUPPORT) {
                continue;
            }

            var score = correlation * gridSupport;

            if (score > bestScore
                    || (Double.compare(score, bestScore) == 0 && (bestLag == 0
                    || lag < bestLag))) {

                bestScore = score;
                bestLag = lag;
            }
        }

        return bestLag > 0 ? 60.0 / (bestLag * WINDOW_SECONDS) : 0.0;
    }


    private double calculateOnsetCorrelation(double[] detectedOnsets, int lag) {

        var count = detectedOnsets.length - lag;

        if (count < 2) {
            return 0.0;
        }

        var leftMean = 0.0;
        var rightMean = 0.0;

        for (var index = lag; index < detectedOnsets.length; index++) {
            leftMean += isOnset(detectedOnsets[index]) ? 1.0 : 0.0;
            rightMean += isOnset(detectedOnsets[index - lag]) ? 1.0 : 0.0;
        }

        leftMean /= count;
        rightMean /= count;

        var covariance = 0.0;
        var leftVariance = 0.0;
        var rightVariance = 0.0;

        for (var index = lag; index < detectedOnsets.length; index++) {

            var left = (isOnset(detectedOnsets[index]) ? 1.0 : 0.0) - leftMean;
            var right = (isOnset(detectedOnsets[index - lag]) ? 1.0 : 0.0) - rightMean;

            covariance += left * right;
            leftVariance += left * left;
            rightVariance += right * right;
        }

        var denominator = Math.sqrt(leftVariance * rightVariance);

        return denominator > 0.0 ? covariance / denominator : 0.0;
    }


    private double calculateGridSupport(double[] detectedOnsets, int lag, int toleranceWindows) {

        var totalStrength = 0.0;

        for (var onset : detectedOnsets) {
            totalStrength += onset;
        }

        if (totalStrength <= 0.0) {
            return 0.0;
        }

        var bestMatchedStrength = 0.0;

        for (var phase = 0; phase < lag; phase++) {

            var matchedStrength = 0.0;

            for (var index = 0; index < detectedOnsets.length; index++) {

                var strength = detectedOnsets[index];

                if (!isOnset(strength)) {
                    continue;
                }

                var remainder = index % lag;
                var distance = Math.abs(remainder - phase);
                distance = Math.min(distance, lag - distance);

                if (distance <= toleranceWindows) {
                    matchedStrength += strength;
                }
            }

            bestMatchedStrength = Math.max(bestMatchedStrength, matchedStrength);
        }

        return bestMatchedStrength / totalStrength;
    }


    private int countOnsets(double[] detectedOnsets) {

        var count = 0;

        for (var onset : detectedOnsets) {

            if (isOnset(onset)) {
                count++;
            }
        }

        return count;
    }


    private boolean isOnset(double value) {
        return value > 0.0;
    }


    private double mean(double[] values) {

        var sum = 0.0;

        for (var value : values) {
            sum += value;
        }

        return values.length > 0 ? sum / values.length : 0.0;
    }


    private double standardDeviation(double[] values, double mean) {

        var variance = 0.0;

        for (var value : values) {
            var difference = value - mean;
            variance += difference * difference;
        }

        return values.length > 0 ? Math.sqrt(variance / values.length) : 0.0;
    }

}