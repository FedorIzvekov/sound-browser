package com.fedorizvekov.soundbrowser.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.Waveform;

public final class WaveformService {

    private static final int DEFAULT_POINTS = 1000;
    private static final int BUFFER_SIZE = 8192;

    private final int points;


    public WaveformService() {
        this(DEFAULT_POINTS);
    }


    public WaveformService(int points) {

        if (points <= 0) {
            throw new IllegalArgumentException("Waveform points must be greater than zero");
        }

        this.points = points;
    }


    public Optional<Waveform> analyze(Path file) {

        try (var stream = AudioSystem.getAudioInputStream(file.toFile())) {

            var format = stream.getFormat();

            if (!isSupportedFormat(format)) {
                return Optional.empty();
            }

            var totalFrames = stream.getFrameLength();

            if (totalFrames <= 0) {
                return Optional.empty();
            }

            var bucketCount = (int) Math.min(points, totalFrames);

            var minimums = new float[bucketCount];
            var maximums = new float[bucketCount];

            var framesRead = readWaveform(stream, format, totalFrames, minimums, maximums);

            if (framesRead != totalFrames) {
                return Optional.empty();
            }

            return Optional.of(new Waveform(minimums, maximums));

        } catch (IOException | UnsupportedAudioFileException exception) {
            return Optional.empty();
        }
    }


    private long readWaveform(AudioInputStream stream, AudioFormat format, long totalFrames, float[] minimums, float[] maximums) throws IOException {

        var frameSize = format.getFrameSize();
        var channels = format.getChannels();
        var bytesPerSample = format.getSampleSizeInBits() / 8;

        var bufferSize = Math.max(frameSize, BUFFER_SIZE - BUFFER_SIZE % frameSize);
        var buffer = new byte[bufferSize];

        var frameIndex = 0L;
        var previousBucket = -1;

        int read;

        while ((read = stream.read(buffer)) != -1) {

            var framesRead = read / frameSize;

            for (var localFrame = 0; localFrame < framesRead; localFrame++) {

                var frameOffset = localFrame * frameSize;
                var bucket = (int) (frameIndex * minimums.length / totalFrames);

                for (var channel = 0; channel < channels; channel++) {
                    var sampleOffset = frameOffset + channel * bytesPerSample;
                    var sample = readSample(buffer, sampleOffset, format);

                    if (bucket != previousBucket) {
                        minimums[bucket] = sample;
                        maximums[bucket] = sample;
                        previousBucket = bucket;
                    } else {
                        minimums[bucket] = Math.min(minimums[bucket], sample);
                        maximums[bucket] = Math.max(maximums[bucket], sample);
                    }
                }

                frameIndex++;
            }
        }

        return frameIndex;
    }


    private float readSample(byte[] data, int offset, AudioFormat format) {
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


    private float readSignedSample(long raw, int bits) {
        var signBit = 1L << (bits - 1);
        var fullRange = 1L << bits;
        var signed = (raw & signBit) != 0 ? raw - fullRange : raw;

        return signed / (float) signBit;
    }


    private float readUnsignedSample(long raw, int bits) {
        var midpoint = 1L << (bits - 1);

        return (raw - midpoint) / (float) midpoint;
    }


    private float readFloatingPointSample(long raw, int bits) {
        if (bits == 32) {
            return Float.intBitsToFloat((int) raw);
        }

        return (float) Double.longBitsToDouble(raw);
    }


    private boolean isSupportedFormat(AudioFormat format) {
        var encoding = format.getEncoding();

        var supportedEncoding = encoding.equals(AudioFormat.Encoding.PCM_SIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_FLOAT);

        if (!supportedEncoding || format.getChannels() <= 0 || format.getFrameSize() <= 0) {
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
