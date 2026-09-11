package com.fedorizvekov.soundbrowser.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.AudioFileType;
import com.fedorizvekov.soundbrowser.model.AudioFormatFilter;
import com.fedorizvekov.soundbrowser.model.CatalogError;
import com.fedorizvekov.soundbrowser.model.CatalogResult;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.model.export.ExportProfile;
import com.fedorizvekov.soundbrowser.model.export.JsonlExportResult;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.SoundCatalogService;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import com.fedorizvekov.soundbrowser.service.export.JsonlExportService;
import com.fedorizvekov.soundbrowser.ui.component.BrowserHeader;
import com.fedorizvekov.soundbrowser.ui.component.CatalogStatus;
import com.fedorizvekov.soundbrowser.ui.component.SoundList;
import com.fedorizvekov.soundbrowser.ui.component.SoundListHeader;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

public final class SoundBrowserView extends BorderPane {

    private static final Duration EXPORT_PROGRESS_INTERVAL = Duration.seconds(5);

    private final SoundCatalogService soundCatalogService;
    private final JsonlExportService jsonlExportService;
    private final AudioPlayer audioPlayer;

    private final ObservableList<SoundEntry> sounds = FXCollections.observableArrayList();
    private final FilteredList<SoundEntry> filteredSounds = new FilteredList<>(sounds);

    private final BrowserHeader header = new BrowserHeader();
    private final CatalogStatus statusView = new CatalogStatus(header.getFormatSwitch());
    private final SoundListHeader soundListHeader = new SoundListHeader();
    private final AtomicLong exportedCount = new AtomicLong();
    private final SoundList soundList;

    private Path currentDirectory;
    private Timeline exportProgressTimer;
    private ExportProfile currentExportProfile;
    private long exportTotal;
    private int errorCount;

    private boolean loading;
    private boolean exporting;


    public SoundBrowserView(
            SoundCatalogService soundCatalogService,
            JsonlExportService jsonlExportService,
            AudioPlayer audioPlayer,
            WaveformService waveformService
    ) {
        this.soundCatalogService = soundCatalogService;
        this.jsonlExportService = jsonlExportService;
        this.audioPlayer = audioPlayer;

        soundList = new SoundList(filteredSounds, audioPlayer, waveformService);

        configureView();
        configureActions();
        updateState();
    }


    private void configureView() {

        getStyleClass().add("sound-browser");

        var browserHeader = new VBox(12, header, statusView);
        browserHeader.getStyleClass().add("browser-header");

        setTop(browserHeader);

        var listContainer = new VBox(4, soundListHeader, soundList);

        VBox.setVgrow(soundList, Priority.ALWAYS);

        setCenter(listContainer);
        BorderPane.setMargin(listContainer, new Insets(12, 0, 0, 0));
    }


    private void configureActions() {

        header.setOnOpenDirectory(this::selectDirectory);
        header.setOnExportSfx(() -> selectExportTarget(ExportProfile.SFX));
        header.setOnExportMusic(() -> selectExportTarget(ExportProfile.MUSIC));
        header.setOnSearch(this::filterSounds);
        header.setOnFormatChanged(this::changeFormatFilter);

        audioPlayer.setOnPlaybackFinished(file -> Platform.runLater(() -> handlePlaybackFinished(file)));

        filteredSounds.addListener((ListChangeListener<SoundEntry>) change -> updateState());
    }


    private void handlePlaybackFinished(Path file) {

        if (!file.equals(audioPlayer.getCurrentFile()) || audioPlayer.isPaused() || !audioPlayer.isFinished()) {
            return;
        }

        playNextIncludedSound(file);
    }


    private void playNextIncludedSound(Path previousFile) {

        var nextEntry = soundList.getNextIncludedEntry(previousFile);

        while (nextEntry.isPresent()) {

            var entry = nextEntry.get();

            try {
                audioPlayer.play(entry.path());
                soundList.getSelectionModel().select(entry);
                soundList.refresh();
                return;

            } catch (IOException | UnsupportedAudioFileException | LineUnavailableException exception) {
                previousFile = entry.path();
                nextEntry = soundList.getNextIncludedEntry(previousFile);
            }
        }

        audioPlayer.stop();
        soundList.refresh();
    }


    private void selectDirectory() {

        if (loading || exporting) {
            return;
        }

        var chooser = new DirectoryChooser();
        chooser.setTitle("Select Sound Directory");

        var directory = chooser.showDialog(getScene().getWindow());

        if (directory != null) {
            loadDirectory(directory.toPath());
        }
    }


    private void changeFormatFilter(AudioFormatFilter formatFilter) {

        if (currentDirectory != null && !loading && !exporting) {
            loadDirectory(currentDirectory);
        }
    }


    private void loadDirectory(Path directory) {

        var root = directory.toAbsolutePath().normalize();
        var directoryChanged = !root.equals(currentDirectory);
        var formatFilter = header.getFormatFilter();

        currentDirectory = root;

        audioPlayer.stop();
        setLoading(true);

        header.setDirectory(root);

        if (directoryChanged) {
            header.clearSearch();
            soundList.resetInclusion();
        }

        errorCount = 0;
        sounds.clear();

        statusView.showInfo("Scanning audio files...");
        updateState();

        Thread.ofVirtual().name("sound-catalog-loader").start(() -> loadCatalog(root, formatFilter));
    }


    private void loadCatalog(Path directory, AudioFormatFilter formatFilter) {

        try {
            var result = soundCatalogService.load(directory, formatFilter);

            Platform.runLater(() -> applyCatalogResult(result));

        } catch (Exception exception) {
            Platform.runLater(() -> handleLoadingFailure(exception));
        }
    }


    private void applyCatalogResult(CatalogResult result) {

        errorCount = result.errors().size();

        sounds.setAll(result.entries());

        setLoading(false);

        if (result.errors().isEmpty()) {
            statusView.clearStatus();
            return;
        }

        statusView.showWarning(formatSkippedFiles(result.errors()));
    }


    private void handleLoadingFailure(Exception exception) {

        sounds.clear();
        errorCount = 0;

        setLoading(false);

        statusView.showError("Failed to load directory: " + formatException(exception));
    }


    private void filterSounds(String query) {

        audioPlayer.stop();

        var normalizedQuery = normalize(query);

        filteredSounds.setPredicate(entry -> {

            if (normalizedQuery.isEmpty()) {
                return true;
            }

            return normalize(entry.filename()).contains(normalizedQuery)
                    || normalize(entry.relativePath().toString()).contains(normalizedQuery);
        });

        soundList.refresh();
    }


    private void selectExportTarget(ExportProfile exportProfile) {

        if (loading || exporting) {
            return;
        }

        var entries = soundList.getIncludedEntries();

        if (entries.isEmpty()) {
            statusView.showWarning("No sounds included for export");
            return;
        }

        var chooser = new FileChooser();

        chooser.setTitle(exportProfile == ExportProfile.SFX ? "Export SFX library" : "Export music library");
        chooser.setInitialFileName(buildExportFileName(exportProfile));

        var selectedFile = chooser.showSaveDialog(getScene().getWindow());

        if (selectedFile == null) {
            return;
        }

        var targetFile = ensureJsonlExtension(selectedFile.toPath());

        startExport(entries, targetFile, exportProfile);
    }


    private void startExport(List<SoundEntry> entries, Path targetFile, ExportProfile exportProfile) {

        audioPlayer.stop();
        soundList.refresh();

        exportedCount.set(0L);
        exportTotal = entries.size();
        currentExportProfile = exportProfile;

        setExporting(true);

        updateExportProgress();
        exportProgressTimer = new Timeline(new KeyFrame(EXPORT_PROGRESS_INTERVAL, event -> updateExportProgress()));
        exportProgressTimer.setCycleCount(Animation.INDEFINITE);
        exportProgressTimer.play();

        Thread.ofVirtual().name("jsonl-exporter").start(() -> exportJsonl(entries, targetFile, exportProfile));
    }


    private void exportJsonl(List<SoundEntry> entries, Path targetFile, ExportProfile exportProfile) {

        try {
            var result = jsonlExportService.export(entries, targetFile, exportProfile, exportedCount::set);

            Platform.runLater(() -> handleExportSuccess(result));

        } catch (IOException | RuntimeException exception) {
            Platform.runLater(() -> handleExportFailure(exception));
        }
    }


    private void updateExportProgress() {

        if (!exporting || currentExportProfile == null) {
            return;
        }

        statusView.showInfo("Exporting %,d / %,d sounds as %s...".formatted(
                exportedCount.get(),
                exportTotal,
                formatExportProfile(currentExportProfile)
        ));
    }


    private void stopExportProgress() {

        if (exportProgressTimer != null) {

            exportProgressTimer.stop();
            exportProgressTimer = null;
        }

        currentExportProfile = null;
        exportTotal = 0L;
        exportedCount.set(0L);
    }


    private void handleExportSuccess(JsonlExportResult result) {

        stopExportProgress();
        setExporting(false);

        statusView.showSuccess("%,d sounds exported to %s".formatted(result.exportedCount(), result.file().getFileName()));
    }


    private void handleExportFailure(Exception exception) {

        stopExportProgress();
        setExporting(false);

        statusView.showError("JSONL export failed: " + formatException(exception));
    }


    private void updateState() {

        statusView.updateCounts(filteredSounds.size(), sounds.size() + errorCount, errorCount);

        header.setHasSounds(!sounds.isEmpty());
        header.setExportAvailable(!filteredSounds.isEmpty());

        soundList.setDisable(loading || exporting);
    }


    private void setLoading(boolean loading) {

        this.loading = loading;

        header.setLoading(loading);

        updateState();
    }


    private void setExporting(boolean exporting) {

        this.exporting = exporting;

        header.setExporting(exporting);

        updateState();
    }


    private String formatSkippedFiles(List<CatalogError> errors) {

        var wavErrors = errors.stream().filter(error -> error.fileType() == AudioFileType.WAV).count();
        var oggErrors = errors.stream().filter(error -> error.fileType() == AudioFileType.OGG).count();

        var total = errors.size();
        var message = new StringBuilder();

        if (total == 1) {
            message.append("1 audio file could not be analyzed");
        } else {
            message.append(total).append(" audio files could not be analyzed");
        }

        message.append(": ");

        if (wavErrors > 0) {
            message.append(wavErrors).append(" WAV");
        }

        if (oggErrors > 0) {

            if (wavErrors > 0) {
                message.append(", ");
            }

            message.append(oggErrors).append(" OGG");
        }

        return message.toString();
    }


    private String formatExportProfile(ExportProfile exportProfile) {
        return exportProfile == ExportProfile.SFX ? "SFX" : "music";
    }


    private String formatException(Exception exception) {

        var message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }


    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }


    private Path ensureJsonlExtension(Path file) {

        var filename = file.getFileName().toString();

        if (filename.toLowerCase(Locale.ROOT).endsWith(".jsonl")) {
            return file;
        }

        return file.resolveSibling(filename + ".jsonl");
    }


    private String buildExportFileName(ExportProfile exportProfile) {

        var normalizedFolderName = currentDirectory.getFileName().toString().strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}._-]+", "_");
        var suffix = exportProfile == ExportProfile.MUSIC ? "export" + "_music" :  "export" + "_sfx";

        return "%s_%s.jsonl".formatted(normalizedFolderName, suffix);
    }

}