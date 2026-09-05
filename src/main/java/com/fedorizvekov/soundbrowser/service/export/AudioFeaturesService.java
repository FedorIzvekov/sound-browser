package com.fedorizvekov.soundbrowser.service.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.export.AudioFeatures;
import com.fedorizvekov.soundbrowser.service.AudioDecoder;

public final class AudioFeaturesService {

    private static final int DEFAULT_ENVELOPE_POINTS = 64;
    private static final int BUFFER_SIZE = 8192;

    private static final double SILENCE_THRESHOLD_DBFS = -60.0;
    private static final double SILENCE_THRESHOLD = Math.pow(10.0, SILENCE_THRESHOLD_DBFS / 20.0);
    private static final double SILENCE_WINDOW_SECONDS = 0.01;

    private final AudioDecoder audioDecoder;
    private final int envelopePoints;


    public AudioFeaturesService(AudioDecoder audioDecoder) {
        this(audioDecoder, DEFAULT_ENVELOPE_POINTS);
    }


    public AudioFeaturesService(AudioDecoder audioDecoder, int envelopePoints) {

        if (envelopePoints <= 0) {
            throw new IllegalArgumentException("Envelope points must be greater than zero");
        }

        this.audioDecoder = audioDecoder;
        this.envelopePoints = envelopePoints;
    }


    public Optional<AudioFeatures> analyze(Path file) {

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


    private Optional<AudioFeatures> analyze(AudioInputStream stream, AudioFormat format, long totalFrames) throws IOException {

        var bucketCount = (int) Math.min(envelopePoints, totalFrames);

        var bucketSquareSums = new double[bucketCount];
        var bucketSampleCounts = new long[bucketCount];
        var peakEnvelope = new float[bucketCount];

        var frameSize = format.getFrameSize();
        var channels = format.getChannels();
        var bytesPerSample = format.getSampleSizeInBits() / 8;

        var silenceWindowFrames = Math.max(1L, Math.round(format.getSampleRate() * SILENCE_WINDOW_SECONDS));

        var bufferSize = Math.max(frameSize, BUFFER_SIZE - BUFFER_SIZE % frameSize);
        var buffer = new byte[bufferSize];

        var frameIndex = 0L;
        var sampleCount = 0L;
        var squareSum = 0.0;

        var peak = 0.0;
        var peakFrameIndex = 0L;

        var windowFrames = 0L;
        var windowSampleCount = 0L;
        var windowSquareSum = 0.0;
        var silentFrames = 0L;

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
                var bucket = (int) (frameIndex * (long) bucketCount / totalFrames);

                for (var channel = 0; channel < channels; channel++) {

                    var sampleOffset = frameOffset + channel * bytesPerSample;
                    var sample = readSample(buffer, sampleOffset, format);

                    if (!Double.isFinite(sample)) {
                        return Optional.empty();
                    }

                    var absoluteSample = Math.abs(sample);
                    var square = sample * sample;

                    squareSum += square;
                    sampleCount++;

                    bucketSquareSums[bucket] += square;
                    bucketSampleCounts[bucket]++;

                    peakEnvelope[bucket] = (float) Math.max(peakEnvelope[bucket], absoluteSample);

                    windowSquareSum += square;
                    windowSampleCount++;

                    if (absoluteSample > peak) {
                        peak = absoluteSample;
                        peakFrameIndex = frameIndex;
                    }
                }

                frameIndex++;
                windowFrames++;

                if (windowFrames == silenceWindowFrames) {

                    if (isSilent(windowSquareSum, windowSampleCount)) {
                        silentFrames += windowFrames;
                    }

                    windowFrames = 0L;
                    windowSampleCount = 0L;
                    windowSquareSum = 0.0;
                }
            }
        }

        if (sampleCount == 0) {
            return Optional.empty();
        }

        if (windowFrames > 0 && isSilent(windowSquareSum, windowSampleCount)) {
            silentFrames += windowFrames;
        }

        var rmsEnvelope = createRmsEnvelope(bucketSquareSums, bucketSampleCounts);

        var rms = Math.sqrt(squareSum / sampleCount);
        var crestFactor = rms > 0.0 ? peak / rms : 0.0;
        var silenceRatio = silentFrames / (double) frameIndex;
        var peakTimeRatio = frameIndex > 1 && peak > 0.0
                ? peakFrameIndex / (double) (frameIndex - 1)
                : 0.0;

        return Optional.of(new AudioFeatures(
                peak,
                rms,
                crestFactor,
                silenceRatio,
                peakTimeRatio,
                rmsEnvelope,
                peakEnvelope
        ));
    }


    private float[] createRmsEnvelope(double[] squareSums, long[] sampleCounts) {

        var envelope = new float[squareSums.length];

        for (var index = 0; index < envelope.length; index++) {

            if (sampleCounts[index] > 0) {
                envelope[index] = (float) Math.sqrt(squareSums[index] / sampleCounts[index]);
            }
        }

        return envelope;
    }


    private boolean isSilent(double squareSum, long sampleCount) {

        if (sampleCount == 0) {
            return true;
        }

        var windowRms = Math.sqrt(squareSum / sampleCount);

        return windowRms <= SILENCE_THRESHOLD;
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
                || format.getSampleRate() <= 0.0f) {
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