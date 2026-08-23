package com.fedorizvekov.soundbrowser.model;

public record AudioMetadata(
        double durationSeconds,
        float sampleRate,
        int channels,
        int sampleSizeBits,
        String encoding,
        boolean bigEndian,
        int frameSizeBytes,
        int frameLength,
        String type
) {
}