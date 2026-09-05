package com.fedorizvekov.soundbrowser.service;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class AudioDecoder {

    private static final int PCM_SAMPLE_SIZE_BITS = 16;
    private static final double MICROSECONDS_PER_SECOND = 1_000_000.0;


    public AudioInputStream open(Path file) throws IOException, UnsupportedAudioFileException {

        var fileFormat = AudioSystem.getAudioFileFormat(file.toFile());
        var sourceStream = AudioSystem.getAudioInputStream(file.toFile());
        var sourceFormat = sourceStream.getFormat();

        if (isPcm(sourceFormat)) {
            return sourceStream;
        }

        var sampleRate = sourceFormat.getSampleRate();
        var channels = sourceFormat.getChannels();

        if (!Float.isFinite(sampleRate) || sampleRate <= 0.0f || channels <= 0) {
            sourceStream.close();
            throw new UnsupportedAudioFileException("Invalid audio format: " + sourceFormat);
        }

        var targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                PCM_SAMPLE_SIZE_BITS,
                channels,
                channels * PCM_SAMPLE_SIZE_BITS / 8,
                sampleRate,
                false
        );

        if (!AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
            sourceStream.close();
            throw new UnsupportedAudioFileException("Cannot decode audio format: " + sourceFormat);
        }

        AudioInputStream decodedStream;

        try {
            decodedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);

        } catch (IllegalArgumentException exception) {
            sourceStream.close();

            var unsupported = new UnsupportedAudioFileException("Cannot decode audio format: " + sourceFormat);
            unsupported.initCause(exception);

            throw unsupported;
        }

        if (decodedStream.getFrameLength() != AudioSystem.NOT_SPECIFIED) {
            return decodedStream;
        }

        var frameLength = resolveFrameLength(fileFormat, targetFormat);

        if (frameLength == AudioSystem.NOT_SPECIFIED) {
            return decodedStream;
        }

        return new AudioInputStream(decodedStream, targetFormat, frameLength);
    }


    private long resolveFrameLength(AudioFileFormat fileFormat, AudioFormat decodedFormat) {

        if (fileFormat.getFrameLength() != AudioSystem.NOT_SPECIFIED) {
            return fileFormat.getFrameLength();
        }

        var duration = fileFormat.properties().get("duration");

        if (!(duration instanceof Number number)) {
            return AudioSystem.NOT_SPECIFIED;
        }

        var durationSeconds = number.doubleValue() / MICROSECONDS_PER_SECOND;
        var frameRate = decodedFormat.getFrameRate();

        if (!Double.isFinite(durationSeconds)
                || durationSeconds <= 0.0
                || !Float.isFinite(frameRate)
                || frameRate <= 0.0f) {
            return AudioSystem.NOT_SPECIFIED;
        }

        return Math.max(1L, Math.round(durationSeconds * frameRate));
    }


    private boolean isPcm(AudioFormat format) {

        var encoding = format.getEncoding();

        return encoding.equals(AudioFormat.Encoding.PCM_SIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_UNSIGNED)
                || encoding.equals(AudioFormat.Encoding.PCM_FLOAT);
    }

}