package com.fedorizvekov.soundbrowser.model;

import java.nio.file.Path;

public record CatalogError(
        Path path,
        String message
) {
}
