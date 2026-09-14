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

    private static final double ROLLOFF_PERCENTILE = 0.85;
    private static final double FLATNESS_POWER_FLOOR = 1.0e-300;

    private final double sampleRate;
    private final int fftSize;
    private final int hopSize;

    private final double[] window;
    private final double[] samples;
    private final double[] real;
    private final double[] imaginary;
    private final double[] previousNormalizedPower;

    private int sampleCount;
    private int newSamplesSinceLastWindow;
    private long processedWindows;
    private long spectralWindowCount;

    private double magnitudeSum;
    private double weightedFrequencyMagnitudeSum;

    private double spectralFlatnessSum;
    private double spectralRolloffSum;
    private double spectralBandwidthSum;

    private boolean hasPreviousSpectrum;
    private long spectralFluxCount;
    private double spectralFluxSum;

    private long centroidWindowCount;
    private double centroidMean;
    private double centroidM2;

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
        this.previousNormalizedPower = new double[fftSize / 2 + 1];
    }


    public void accept(double monoSample) {

        samples[sampleCount++] = monoSample;
        newSamplesSinceLastWindow++;

        if (sampleCount == fftSize) {

            processWindow(true);

            System.arraycopy(samples, hopSize, samples, 0, fftSize - hopSize);

            sampleCount = fftSize - hopSize;
            newSamplesSinceLastWindow = 0;
        }
    }


    public SpectralMetrics finish() {

        if (sampleCount > 0 && (processedWindows == 0 || newSamplesSinceLastWindow > 0)) {

            Arrays.fill(samples, sampleCount, fftSize, 0.0);

            processWindow(false);
            newSamplesSinceLastWindow = 0;
        }

        var totalEnergy = subEnergy + lowEnergy + midEnergy + highEnergy + veryHighEnergy;

        var spectralCentroidHz = magnitudeSum > 0.0
                ? weightedFrequencyMagnitudeSum / magnitudeSum
                : 0.0;

        var spectralFlux = spectralFluxCount > 0
                ? spectralFluxSum / spectralFluxCount
                : 0.0;

        var spectralCentroidVariationHz = centroidWindowCount > 1
                ? Math.sqrt(centroidM2 / centroidWindowCount)
                : 0.0;

        if (totalEnergy <= 0.0 || spectralWindowCount == 0) {
            return new SpectralMetrics(
                    spectralCentroidHz,
                    0.0,
                    0.0,
                    0.0,
                    spectralFlux,
                    spectralCentroidVariationHz,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
            );
        }

        return new SpectralMetrics(
                spectralCentroidHz,
                spectralFlatnessSum / spectralWindowCount,
                spectralRolloffSum / spectralWindowCount,
                spectralBandwidthSum / spectralWindowCount,
                spectralFlux,
                spectralCentroidVariationHz,
                subEnergy / totalEnergy,
                lowEnergy / totalEnergy,
                midEnergy / totalEnergy,
                highEnergy / totalEnergy,
                veryHighEnergy / totalEnergy
        );
    }


    private void processWindow(boolean includeTemporalMetrics) {

        for (var index = 0; index < fftSize; index++) {
            real[index] = samples[index] * window[index];
            imaginary[index] = 0.0;
        }

        fft(real, imaginary);

        var nyquistHz = sampleRate / 2.0;
        var maxFrequencyHz = Math.min(MAX_ANALYSIS_FREQUENCY_HZ, nyquistHz);
        var maxBin = Math.min(fftSize / 2, (int) Math.floor(maxFrequencyHz * fftSize / sampleRate));

        var windowMagnitudeSum = 0.0;
        var windowWeightedFrequencyMagnitudeSum = 0.0;

        var windowPowerSum = 0.0;
        var windowWeightedFrequencyPowerSum = 0.0;

        var flatnessPowerSum = 0.0;
        var flatnessLogPowerSum = 0.0;
        var analyzedBinCount = 0;

        for (var bin = 1; bin <= maxBin; bin++) {

            var frequencyHz = bin * sampleRate / fftSize;

            if (frequencyHz < MIN_ANALYSIS_FREQUENCY_HZ) {
                continue;
            }

            var realValue = real[bin];
            var imaginaryValue = imaginary[bin];

            var magnitude = Math.hypot(realValue, imaginaryValue);
            var power = realValue * realValue + imaginaryValue * imaginaryValue;

            imaginary[bin] = power;

            magnitudeSum += magnitude;
            weightedFrequencyMagnitudeSum += frequencyHz * magnitude;

            windowMagnitudeSum += magnitude;
            windowWeightedFrequencyMagnitudeSum += frequencyHz * magnitude;

            windowPowerSum += power;
            windowWeightedFrequencyPowerSum += frequencyHz * power;

            var flatnessPower = Math.max(power, FLATNESS_POWER_FLOOR);

            flatnessPowerSum += flatnessPower;
            flatnessLogPowerSum += Math.log(flatnessPower);
            analyzedBinCount++;

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

        if (windowPowerSum > 0.0 && windowMagnitudeSum > 0.0 && analyzedBinCount > 0) {

            var windowMagnitudeCentroidHz = windowWeightedFrequencyMagnitudeSum / windowMagnitudeSum;
            var windowPowerCentroidHz = windowWeightedFrequencyPowerSum / windowPowerSum;

            spectralFlatnessSum += calculateSpectralFlatness(flatnessPowerSum, flatnessLogPowerSum, analyzedBinCount);

            updateDerivedMetrics(maxBin, windowPowerSum, windowPowerCentroidHz, includeTemporalMetrics);

            spectralWindowCount++;

            if (includeTemporalMetrics) {
                updateCentroidVariation(windowMagnitudeCentroidHz);
            }

        } else if (includeTemporalMetrics) {
            hasPreviousSpectrum = false;
        }

        processedWindows++;
    }


    private void updateDerivedMetrics(int maxBin, double powerSum, double centroidHz, boolean includeTemporalMetrics) {

        var targetPower = powerSum * ROLLOFF_PERCENTILE;
        var cumulativePower = 0.0;
        var rolloffHz = 0.0;
        var rolloffFound = false;

        var weightedSquaredDeviationSum = 0.0;

        var squaredFluxDifferenceSum = 0.0;

        for (var bin = 1; bin <= maxBin; bin++) {

            var frequencyHz = bin * sampleRate / fftSize;

            if (frequencyHz < MIN_ANALYSIS_FREQUENCY_HZ) {
                continue;
            }

            var power = imaginary[bin];

            if (!rolloffFound) {

                cumulativePower += power;
                rolloffHz = frequencyHz;

                if (cumulativePower >= targetPower) {
                    rolloffFound = true;
                }
            }

            var deviation = frequencyHz - centroidHz;

            weightedSquaredDeviationSum += power * deviation * deviation;

            if (includeTemporalMetrics) {

                var normalizedPower = power / powerSum;

                if (hasPreviousSpectrum) {

                    var difference = normalizedPower - previousNormalizedPower[bin];

                    squaredFluxDifferenceSum += difference * difference;
                }

                previousNormalizedPower[bin] = normalizedPower;
            }
        }

        spectralRolloffSum += rolloffHz;

        spectralBandwidthSum += Math.sqrt(weightedSquaredDeviationSum / powerSum);

        if (includeTemporalMetrics) {

            if (hasPreviousSpectrum) {

                spectralFluxSum += Math.sqrt(squaredFluxDifferenceSum);

                spectralFluxCount++;
            }

            hasPreviousSpectrum = true;
        }
    }


    private double calculateSpectralFlatness(double powerSum, double logPowerSum, int binCount) {

        var arithmeticMean = powerSum / binCount;

        var geometricMean = Math.exp(logPowerSum / binCount);

        return Math.min(1.0, geometricMean / arithmeticMean);
    }


    private void updateCentroidVariation(double centroidHz) {

        centroidWindowCount++;

        var delta = centroidHz - centroidMean;
        centroidMean += delta / centroidWindowCount;

        var deltaAfterMeanUpdate = centroidHz - centroidMean;
        centroidM2 += delta * deltaAfterMeanUpdate;
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