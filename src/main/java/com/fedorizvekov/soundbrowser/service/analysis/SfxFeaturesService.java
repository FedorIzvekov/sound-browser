package com.fedorizvekov.soundbrowser.service.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.analysis.SfxFeatures;
import com.fedorizvekov.soundbrowser.service.AudioDecoder;

public final class SfxFeaturesService {

    private static final int DEFAULT_ENVELOPE_POINTS = 64;
    private static final int BUFFER_SIZE = 8192;

    private final AudioDecoder audioDecoder;
    private final int envelopePoints;


    public SfxFeaturesService(AudioDecoder audioDecoder) {
        this(audioDecoder, DEFAULT_ENVELOPE_POINTS);
    }


    public SfxFeaturesService(AudioDecoder audioDecoder, int envelopePoints) {

        if (envelopePoints <= 0) {
            throw new IllegalArgumentException("Envelope points must be greater than zero");
        }

        this.audioDecoder = audioDecoder;
        this.envelopePoints = envelopePoints;
    }


    public Optional<SfxFeatures> analyze(Path file) {

        try (var stream = audioDecoder.open(file)) {

            var format = stream.getFormat();

            if (!isSupportedFormat(format)) {
                return Optional.empty();
            }

            var totalFrames = stream.getFrameLength();

            if (totalFrames <= 0) {
                return Optional.empty();
            }

            return analyze(stream, format, totalFrames);

        } catch (IOException | UnsupportedAudioFileException exception) {
            return Optional.empty();
        }
    }


    private Optional<SfxFeatures> analyze(AudioInputStream stream, AudioFormat format, long totalFrames) throws IOException {

        var channels = format.getChannels();
        var frameSize = format.getFrameSize();
        var bytesPerSample = format.getSampleSizeInBits() / 8;
        var sampleRate = format.getSampleRate();

        var activityAnalyzer = new ActivityAnalyzer(sampleRate);
        var amplitudeAnalyzer = new AmplitudeAnalyzer(totalFrames, channels, envelopePoints, sampleRate);
        var loopAnalyzer = new LoopAnalyzer(totalFrames, channels, sampleRate);
        var spectralAnalyzer = new SpectralAnalyzer(sampleRate);
        var stereoAnalyzer = channels == 2 ? new StereoAnalyzer() : null;

        var bufferSize = Math.max(frameSize, BUFFER_SIZE - BUFFER_SIZE % frameSize);
        var buffer = new byte[bufferSize];
        var frameSamples = new double[channels];

        var frameIndex = 0L;

        int read;

        while ((read = stream.read(buffer)) != -1) {

            if (read == 0) {
                continue;
            }

            if (read % frameSize != 0) {
                return Optional.empty();
            }

            var framesRead = read / frameSize;

            for (var localFrame = 0; localFrame < framesRead; localFrame++) {

                if (frameIndex >= totalFrames) {
                    return Optional.empty();
                }

                var frameOffset = localFrame * frameSize;

                var frameSampleSum = 0.0;
                var frameSquareSum = 0.0;
                var framePeak = 0.0;

                var leftSample = 0.0;
                var rightSample = 0.0;

                for (var channel = 0; channel < channels; channel++) {

                    var sampleOffset = frameOffset + channel * bytesPerSample;
                    var sample = readSample(buffer, sampleOffset, format);

                    if (!Double.isFinite(sample)) {
                        return Optional.empty();
                    }

                    frameSamples[channel] = sample;

                    if (channel == 0) {
                        leftSample = sample;
                    } else if (channel == 1) {
                        rightSample = sample;
                    }

                    frameSampleSum += sample;
                    frameSquareSum += sample * sample;
                    framePeak = Math.max(framePeak, Math.abs(sample));
                }

                var monoSample = frameSampleSum / channels;
                var frameAmplitude = Math.sqrt(frameSquareSum / channels);

                activityAnalyzer.accept(frameAmplitude);
                amplitudeAnalyzer.accept(frameIndex, frameSquareSum, framePeak);
                loopAnalyzer.accept(frameSamples, frameAmplitude);
                spectralAnalyzer.accept(monoSample);

                if (stereoAnalyzer != null) {
                    stereoAnalyzer.accept(leftSample, rightSample);
                }

                frameIndex++;
            }
        }

        if (frameIndex == 0) {
            return Optional.empty();
        }

        var activityMetrics = activityAnalyzer.finish();
        var amplitudeMetrics = amplitudeAnalyzer.finish();
        var loopMetrics = loopAnalyzer.finish();
        var spectralMetrics = spectralAnalyzer.finish();
        var stereoCorrelation = stereoAnalyzer != null ? stereoAnalyzer.finish() : null;

        return Optional.of(new SfxFeatures(
                activityMetrics,
                amplitudeMetrics,
                loopMetrics,
                spectralMetrics,
                stereoCorrelation
        ));
    }


    private double readSample(byte[] data, int offset, AudioFormat format) {

        var encoding = format.getEncoding();
        var bits = format.getSampleSizeInBits();
        var bytes = bits / 8;
        var raw = readRawValue(data, offset, bytes, format.isBigEndian());

        if (encoding.equals(AudioFormat.Encoding.PCM_FLOAT)) {
            return readFloatingPointSample(raw, bits);
        }

        if (encoding.equals(AudioFormat.Encoding.PCM_SIGNED)) {
            return readSignedSample(raw, bits);
        }

        return readUnsignedSample(raw, bits);
    }


    private long readRawValue(byte[] data, int offset, int bytes, boolean bigEndian) {

        var value = 0L;

        if (bigEndian) {

            for (var index = 0; index < bytes; index++) {
                value = (value << 8) | (data[offset + index] & 0xFFL);
            }

        } else {

            for (var index = bytes - 1; index >= 0; index--) {
                value = (value << 8) | (data[offset + index] & 0xFFL);
            }
        }

        return value;
    }


    private double readSignedSample(long raw, int bits) {

        var signBit = 1L << (bits - 1);
        var fullRange = 1L << bits;
        var signed = (raw & signBit) != 0 ? raw - fullRange : raw;

        return signed / (double) signBit;
    }


    private double readUnsignedSample(long raw, int bits) {

        var midpoint = 1L << (bits - 1);

        return (raw - midpoint) / (double) midpoint;
    }


    private double readFloatingPointSample(long raw, int bits) {

        if (bits == 32) {
            return Float.intBitsToFloat((int) raw);
        }

        return Double.longBitsToDouble(raw);
    }


    private boolean isSupportedFormat(AudioFormat format) {

        var encoding = format.getEncoding();

        var supportedEncoding = encoding.equals(AudioFormat.Encoding.PCM_SIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_FLOAT);

        if (!supportedEncoding
                || format.getChannels() <= 0
                || format.getFrameSize() <= 0
                || !Float.isFinite(format.getSampleRate())
                || format.getSampleRate() <= 0.0f
        ) {
            return false;
        }

        var bits = format.getSampleSizeInBits();

        var supportedBits = encoding.equals(AudioFormat.Encoding.PCM_FLOAT)
                ? bits == 32 || bits == 64
                : bits == 8 || bits == 16 || bits == 24 || bits == 32;

        if (!supportedBits) {
            return false;
        }

        var bytesPerSample = bits / 8;
        var expectedFrameSize = bytesPerSample * format.getChannels();

        return format.getFrameSize() == expectedFrameSize;
    }

}