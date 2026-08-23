package com.fedorizvekov.soundbrowser.service;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.AudioMetadata;

public final class AudioAnalyzer {

    private static final double MICROSECONDS_PER_SECOND = 1_000_000.0;


    public AudioMetadata analyze(Path file) throws IOException, UnsupportedAudioFileException {

        var fileFormat = AudioSystem.getAudioFileFormat(file.toFile());
        var audioFormat = fileFormat.getFormat();

        return new AudioMetadata(
                resolveDurationSeconds(fileFormat),
                audioFormat.getSampleRate(),
                audioFormat.getChannels(),
                audioFormat.getSampleSizeInBits(),
                audioFormat.getEncoding().toString(),
                audioFormat.isBigEndian(),
                audioFormat.getFrameSize(),
                fileFormat.getFrameLength(),
                fileFormat.getType().toString()
        );

    }


    private double resolveDurationSeconds(AudioFileFormat fileFormat) {

        var audioFormat = fileFormat.getFormat();
        var frameLength = fileFormat.getFrameLength();
        var frameRate = audioFormat.getFrameRate();

        if (frameLength != AudioSystem.NOT_SPECIFIED && frameRate != AudioSystem.NOT_SPECIFIED && frameRate > 0.0f) {
            return frameLength / (double) frameRate;
        }

        var duration = fileFormat.properties().get("duration");

        if (duration instanceof Number number) {
            return number.longValue() / MICROSECONDS_PER_SECOND;
        }

        return Double.NaN;
    }

}
