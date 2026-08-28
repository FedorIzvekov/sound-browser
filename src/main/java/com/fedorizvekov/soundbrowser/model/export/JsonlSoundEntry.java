package com.fedorizvekov.soundbrowser.model.export;

import com.fedorizvekov.soundbrowser.model.AudioMetadata;

public record JsonlSoundEntry(
        String relativePath,
        String filename,
        long sizeBytes,
        AudioMetadata metadata,
        AudioFeatures features
) {
}
