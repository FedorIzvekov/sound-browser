package com.fedorizvekov.soundbrowser.service.analysis;

import com.fedorizvekov.soundbrowser.model.analysis.LoopMetrics;
import com.fedorizvekov.soundbrowser.model.analysis.SpectralMetrics;

public final class LoopAnalyzer {

    private static final double WAVEFORM_CONTEXT_SECONDS = 0.005;
    private static final double AMPLITUDE_WINDOW_SECONDS = 0.05;
    private static final double SPECTRAL_WINDOW_SECONDS = 0.10;

    private static final double WAVEFORM_TOLERANCE = 1.5;
    private static final double MIN_SIGNAL_SCALE = 1.0e-4;

    private final int channels;
    private final double sampleRate;

    private final int waveformContextFrames;
    private final int amplitudeWindowFrames;
    private final int spectralWindowFrames;

    private final double[][] startWaveformSamples;
    private final double[][] endWaveformSamples;

    private final double[] endAmplitudeSquares;

    private final double[][] startSpectralSamples;
    private final double[][] endSpectralSamples;

    private final double[] channelSquareSums;

    private long acceptedFrames;
    private double globalAmplitudeSquareSum;

    private int startWaveformCount;
    private int endWaveformCount;
    private int endWaveformWriteIndex;

    private double startAmplitudeSquareSum;
    private int startAmplitudeCount;
    private int endAmplitudeCount;
    private int endAmplitudeWriteIndex;
    private double endAmplitudeSquareSum;

    private int startSpectralCount;
    private int endSpectralCount;
    private int endSpectralWriteIndex;


    public LoopAnalyzer(long totalFrames, int channels, double sampleRate) {

        if (totalFrames <= 0) {
            throw new IllegalArgumentException("Total frames must be greater than zero");
        }

        if (channels <= 0) {
            throw new IllegalArgumentException("Channels must be greater than zero");
        }

        if (!Double.isFinite(sampleRate) || sampleRate <= 0.0) {
            throw new IllegalArgumentException("Sample rate must be greater than zero");
        }

        this.channels = channels;
        this.sampleRate = sampleRate;

        this.waveformContextFrames = resolveWindowFrames(totalFrames, sampleRate, WAVEFORM_CONTEXT_SECONDS);

        this.amplitudeWindowFrames = resolveWindowFrames(totalFrames, sampleRate, AMPLITUDE_WINDOW_SECONDS);

        this.spectralWindowFrames = resolveWindowFrames(totalFrames, sampleRate, SPECTRAL_WINDOW_SECONDS);

        this.startWaveformSamples = new double[channels][waveformContextFrames];
        this.endWaveformSamples = new double[channels][waveformContextFrames];

        this.endAmplitudeSquares = new double[amplitudeWindowFrames];

        this.startSpectralSamples = new double[channels][spectralWindowFrames];
        this.endSpectralSamples = new double[channels][spectralWindowFrames];

        this.channelSquareSums = new double[channels];
    }


    public void accept(double[] channelSamples, double frameMeanSquare) {

        if (channelSamples.length != channels) {
            throw new IllegalArgumentException("Channel sample count does not match configured channels");
        }

        acceptedFrames++;
        globalAmplitudeSquareSum += frameMeanSquare;

        for (var channel = 0; channel < channels; channel++) {

            var sample = channelSamples[channel];

            channelSquareSums[channel] += sample * sample;
        }

        acceptWaveformSamples(channelSamples);
        acceptAmplitude(frameMeanSquare);
        acceptSpectralSamples(channelSamples);
    }


    public LoopMetrics finish() {

        if (acceptedFrames == 0) {
            return new LoopMetrics(0.0, 0.0, 0.0);
        }

        return new LoopMetrics(calculateAmplitudeMismatch(), calculateWaveformMismatch(), calculateSpectralMismatch());
    }


    private void acceptWaveformSamples(double[] channelSamples) {

        if (startWaveformCount < waveformContextFrames) {

            for (var channel = 0; channel < channels; channel++) {
                startWaveformSamples[channel][startWaveformCount] = channelSamples[channel];
            }

            startWaveformCount++;
        }

        for (var channel = 0; channel < channels; channel++) {
            endWaveformSamples[channel][endWaveformWriteIndex] = channelSamples[channel];
        }

        if (endWaveformCount < waveformContextFrames) {
            endWaveformCount++;
        }

        endWaveformWriteIndex++;

        if (endWaveformWriteIndex == waveformContextFrames) {
            endWaveformWriteIndex = 0;
        }
    }


    private void acceptAmplitude(double amplitudeSquare) {

        if (startAmplitudeCount < amplitudeWindowFrames) {
            startAmplitudeSquareSum += amplitudeSquare;
            startAmplitudeCount++;
        }

        if (endAmplitudeCount == amplitudeWindowFrames) {
            endAmplitudeSquareSum -= endAmplitudeSquares[endAmplitudeWriteIndex];
        } else {
            endAmplitudeCount++;
        }

        endAmplitudeSquares[endAmplitudeWriteIndex] = amplitudeSquare;
        endAmplitudeSquareSum += amplitudeSquare;

        endAmplitudeWriteIndex++;

        if (endAmplitudeWriteIndex == amplitudeWindowFrames) {
            endAmplitudeWriteIndex = 0;
        }
    }


    private void acceptSpectralSamples(double[] channelSamples) {

        if (startSpectralCount < spectralWindowFrames) {

            for (var channel = 0; channel < channels; channel++) {
                startSpectralSamples[channel][startSpectralCount] = channelSamples[channel];
            }

            startSpectralCount++;
        }

        for (var channel = 0; channel < channels; channel++) {
            endSpectralSamples[channel][endSpectralWriteIndex] = channelSamples[channel];
        }

        if (endSpectralCount < spectralWindowFrames) {
            endSpectralCount++;
        }

        endSpectralWriteIndex++;

        if (endSpectralWriteIndex == spectralWindowFrames) {
            endSpectralWriteIndex = 0;
        }
    }


    private double calculateAmplitudeMismatch() {

        if (startAmplitudeCount == 0 || endAmplitudeCount == 0) {
            return 0.0;
        }

        var startRms = Math.sqrt(Math.max(0.0, startAmplitudeSquareSum) / startAmplitudeCount);

        var endRms = Math.sqrt(Math.max(0.0, endAmplitudeSquareSum) / endAmplitudeCount);

        var globalRms = Math.sqrt(Math.max(0.0, globalAmplitudeSquareSum) / acceptedFrames);

        var scale = Math.max(MIN_SIGNAL_SCALE, Math.max(globalRms, Math.max(startRms, endRms)));

        return Math.min(1.0, Math.abs(startRms - endRms) / scale);
    }


    private double calculateWaveformMismatch() {

        if (startWaveformCount == 0 || endWaveformCount == 0) {
            return 0.0;
        }

        var maximumMismatch = 0.0;

        for (var channel = 0; channel < channels; channel++) {

            var mismatch = calculateChannelWaveformMismatch(channel);

            maximumMismatch = Math.max(maximumMismatch, mismatch);
        }

        return maximumMismatch;
    }


    private double calculateChannelWaveformMismatch(int channel) {

        var firstStart = startWaveformSamples[channel][0];

        var lastEnd = endWaveformSample(channel, endWaveformCount - 1);

        var seamDelta = firstStart - lastEnd;

        var derivativeSquareSum = 0.0;
        var derivativeCount = 0;

        for (var index = 1; index < startWaveformCount; index++) {

            var difference = startWaveformSamples[channel][index] - startWaveformSamples[channel][index - 1];

            derivativeSquareSum += difference * difference;
            derivativeCount++;
        }

        for (var index = 1; index < endWaveformCount; index++) {

            var difference = endWaveformSample(channel, index) - endWaveformSample(channel, index - 1);

            derivativeSquareSum += difference * difference;
            derivativeCount++;
        }

        var derivativeRms = derivativeCount > 0 ? Math.sqrt(derivativeSquareSum / derivativeCount) : 0.0;

        var curvatureRms = calculateCurvatureRms(channel);

        var seamCurvature = calculateSeamCurvature(channel, seamDelta);

        var channelRms = Math.sqrt(channelSquareSums[channel] / acceptedFrames);

        var scale = Math.max(MIN_SIGNAL_SCALE, Math.max(channelRms, derivativeRms));

        var valueExcess = Math.max(0.0, Math.abs(seamDelta) - WAVEFORM_TOLERANCE * derivativeRms);

        var curvatureExcess = Math.max(0.0, seamCurvature - WAVEFORM_TOLERANCE * curvatureRms);

        return Math.min(1.0, Math.max(valueExcess, curvatureExcess) / scale);
    }


    private double calculateCurvatureRms(int channel) {

        var squareSum = 0.0;
        var count = 0;

        for (var index = 2; index < startWaveformCount; index++) {

            var previousDifference = startWaveformSamples[channel][index - 1] - startWaveformSamples[channel][index - 2];

            var difference = startWaveformSamples[channel][index] - startWaveformSamples[channel][index - 1];

            var curvature = difference - previousDifference;

            squareSum += curvature * curvature;
            count++;
        }

        for (var index = 2; index < endWaveformCount; index++) {

            var previousDifference = endWaveformSample(channel, index - 1) - endWaveformSample(channel, index - 2);

            var difference = endWaveformSample(channel, index) - endWaveformSample(channel, index - 1);

            var curvature = difference - previousDifference;

            squareSum += curvature * curvature;
            count++;
        }

        return count > 0 ? Math.sqrt(squareSum / count) : 0.0;
    }


    private double calculateSeamCurvature(int channel, double seamDelta) {

        var maximum = 0.0;

        if (endWaveformCount >= 2) {

            var endDifference = endWaveformSample(channel, endWaveformCount - 1) - endWaveformSample(channel, endWaveformCount - 2);

            maximum = Math.max(maximum, Math.abs(seamDelta - endDifference));
        }

        if (startWaveformCount >= 2) {

            var startDifference = startWaveformSamples[channel][1] - startWaveformSamples[channel][0];

            maximum = Math.max(maximum, Math.abs(startDifference - seamDelta));
        }

        return maximum;
    }


    private double calculateSpectralMismatch() {

        if (startSpectralCount == 0 || endSpectralCount == 0) {
            return 0.0;
        }

        var mismatchSum = 0.0;

        for (var channel = 0; channel < channels; channel++) {

            var startAnalyzer = new SpectralAnalyzer(sampleRate);
            var endAnalyzer = new SpectralAnalyzer(sampleRate);

            for (var index = 0; index < startSpectralCount; index++) {
                startAnalyzer.accept(startSpectralSamples[channel][index]);
            }

            for (var index = 0; index < endSpectralCount; index++) {
                endAnalyzer.accept(endSpectralSample(channel, index));
            }

            mismatchSum += calculateSpectralDistance(startAnalyzer.finish(), endAnalyzer.finish());
        }

        return mismatchSum / channels;
    }


    private double calculateSpectralDistance(SpectralMetrics start, SpectralMetrics end) {

        var subDifference = start.subEnergy() - end.subEnergy();

        var lowDifference = start.lowEnergy() - end.lowEnergy();

        var midDifference = start.midEnergy() - end.midEnergy();

        var highDifference = start.highEnergy() - end.highEnergy();

        var veryHighDifference = start.veryHighEnergy() - end.veryHighEnergy();

        var squaredDifferenceSum = subDifference * subDifference + lowDifference * lowDifference + midDifference * midDifference + highDifference * highDifference + veryHighDifference * veryHighDifference;

        return Math.min(1.0, Math.sqrt(squaredDifferenceSum / 2.0));
    }


    private double endWaveformSample(int channel, int logicalIndex) {

        var firstIndex = endWaveformCount == waveformContextFrames ? endWaveformWriteIndex : 0;

        var physicalIndex = (firstIndex + logicalIndex) % waveformContextFrames;

        return endWaveformSamples[channel][physicalIndex];
    }


    private double endSpectralSample(int channel, int logicalIndex) {

        var firstIndex = endSpectralCount == spectralWindowFrames ? endSpectralWriteIndex : 0;

        var physicalIndex = (firstIndex + logicalIndex) % spectralWindowFrames;

        return endSpectralSamples[channel][physicalIndex];
    }


    private int resolveWindowFrames(long totalFrames, double sampleRate, double windowSeconds) {

        var requestedFrames = Math.max(1L, Math.round(sampleRate * windowSeconds));

        var maximumFrames = Math.max(1L, totalFrames / 2L);

        return (int) Math.min(requestedFrames, maximumFrames);
    }

}