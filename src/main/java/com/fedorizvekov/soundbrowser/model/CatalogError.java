package com.fedorizvekov.soundbrowser.model;

import java.nio.file.Path;

public record CatalogError(
        Path file,
        Type type,
        String message
) {

    public enum Type {
        FILE_UNREADABLE,
        UNSUPPORTED_AUDIO,
        ANALYSIS_FAILED
    }

}