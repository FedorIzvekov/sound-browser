package com.fedorizvekov.soundbrowser.service.analysis;

import com.fedorizvekov.soundbrowser.model.analysis.AmplitudeMetrics;

public final class AmplitudeAnalyzer {

    private final long totalFrames;
    private final int channels;

    private final double[] bucketSquareSums;
    private final long[] bucketSampleCounts;
    private final float[] peakEnvelope;

    private long sampleCount;
    private double squareSum;
    private double peak;


    public AmplitudeAnalyzer(long totalFrames, int channels, int envelopePoints) {

        if (totalFrames <= 0) {
            throw new IllegalArgumentException("Total frames must be greater than zero");
        }

        if (channels <= 0) {
            throw new IllegalArgumentException("Channels must be greater than zero");
        }

        if (envelopePoints <= 0) {
            throw new IllegalArgumentException("Envelope points must be greater than zero");
        }

        this.totalFrames = totalFrames;
        this.channels = channels;

        var bucketCount = (int) Math.min(envelopePoints, totalFrames);

        this.bucketSquareSums = new double[bucketCount];
        this.bucketSampleCounts = new long[bucketCount];
        this.peakEnvelope = new float[bucketCount];
    }


    public void accept(long frameIndex, double frameSquareSum, double framePeak) {

        var bucket = (int) (frameIndex * bucketSquareSums.length / totalFrames);

        squareSum += frameSquareSum;
        sampleCount += channels;
        peak = Math.max(peak, framePeak);

        bucketSquareSums[bucket] += frameSquareSum;
        bucketSampleCounts[bucket] += channels;
        peakEnvelope[bucket] = (float) Math.max(peakEnvelope[bucket], framePeak);
    }


    public AmplitudeMetrics finish() {

        var rmsEnvelope = createRmsEnvelope();

        if (sampleCount == 0) {
            return new AmplitudeMetrics(0.0, 0.0, 0.0, rmsEnvelope, peakEnvelope);
        }

        var rms = Math.sqrt(squareSum / sampleCount);
        var crestFactor = rms > 0.0 ? peak / rms : 0.0;

        return new AmplitudeMetrics(peak, rms, crestFactor, rmsEnvelope, peakEnvelope);
    }


    private float[] createRmsEnvelope() {

        var envelope = new float[bucketSquareSums.length];

        for (var index = 0; index < envelope.length; index++) {

            if (bucketSampleCounts[index] > 0) {
                envelope[index] = (float) Math.sqrt(bucketSquareSums[index] / bucketSampleCounts[index]);
            }
        }

        return envelope;
    }

}