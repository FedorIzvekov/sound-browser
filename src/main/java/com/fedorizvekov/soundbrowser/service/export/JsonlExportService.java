package com.fedorizvekov.soundbrowser.service.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.model.export.JsonlExportResult;
import com.fedorizvekov.soundbrowser.model.export.JsonlSoundEntry;

public final class JsonlExportService {

    private final AudioFeaturesService audioFeaturesService;
    private final ObjectWriter jsonWriter;


    public JsonlExportService(AudioFeaturesService audioFeaturesService) {
        this.audioFeaturesService = audioFeaturesService;
        jsonWriter = new ObjectMapper().writerFor(JsonlSoundEntry.class);
    }


    public JsonlExportResult export(Collection<SoundEntry> entries, Path targetFile) throws IOException {

        var absoluteTarget = targetFile.toAbsolutePath().normalize();
        var directory = absoluteTarget.getParent();
        var filename = absoluteTarget.getFileName().toString();

        var temporaryFile = Files.createTempFile(directory, filename + ".", ".tmp");

        var exportedCount = 0L;

        try {

            try (var writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {

                for (var entry : entries) {

                    var features = audioFeaturesService.analyze(entry.path()).orElse(null);

                    var jsonlEntry = new JsonlSoundEntry(
                            normalizePath(entry.relativePath()),
                            entry.filename(),
                            entry.sizeBytes(),
                            entry.metadata(),
                            features
                    );

                    writer.write(jsonWriter.writeValueAsString(jsonlEntry));
                    writer.newLine();

                    exportedCount++;
                }
            }

            replaceTarget(temporaryFile, absoluteTarget);

            return new JsonlExportResult(absoluteTarget, exportedCount);

        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }


    private void replaceTarget(Path temporaryFile, Path targetFile) throws IOException {

        try {

            Files.move(temporaryFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (AtomicMoveNotSupportedException exception) {

            Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        }
    }


    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}