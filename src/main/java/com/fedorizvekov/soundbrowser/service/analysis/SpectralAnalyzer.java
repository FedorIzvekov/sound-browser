package com.fedorizvekov.soundbrowser.service.analysis;

import java.util.Arrays;
import com.fedorizvekov.soundbrowser.model.analysis.SpectralMetrics;

public final class SpectralAnalyzer {

    private static final double TARGET_WINDOW_SECONDS = 0.085;

    private static final int MIN_FFT_SIZE = 2048;
    private static final int MAX_FFT_SIZE = 16384;

    private static final double MIN_ANALYSIS_FREQUENCY_HZ = 20.0;
    private static final double SUB_MAX_HZ = 80.0;
    private static final double LOW_MAX_HZ = 250.0;
    private static final double MID_MAX_HZ = 2_000.0;
    private static final double HIGH_MAX_HZ = 8_000.0;
    private static final double MAX_ANALYSIS_FREQUENCY_HZ = 20_000.0;

    private final double sampleRate;
    private final int fftSize;
    private final int hopSize;

    private final double[] window;
    private final double[] samples;
    private final double[] real;
    private final double[] imaginary;

    private int sampleCount;
    private int newSamplesSinceLastWindow;
    private long processedWindows;

    private double magnitudeSum;
    private double weightedFrequencyMagnitudeSum;

    private double subEnergy;
    private double lowEnergy;
    private double midEnergy;
    private double highEnergy;
    private double veryHighEnergy;


    public SpectralAnalyzer(double sampleRate) {

        if (!Double.isFinite(sampleRate) || sampleRate <= 0.0) {
            throw new IllegalArgumentException("Sample rate must be greater than zero");
        }

        this.sampleRate = sampleRate;
        this.fftSize = resolveFftSize(sampleRate);
        this.hopSize = fftSize / 2;

        this.window = createHannWindow(fftSize);
        this.samples = new double[fftSize];
        this.real = new double[fftSize];
        this.imaginary = new double[fftSize];
    }


    public void accept(double monoSample) {

        samples[sampleCount++] = monoSample;
        newSamplesSinceLastWindow++;

        if (sampleCount == fftSize) {

            processWindow();

            System.arraycopy(samples, hopSize, samples, 0, fftSize - hopSize);

            sampleCount = fftSize - hopSize;
            newSamplesSinceLastWindow = 0;
        }
    }


    public SpectralMetrics finish() {

        if (sampleCount > 0 && (processedWindows == 0 || newSamplesSinceLastWindow > 0)) {

            Arrays.fill(samples, sampleCount, fftSize, 0.0);

            processWindow();
            newSamplesSinceLastWindow = 0;
        }

        var totalEnergy = subEnergy + lowEnergy + midEnergy + highEnergy + veryHighEnergy;
        var spectralCentroidHz = magnitudeSum > 0.0 ? weightedFrequencyMagnitudeSum / magnitudeSum : 0.0;

        if (totalEnergy <= 0.0) {
            return new SpectralMetrics(spectralCentroidHz, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        return new SpectralMetrics(
                spectralCentroidHz,
                subEnergy / totalEnergy,
                lowEnergy / totalEnergy,
                midEnergy / totalEnergy,
                highEnergy / totalEnergy,
                veryHighEnergy / totalEnergy
        );
    }


    private void processWindow() {

        for (var index = 0; index < fftSize; index++) {
            real[index] = samples[index] * window[index];
            imaginary[index] = 0.0;
        }

        fft(real, imaginary);

        var nyquistHz = sampleRate / 2.0;
        var maxFrequencyHz = Math.min(MAX_ANALYSIS_FREQUENCY_HZ, nyquistHz);
        var maxBin = Math.min(fftSize / 2, (int) Math.floor(maxFrequencyHz * fftSize / sampleRate));

        for (var bin = 1; bin <= maxBin; bin++) {

            var frequencyHz = bin * sampleRate / fftSize;

            if (frequencyHz < MIN_ANALYSIS_FREQUENCY_HZ) {
                continue;
            }

            var realValue = real[bin];
            var imaginaryValue = imaginary[bin];

            var magnitude = Math.hypot(realValue, imaginaryValue);
            var power = realValue * realValue + imaginaryValue * imaginaryValue;

            magnitudeSum += magnitude;
            weightedFrequencyMagnitudeSum += frequencyHz * magnitude;

            if (frequencyHz < SUB_MAX_HZ) {
                subEnergy += power;
            } else if (frequencyHz < LOW_MAX_HZ) {
                lowEnergy += power;
            } else if (frequencyHz < MID_MAX_HZ) {
                midEnergy += power;
            } else if (frequencyHz < HIGH_MAX_HZ) {
                highEnergy += power;
            } else {
                veryHighEnergy += power;
            }
        }

        processedWindows++;
    }


    private int resolveFftSize(double sampleRate) {

        var targetSize = Math.max(MIN_FFT_SIZE, (int) Math.round(sampleRate * TARGET_WINDOW_SECONDS));
        var size = 1;

        while (size < targetSize && size < MAX_FFT_SIZE) {
            size <<= 1;
        }

        return Math.max(MIN_FFT_SIZE, Math.min(size, MAX_FFT_SIZE));
    }


    private double[] createHannWindow(int size) {

        var result = new double[size];

        for (var index = 0; index < size; index++) {
            result[index] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index / (size - 1));
        }

        return result;
    }


    private void fft(double[] real, double[] imaginary) {

        var size = real.length;
        var reversed = 0;

        for (var index = 1; index < size; index++) {

            var bit = size >> 1;

            while ((reversed & bit) != 0) {
                reversed ^= bit;
                bit >>= 1;
            }

            reversed ^= bit;

            if (index < reversed) {

                var realValue = real[index];
                real[index] = real[reversed];
                real[reversed] = realValue;

                var imaginaryValue = imaginary[index];
                imaginary[index] = imaginary[reversed];
                imaginary[reversed] = imaginaryValue;
            }
        }

        for (var length = 2; length <= size; length <<= 1) {

            var angle = -2.0 * Math.PI / length;
            var stepReal = Math.cos(angle);
            var stepImaginary = Math.sin(angle);
            var halfLength = length / 2;

            for (var offset = 0; offset < size; offset += length) {

                var rotationReal = 1.0;
                var rotationImaginary = 0.0;

                for (var index = 0; index < halfLength; index++) {

                    var evenIndex = offset + index;
                    var oddIndex = evenIndex + halfLength;

                    var oddReal = real[oddIndex] * rotationReal - imaginary[oddIndex] * rotationImaginary;
                    var oddImaginary = real[oddIndex] * rotationImaginary + imaginary[oddIndex] * rotationReal;

                    var evenReal = real[evenIndex];
                    var evenImaginary = imaginary[evenIndex];

                    real[evenIndex] = evenReal + oddReal;
                    imaginary[evenIndex] = evenImaginary + oddImaginary;

                    real[oddIndex] = evenReal - oddReal;
                    imaginary[oddIndex] = evenImaginary - oddImaginary;

                    var nextRotationReal = rotationReal * stepReal - rotationImaginary * stepImaginary;

                    rotationImaginary = rotationReal * stepImaginary + rotationImaginary * stepReal;
                    rotationReal = nextRotationReal;
                }
            }
        }
    }
}