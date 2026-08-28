package com.fedorizvekov.soundbrowser.model.export;

import java.nio.file.Path;

public record JsonlExportResult(
        Path file,
        long exportedCount
) {
}
