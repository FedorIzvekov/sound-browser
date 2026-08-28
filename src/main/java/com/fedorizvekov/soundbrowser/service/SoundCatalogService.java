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
        var audioFiles = findAudioFiles(root);

        var entries = new ArrayList<SoundEntry>(audioFiles.size());
        var errors = new ArrayList<CatalogError>();
        var oggFiles = new ArrayList<Path>();

        for (var file : audioFiles) {

            if (isOgg(file)) {
                oggFiles.add(file);
                continue;
            }

            try {

                entries.add(createEntry(root, file));

            } catch (UnsupportedAudioFileException exception) {

                errors.add(createError(file, CatalogError.Type.UNSUPPORTED_AUDIO, exception));

            } catch (IOException exception) {

                errors.add(createError(file, CatalogError.Type.FILE_UNREADABLE, exception));

            }
        }

        return new CatalogResult(List.copyOf(entries), List.copyOf(errors), List.copyOf(oggFiles));
    }


    private SoundEntry createEntry(Path root, Path file) throws IOException, UnsupportedAudioFileException {
        var metadata = audioAnalyzer.analyze(file);
        return new SoundEntry(
                file,
                root.relativize(file),
                file.getFileName().toString(),
                Files.size(file),
                metadata
        );
    }


    private CatalogError createError(Path file, CatalogError.Type type, Exception exception) {
        return new CatalogError(file, type, formatErrorMessage(exception));
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


    private List<Path> findAudioFiles(Path root) throws IOException {

        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isWav(path) || isOgg(path))
                    .sorted()
                    .toList();
        }
    }


    private boolean isWav(Path path) {
        return filename(path).endsWith(".wav");
    }


    private boolean isOgg(Path path) {
        return filename(path).endsWith(".ogg");
    }


    private String filename(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT);
    }


    private String formatErrorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

}
