package com.fedorizvekov.soundbrowser.service.analysis;

import com.fedorizvekov.soundbrowser.model.analysis.AmplitudeMetrics;

public final class AmplitudeAnalyzer {

    private static final double RMS_VARIATION_WINDOW_SECONDS = 0.1;

    private static final double START_FRACTION = 0.2;
    private static final double END_FRACTION = 0.8;

    private final long totalFrames;
    private final int channels;
    private final int rmsVariationWindowFrames;

    private final double[] bucketSquareSums;
    private final long[] bucketSampleCounts;
    private final float[] peakEnvelope;

    private final long startFrameExclusive;
    private final long middleFrameExclusive;

    private int currentBucket;
    private long nextBucketFrame;

    private long sampleCount;
    private double squareSum;
    private double peak;
    private long peakFrameIndex;

    private int variationWindowFrameCount;
    private double variationWindowSquareSum;

    private long variationWindowCount;
    private double variationMeanRms;
    private double variationM2;

    private double startSquareSum;
    private long startSampleCount;

    private double middleSquareSum;
    private long middleSampleCount;

    private double endSquareSum;
    private long endSampleCount;


    public AmplitudeAnalyzer(long totalFrames, int channels, int envelopePoints, double sampleRate) {

        if (totalFrames <= 0) {
            throw new IllegalArgumentException("Total frames must be greater than zero");
        }

        if (channels <= 0) {
            throw new IllegalArgumentException("Channels must be greater than zero");
        }

        if (envelopePoints <= 0) {
            throw new IllegalArgumentException("Envelope points must be greater than zero");
        }

        if (!Double.isFinite(sampleRate) || sampleRate <= 0.0) {
            throw new IllegalArgumentException("Sample rate must be greater than zero");
        }

        this.totalFrames = totalFrames;
        this.channels = channels;
        this.rmsVariationWindowFrames = Math.max(1, (int) Math.round(sampleRate * RMS_VARIATION_WINDOW_SECONDS));

        var bucketCount = (int) Math.min(envelopePoints, totalFrames);

        this.bucketSquareSums = new double[bucketCount];
        this.bucketSampleCounts = new long[bucketCount];
        this.peakEnvelope = new float[bucketCount];

        this.startFrameExclusive = findFractionBoundary(START_FRACTION);
        this.middleFrameExclusive = findFractionBoundary(END_FRACTION);

        this.currentBucket = 0;
        this.nextBucketFrame = bucketCount > 1 ? calculateBucketStartFrame(1) : totalFrames;
    }


    public void accept(long frameIndex, double frameSquareSum, double framePeak) {

        updateBucket(frameIndex);

        squareSum += frameSquareSum;
        sampleCount += channels;

        if (framePeak > peak) {
            peak = framePeak;
            peakFrameIndex = frameIndex;
        }

        bucketSquareSums[currentBucket] += frameSquareSum;
        bucketSampleCounts[currentBucket] += channels;
        peakEnvelope[currentBucket] = (float) Math.max(peakEnvelope[currentBucket], framePeak);

        updateEnvelopeSummary(frameIndex, frameSquareSum);

        variationWindowSquareSum += frameSquareSum;
        variationWindowFrameCount++;

        if (variationWindowFrameCount == rmsVariationWindowFrames) {
            finishVariationWindow();
        }
    }


    public AmplitudeMetrics finish() {

        var rmsEnvelope = createRmsEnvelope();

        if (sampleCount == 0) {
            return new AmplitudeMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, rmsEnvelope, peakEnvelope);
        }

        var rms = Math.sqrt(squareSum / sampleCount);
        var crestFactor = rms > 0.0 ? peak / rms : 0.0;
        var rmsVariation = calculateRmsVariation();

        var rmsStartRatio = calculateRmsRatio(startSquareSum, startSampleCount, rms);
        var rmsMiddleRatio = calculateRmsRatio(middleSquareSum, middleSampleCount, rms);
        var rmsEndRatio = calculateRmsRatio(endSquareSum, endSampleCount, rms);
        var peakPosition = calculatePeakPosition();

        return new AmplitudeMetrics(
                peak,
                rms,
                crestFactor,
                rmsVariation,
                rmsStartRatio,
                rmsMiddleRatio,
                rmsEndRatio,
                peakPosition,
                rmsEnvelope,
                peakEnvelope
        );
    }


    private void updateBucket(long frameIndex) {

        while (frameIndex >= nextBucketFrame && currentBucket < bucketSquareSums.length - 1) {

            currentBucket++;

            nextBucketFrame = currentBucket < bucketSquareSums.length - 1 ? calculateBucketStartFrame(currentBucket + 1) : totalFrames;
        }
    }


    private long calculateBucketStartFrame(int bucket) {

        var bucketCount = bucketSquareSums.length;

        var quotient = totalFrames / bucketCount;
        var remainder = totalFrames % bucketCount;

        return (long) bucket * quotient + ((long) bucket * remainder + bucketCount - 1) / bucketCount;
    }


    private long findFractionBoundary(double fraction) {

        var low = 0L;
        var high = totalFrames;

        while (low < high) {

            var middle = low + (high - low) / 2;

            var position = (middle + 0.5) / totalFrames;

            if (position < fraction) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        return low;
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


    private void updateEnvelopeSummary(long frameIndex, double frameSquareSum) {

        if (frameIndex < startFrameExclusive) {

            startSquareSum += frameSquareSum;
            startSampleCount += channels;

        } else if (frameIndex < middleFrameExclusive) {

            middleSquareSum += frameSquareSum;
            middleSampleCount += channels;

        } else {

            endSquareSum += frameSquareSum;
            endSampleCount += channels;
        }
    }


    private double calculateRmsRatio(double segmentSquareSum, long segmentSampleCount, double rms) {

        if (segmentSampleCount == 0 || rms <= 0.0) {
            return 0.0;
        }

        var segmentRms = Math.sqrt(segmentSquareSum / segmentSampleCount);

        return segmentRms / rms;
    }


    private double calculatePeakPosition() {

        if (peak <= 0.0 || totalFrames <= 1) {
            return 0.0;
        }

        return (double) peakFrameIndex / (totalFrames - 1);
    }


    private void finishVariationWindow() {

        var windowSampleCount = (long) variationWindowFrameCount * channels;
        var windowRms = Math.sqrt(variationWindowSquareSum / windowSampleCount);

        updateVariation(windowRms);

        variationWindowFrameCount = 0;
        variationWindowSquareSum = 0.0;
    }


    private void updateVariation(double rms) {

        variationWindowCount++;

        var delta = rms - variationMeanRms;
        variationMeanRms += delta / variationWindowCount;

        var deltaAfterMeanUpdate = rms - variationMeanRms;
        variationM2 += delta * deltaAfterMeanUpdate;
    }


    private double calculateRmsVariation() {

        var windowCount = variationWindowCount;
        var meanRms = variationMeanRms;
        var m2 = variationM2;

        if (variationWindowFrameCount > 0) {

            var windowSampleCount = (long) variationWindowFrameCount * channels;
            var windowRms = Math.sqrt(variationWindowSquareSum / windowSampleCount);

            windowCount++;

            var delta = windowRms - meanRms;
            meanRms += delta / windowCount;

            var deltaAfterMeanUpdate = windowRms - meanRms;
            m2 += delta * deltaAfterMeanUpdate;
        }

        if (windowCount == 0 || meanRms <= 0.0) {
            return 0.0;
        }

        var standardDeviation = Math.sqrt(m2 / windowCount);

        return standardDeviation / meanRms;
    }

}