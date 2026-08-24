package com.fedorizvekov.soundbrowser.model;

import java.nio.file.Path;

public record SoundEntry(
        Path path,
        Path relativePath,
        String filename,
        long sizeBytes,
        AudioMetadata metadata
) {
}
