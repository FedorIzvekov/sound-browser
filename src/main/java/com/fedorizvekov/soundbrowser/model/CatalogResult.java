package com.fedorizvekov.soundbrowser.model;

import java.util.List;

public record CatalogResult(
        List<SoundEntry> entries,
        List<CatalogError> errors
) {
}
