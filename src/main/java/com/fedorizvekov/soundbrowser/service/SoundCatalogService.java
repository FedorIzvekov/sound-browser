package com.fedorizvekov.soundbrowser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.CatalogError;
import com.fedorizvekov.soundbrowser.model.CatalogResult;
import com.fedorizvekov.soundbrowser.model.SoundEntry;

public final class SoundCatalogService {

    private final AudioAnalyzer audioAnalyzer;


    public SoundCatalogService(AudioAnalyzer audioAnalyzer) {
        this.audioAnalyzer = audioAnalyzer;
    }


    public CatalogResult load(Path directory) throws IOException {

        var root = validateDirectory(directory);
        var wavFiles = findWavFiles(root);

        var entries = new ArrayList<SoundEntry>(wavFiles.size());
        var errors = new ArrayList<CatalogError>();

        for (var file : wavFiles) {

            try {

                var metadata = audioAnalyzer.analyze(file);

                entries.add(new SoundEntry(file, root.relativize(file), file.getFileName().toString(), Files.size(file), metadata));

            } catch (IOException | UnsupportedAudioFileException exception) {
                errors.add(new CatalogError(file, exception.getMessage()));
            }
        }

        return new CatalogResult(List.copyOf(entries), List.copyOf(errors));
    }


    private Path validateDirectory(Path directory) {

        Objects.requireNonNull(directory, "Directory must not be null");

        var root = directory.toAbsolutePath().normalize();

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Directory does not exist: " + root);
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path is not a directory: " + root);
        }

        return root;
    }


    private List<Path> findWavFiles(Path root) throws IOException {

        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isWav)
                    .sorted()
                    .toList();
        }
    }


    private boolean isWav(Path path) {
        var filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".wav");
    }

}
