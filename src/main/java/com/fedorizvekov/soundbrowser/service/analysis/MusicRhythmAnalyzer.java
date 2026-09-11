package com.fedorizvekov.soundbrowser.service.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fedorizvekov.soundbrowser.model.analysis.RhythmMetrics;
import com.fedorizvekov.soundbrowser.model.analysis.TempoStability;

public final class MusicRhythmAnalyzer {

    private static final double WINDOW_SECONDS = 0.01;
    private static final double RHYTHM_WINDOW_SECONDS = 0.10;
    private static final double ONSET_THRESHOLD_WINDOW_SECONDS = 2.0;

    private static final double MIN_TEMPO_BPM = 50.0;
    private static final double MAX_TEMPO_BPM = 200.0;

    private static final double ONSET_THRESHOLD_STD_FACTOR = 0.5;
    private static final double MIN_ONSET_STRENGTH_RATIO = 0.004;

    private static final double TEMPO_TOLERANCE_SECONDS = 0.03;
    private static final double MIN_TEMPO_CORRELATION = 0.25;

    private static final double MIN_STABLE_TEMPO_CORRELATION = 0.20;
    private static final double MIN_STABLE_SEGMENT_CORRELATION = 0.20;
    private static final double MAX_STABLE_SEGMENT_STD_DEV = 0.085;

    private static final double SECOND_HARMONIC_WEIGHT = 0.5;

    private static final double OCTAVE_PREFERENCE_RATIO = 0.95;
    private static final double OCTAVE_MIN_CORRELATION_RATIO = 0.70;

    private static final double THIRD_METER_MIN_BEAT_COVERAGE = 0.62;
    private static final double THIRD_METER_CORRELATION_RATIO = 1.25;

    private static final double TEMPO_ONSET_REFERENCE_PERCENTILE = 0.90;

    private static final double TEMPO_STABILITY_SEGMENT_SECONDS = 15.0;
    private static final double MIN_TEMPO_STABILITY_SEGMENT_SECONDS = 10.0;

    private static final int MIN_TEMPO_ONSETS = 4;
    private static final int MIN_STABLE_SEGMENTS = 4;

    private static final int ONSET_SMOOTHING_WINDOWS = 3;

    private static final int RHYTHM_WINDOW_COUNT = Math.max(1, (int) Math.round(RHYTHM_WINDOW_SECONDS / WINDOW_SECONDS));

    private static final int ONSET_THRESHOLD_RADIUS = Math.max(1, (int) Math.round(ONSET_THRESHOLD_WINDOW_SECONDS / RHYTHM_WINDOW_SECONDS / 2.0));

    private static final int TEMPO_TOLERANCE_WINDOWS = Math.max(1, (int) Math.round(TEMPO_TOLERANCE_SECONDS / WINDOW_SECONDS));

    private static final int TEMPO_STABILITY_SEGMENT_WINDOWS = Math.max(1, (int) Math.round(TEMPO_STABILITY_SEGMENT_SECONDS / WINDOW_SECONDS));

    private static final int MIN_TEMPO_STABILITY_SEGMENT_WINDOWS = Math.max(1, (int) Math.round(MIN_TEMPO_STABILITY_SEGMENT_SECONDS / WINDOW_SECONDS));

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

        var meanRms = calculateMeanRms();
        var energyVariation = calculateEnergyVariation(meanRms);

        var fineOnsetEnvelope = createFineOnsetEnvelope();
        var rhythmOnsetEnvelope = createRhythmOnsetEnvelope(fineOnsetEnvelope);

        var detectedOnsets = detectOnsets(rhythmOnsetEnvelope, fineOnsetEnvelope, meanRms);

        var onsetRate = calculateOnsetRate(detectedOnsets);
        var tempoBpm = estimateTempo(detectedOnsets, onsetRate);

        return new RhythmMetrics(tempoBpm, onsetRate, energyVariation);
    }


    private void flushWindow() {

        windowRms.add(Math.sqrt(currentWindowSquareSum / currentWindowFrames));

        currentWindowSquareSum = 0.0;
        currentWindowFrames = 0;
    }


    private double calculateMeanRms() {

        return windowRms.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }


    private double calculateEnergyVariation(double meanRms) {

        if (meanRms <= 0.0) {
            return 0.0;
        }

        var variance = 0.0;

        for (var value : windowRms) {

            var difference = value - meanRms;
            variance += difference * difference;
        }

        variance /= windowRms.size();

        return Math.sqrt(variance) / meanRms;
    }


    private double[] createFineOnsetEnvelope() {

        var smoothedRms = createSmoothedRms();

        var envelope = new double[smoothedRms.length];

        if (smoothedRms.length == 0) {
            return envelope;
        }

        envelope[0] = smoothedRms[0];

        for (var index = 1; index < smoothedRms.length; index++) {

            envelope[index] = Math.max(0.0, smoothedRms[index] - smoothedRms[index - 1]);
        }

        return envelope;
    }


    private double[] createSmoothedRms() {

        var result = new double[windowRms.size()];

        var radius = ONSET_SMOOTHING_WINDOWS / 2;

        for (var index = 0; index < windowRms.size(); index++) {

            var from = Math.max(0, index - radius);

            var to = Math.min(windowRms.size(), index + radius + 1);

            var squareSum = 0.0;

            for (var sample = from; sample < to; sample++) {

                var rms = windowRms.get(sample);

                squareSum += rms * rms;
            }

            result[index] = Math.sqrt(squareSum / (to - from));
        }

        return result;
    }


    private double[] createRhythmOnsetEnvelope(double[] fineOnsetEnvelope) {

        var size = (fineOnsetEnvelope.length + RHYTHM_WINDOW_COUNT - 1) / RHYTHM_WINDOW_COUNT;

        var envelope = new double[size];

        for (var block = 0; block < size; block++) {

            var from = block * RHYTHM_WINDOW_COUNT;

            var to = Math.min(from + RHYTHM_WINDOW_COUNT, fineOnsetEnvelope.length);

            var peak = 0.0;

            for (var index = from; index < to; index++) {

                peak = Math.max(peak, fineOnsetEnvelope[index]);
            }

            envelope[block] = peak;
        }

        return envelope;
    }


    private double[] detectOnsets(double[] rhythmOnsetEnvelope, double[] fineOnsetEnvelope, double meanRms) {

        var detectedOnsets = new double[windowRms.size()];

        var minimumStrength = meanRms * MIN_ONSET_STRENGTH_RATIO;

        for (var block = 0; block < rhythmOnsetEnvelope.length; block++) {

            var value = rhythmOnsetEnvelope[block];

            if (value < minimumStrength) {
                continue;
            }

            var left = block > 0 ? rhythmOnsetEnvelope[block - 1] : 0.0;

            var right = block + 1 < rhythmOnsetEnvelope.length ? rhythmOnsetEnvelope[block + 1] : 0.0;

            if (value < left || value <= right) {
                continue;
            }

            var threshold = calculateLocalOnsetThreshold(rhythmOnsetEnvelope, block);

            if (value <= threshold) {
                continue;
            }

            var from = block * RHYTHM_WINDOW_COUNT;

            var to = Math.min(from + RHYTHM_WINDOW_COUNT, fineOnsetEnvelope.length);

            var bestIndex = from;
            var bestValue = fineOnsetEnvelope[from];

            for (var index = from + 1; index < to; index++) {

                if (fineOnsetEnvelope[index] > bestValue) {

                    bestValue = fineOnsetEnvelope[index];
                    bestIndex = index;
                }
            }

            detectedOnsets[bestIndex] = value;
        }

        return detectedOnsets;
    }


    private double calculateLocalOnsetThreshold(double[] envelope, int center) {

        var from = Math.max(0, center - ONSET_THRESHOLD_RADIUS);

        var to = Math.min(envelope.length, center + ONSET_THRESHOLD_RADIUS + 1);

        var count = 0;
        var sum = 0.0;

        for (var index = from; index < to; index++) {

            if (index == center) {
                continue;
            }

            sum += envelope[index];
            count++;
        }

        if (count == 0) {
            return 0.0;
        }

        var mean = sum / count;

        var variance = 0.0;

        for (var index = from; index < to; index++) {

            if (index == center) {
                continue;
            }

            var difference = envelope[index] - mean;

            variance += difference * difference;
        }

        var standardDeviation = Math.sqrt(variance / count);

        return mean + standardDeviation * ONSET_THRESHOLD_STD_FACTOR;
    }


    private double calculateOnsetRate(double[] detectedOnsets) {

        var onsetCount = countOnsets(detectedOnsets);

        if (onsetCount < 2) {
            return 0.0;
        }

        var durationSeconds = totalFrames / sampleRate;

        return durationSeconds > 0.0 ? onsetCount / durationSeconds : 0.0;
    }


    private double estimateTempo(double[] detectedOnsets, double onsetRate) {

        if (countOnsets(detectedOnsets) < MIN_TEMPO_ONSETS) {
            return 0.0;
        }

        return estimateSparseTempo(detectedOnsets, onsetRate);
    }


    private double estimateSparseTempo(double[] detectedOnsets, double onsetRate) {

        var tempoEnvelope = createTempoEnvelope(detectedOnsets);

        var minLag = calculateMinLag();

        var maxLag = calculateMaxLag(tempoEnvelope.length);

        if (minLag > maxLag) {
            return 0.0;
        }

        var correlationMaxLag = Math.min(tempoEnvelope.length - 1, maxLag * 2);

        var correlations = new double[correlationMaxLag + 1];

        var scores = new double[maxLag + 1];

        for (var lag = minLag; lag <= correlationMaxLag; lag++) {

            correlations[lag] = calculateCorrelation(tempoEnvelope, lag);
        }

        var bestLag = 0;
        var bestScore = 0.0;

        for (var lag = minLag; lag <= maxLag; lag++) {

            var correlation = correlations[lag];

            var scoringSecondHarmonicCorrelation = lag * 2 <= maxLag ? correlations[lag * 2] : 0.0;

            var score = correlation + SECOND_HARMONIC_WEIGHT * Math.max(0.0, scoringSecondHarmonicCorrelation);

            scores[lag] = score;

            var stability = calculateTempoStability(detectedOnsets, tempoEnvelope, lag);

            var accepted = correlation >= MIN_TEMPO_CORRELATION || isStableTempoCandidate(correlation, stability);

            if (!accepted) {
                continue;
            }

            if (score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }

        if (bestLag == 0) {
            return 0.0;
        }

        var afterOctaveLag = preferFasterOctave(bestLag, bestScore, correlations, scores, minLag, maxLag);

        var afterThirdMeterLag = preferSlowerThirdMeter(afterOctaveLag, correlations, onsetRate, minLag, maxLag);

        var refinedLag = refineLag(afterThirdMeterLag, correlations, minLag, maxLag);

        return lagToBpm(refinedLag);
    }


    private double refineLag(int lag, double[] correlations, int minLag, int maxLag) {

        if (lag <= minLag || lag >= maxLag || lag >= correlations.length - 1) {
            return lag;
        }

        var left = correlations[lag - 1];
        var center = correlations[lag];
        var right = correlations[lag + 1];

        if (!Double.isFinite(left) || !Double.isFinite(center) || !Double.isFinite(right)) {
            return lag;
        }

        if (center < left || center < right) {
            return lag;
        }

        var denominator = left - 2.0 * center + right;

        if (denominator >= 0.0 || Math.abs(denominator) < 1.0e-12) {
            return lag;
        }

        var offset = 0.5 * (left - right) / denominator;

        if (!Double.isFinite(offset)) {
            return lag;
        }

        offset = Math.max(-0.5, Math.min(0.5, offset));

        return lag + offset;
    }


    private int calculateMinLag() {

        return Math.max(1, (int) Math.round(60.0 / (MAX_TEMPO_BPM * WINDOW_SECONDS)));
    }


    private int calculateMaxLag(int envelopeLength) {

        return Math.min(envelopeLength - 1, (int) Math.round(60.0 / (MIN_TEMPO_BPM * WINDOW_SECONDS)));
    }


    private boolean isStableTempoCandidate(double correlation, TempoStability stability) {

        return correlation >= MIN_STABLE_TEMPO_CORRELATION
                && stability.meanCorrelation() >= MIN_STABLE_SEGMENT_CORRELATION
                && stability.standardDeviation() <= MAX_STABLE_SEGMENT_STD_DEV
                && stability.segmentCount() >= MIN_STABLE_SEGMENTS;
    }


    private TempoStability calculateTempoStability(double[] detectedOnsets, double[] tempoEnvelope, int lag) {

        var segmentCorrelations = new ArrayList<Double>();

        for (var from = 0; from < tempoEnvelope.length; from += TEMPO_STABILITY_SEGMENT_WINDOWS) {

            var to = Math.min(from + TEMPO_STABILITY_SEGMENT_WINDOWS, tempoEnvelope.length);

            if (to - from < MIN_TEMPO_STABILITY_SEGMENT_WINDOWS) {
                continue;
            }

            if (countOnsets(detectedOnsets, from, to) < MIN_TEMPO_ONSETS) {
                continue;
            }

            var correlation = calculateCorrelation(tempoEnvelope, from, to, lag);

            segmentCorrelations.add(correlation);
        }

        return calculateTempoStability(segmentCorrelations);
    }


    private TempoStability calculateTempoStability(List<Double> segmentCorrelations) {

        if (segmentCorrelations.isEmpty()) {
            return TempoStability.EMPTY;
        }

        var mean = segmentCorrelations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        var variance = 0.0;

        for (var correlation : segmentCorrelations) {

            var difference = correlation - mean;
            variance += difference * difference;
        }

        variance /= segmentCorrelations.size();

        return new TempoStability(mean, Math.sqrt(variance), segmentCorrelations.size());
    }


    private int preferFasterOctave(int bestLag, double bestScore, double[] correlations, double[] scores, int minLag, int maxLag) {

        var targetLag = bestLag / 2.0;

        if (targetLag < minLag) {
            return bestLag;
        }

        var from = Math.max(minLag, (int) Math.floor(targetLag) - TEMPO_TOLERANCE_WINDOWS);

        var to = Math.min(maxLag, (int) Math.ceil(targetLag) + TEMPO_TOLERANCE_WINDOWS);

        var candidateLag = 0;
        var candidateScore = 0.0;

        for (var lag = from; lag <= to; lag++) {

            if (correlations[lag] < MIN_TEMPO_CORRELATION) {
                continue;
            }

            if (correlations[lag] < correlations[bestLag] * OCTAVE_MIN_CORRELATION_RATIO) {
                continue;
            }

            if (scores[lag] < bestScore * OCTAVE_PREFERENCE_RATIO) {
                continue;
            }

            if (scores[lag] > candidateScore) {

                candidateScore = scores[lag];
                candidateLag = lag;
            }
        }

        return candidateLag > 0 ? candidateLag : bestLag;
    }


    private int preferSlowerThirdMeter(int bestLag, double[] correlations, double onsetRate, int minLag, int maxLag) {

        var bpm = lagToBpm(bestLag);

        var beatRate = bpm / 60.0;

        if (beatRate <= 0.0) {
            return bestLag;
        }

        var beatCoverage = onsetRate / beatRate;

        if (beatCoverage >= THIRD_METER_MIN_BEAT_COVERAGE) {
            return bestLag;
        }

        var targetLag = bestLag * 3.0;

        if (targetLag > maxLag) {
            return bestLag;
        }

        var from = Math.max(minLag, (int) Math.floor(targetLag) - TEMPO_TOLERANCE_WINDOWS);

        var to = Math.min(maxLag, (int) Math.ceil(targetLag) + TEMPO_TOLERANCE_WINDOWS);

        var candidateLag = 0;
        var candidateCorrelation = 0.0;

        for (var lag = from; lag <= to; lag++) {

            var correlation = correlations[lag];

            if (correlation < MIN_TEMPO_CORRELATION) {
                continue;
            }

            if (correlation < correlations[bestLag] * THIRD_METER_CORRELATION_RATIO) {
                continue;
            }

            if (correlation > candidateCorrelation) {

                candidateCorrelation = correlation;

                candidateLag = lag;
            }
        }

        return candidateLag > 0 ? candidateLag : bestLag;
    }


    private double[] createTempoEnvelope(double[] detectedOnsets) {

        var envelope = new double[detectedOnsets.length];

        var referenceStrength = calculateTempoOnsetReference(detectedOnsets);

        if (referenceStrength <= 0.0) {
            return envelope;
        }

        for (var index = 0; index < detectedOnsets.length; index++) {

            var onsetStrength = detectedOnsets[index];

            if (!isOnset(onsetStrength)) {
                continue;
            }

            var normalizedStrength = Math.min(1.0, onsetStrength / referenceStrength);

            var onsetWeight = Math.sqrt(normalizedStrength);

            for (var offset = -TEMPO_TOLERANCE_WINDOWS; offset <= TEMPO_TOLERANCE_WINDOWS; offset++) {

                var target = index + offset;

                if (target < 0 || target >= envelope.length) {
                    continue;
                }

                var toleranceWeight = 1.0 - Math.abs(offset) / (double) (TEMPO_TOLERANCE_WINDOWS + 1);

                envelope[target] = Math.max(envelope[target], onsetWeight * toleranceWeight);
            }
        }

        return envelope;
    }


    private double calculateTempoOnsetReference(double[] detectedOnsets) {

        return calculatePercentileReference(detectedOnsets, TEMPO_ONSET_REFERENCE_PERCENTILE);
    }


    private double calculatePercentileReference(double[] values, double percentile) {

        var positiveValues = Arrays.stream(values).filter(value -> value > 0.0).sorted().toArray();

        if (positiveValues.length == 0) {
            return 0.0;
        }

        var index = (int) Math.round((positiveValues.length - 1) * percentile);

        return positiveValues[index];
    }


    private double lagToBpm(double lag) {

        return 60.0 / (lag * WINDOW_SECONDS);
    }


    private double calculateCorrelation(double[] values, int lag) {

        return calculateCorrelation(values, 0, values.length, lag);
    }


    private double calculateCorrelation(double[] values, int from, int to, int lag) {

        var count = to - from - lag;

        if (count < 2) {
            return 0.0;
        }

        var leftMean = 0.0;
        var rightMean = 0.0;

        for (var index = from + lag; index < to; index++) {

            leftMean += values[index];
            rightMean += values[index - lag];
        }

        leftMean /= count;
        rightMean /= count;

        var covariance = 0.0;
        var leftVariance = 0.0;
        var rightVariance = 0.0;

        for (var index = from + lag; index < to; index++) {

            var left = values[index] - leftMean;

            var right = values[index - lag] - rightMean;

            covariance += left * right;

            leftVariance += left * left;

            rightVariance += right * right;
        }

        var denominator = Math.sqrt(leftVariance * rightVariance);

        return denominator > 0.0 ? covariance / denominator : 0.0;
    }


    private int countOnsets(double[] detectedOnsets) {

        return countOnsets(detectedOnsets, 0, detectedOnsets.length);
    }


    private int countOnsets(double[] detectedOnsets, int from, int to) {

        var count = 0;

        for (var index = from; index < to; index++) {

            if (isOnset(detectedOnsets[index])) {
                count++;
            }
        }

        return count;
    }


    private boolean isOnset(double value) {

        return value > 0.0;
    }

}