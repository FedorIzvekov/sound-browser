package com.fedorizvekov.soundbrowser.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import com.fedorizvekov.soundbrowser.model.CatalogResult;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.model.export.JsonlExportResult;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.SoundCatalogService;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import com.fedorizvekov.soundbrowser.service.export.JsonlExportService;
import com.fedorizvekov.soundbrowser.ui.component.BrowserHeader;
import com.fedorizvekov.soundbrowser.ui.component.CatalogStatus;
import com.fedorizvekov.soundbrowser.ui.component.SoundList;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public final class SoundBrowserView extends BorderPane {

    private final SoundCatalogService soundCatalogService;
    private final JsonlExportService jsonlExportService;

    private final ObservableList<SoundEntry> sounds = FXCollections.observableArrayList();
    private final FilteredList<SoundEntry> filteredSounds = new FilteredList<>(sounds);

    private final BrowserHeader header = new BrowserHeader();
    private final CatalogStatus statusView = new CatalogStatus();
    private final SoundList soundList;

    private int errorCount;
    private int oggCount;

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
        setCenter(soundList);

        BorderPane.setMargin(soundList, new Insets(12, 0, 0, 0));
    }


    private void configureActions() {
        header.setOnOpenDirectory(this::selectDirectory);
        header.setOnExportJsonl(this::selectExportTarget);
        header.setOnSearch(this::filterSounds);

        filteredSounds.addListener((ListChangeListener<SoundEntry>) change -> updateState());
    }


    private void selectDirectory() {

        var chooser = new DirectoryChooser();
        chooser.setTitle("Select Sound Directory");

        var directory = chooser.showDialog(getScene().getWindow());

        if (directory != null) {
            loadDirectory(directory.toPath());
        }
    }


    private void loadDirectory(Path directory) {

        setLoading(true);

        header.setDirectory(directory);
        header.clearSearch();

        sounds.clear();
        errorCount = 0;
        oggCount = 0;

        statusView.showInfo("Scanning audio files...");
        updateState();

        Thread.ofVirtual().name("sound-catalog-loader").start(() -> loadCatalog(directory));
    }


    private void loadCatalog(Path directory) {

        try {
            var result = soundCatalogService.load(directory);

            Platform.runLater(() -> applyCatalogResult(result));

        } catch (Exception exception) {
            Platform.runLater(() -> handleLoadingFailure(exception));
        }
    }


    private void applyCatalogResult(CatalogResult result) {

        errorCount = result.errors().size();
        oggCount = result.oggFiles().size();

        sounds.setAll(result.entries());

        setLoading(false);
        updateState();

        if (errorCount == 0) {
            statusView.clearStatus();
            return;
        }

        statusView.showWarning(formatSkippedFiles(errorCount));
    }


    private void handleLoadingFailure(Exception exception) {

        sounds.clear();
        errorCount = 0;
        oggCount = 0;

        setLoading(false);
        updateState();

        statusView.showError("Failed to load directory: " + formatException(exception));
    }


    private void filterSounds(String query) {

        var normalizedQuery = normalize(query);

        filteredSounds.setPredicate(entry -> {
            if (normalizedQuery.isEmpty()) {
                return true;
            }

            return normalize(entry.filename()).contains(normalizedQuery)
                    || normalize(entry.relativePath().toString()).contains(normalizedQuery);

        });
    }


    private void selectExportTarget() {

        if (filteredSounds.isEmpty()) {
            return;
        }

        var chooser = new FileChooser();

        chooser.setTitle("Export sound library");
        chooser.setInitialFileName("sound_library");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Lines (*.jsonl)", "*.jsonl"));

        var selectedFile = chooser.showSaveDialog(getScene().getWindow());

        if (selectedFile == null) {
            return;
        }

        var entries = List.copyOf(filteredSounds);
        var targetFile = ensureJsonlExtension(selectedFile.toPath());

        startExport(entries, targetFile);
    }


    private void startExport(List<SoundEntry> entries, Path targetFile) {

        setExporting(true);

        statusView.showInfo("Exporting %,d sounds...".formatted(entries.size()));

        Thread.ofVirtual().name("jsonl-exporter").start(() -> exportJsonl(entries, targetFile));
    }


    private void exportJsonl(List<SoundEntry> entries, Path targetFile) {

        try {
            var result = jsonlExportService.export(entries, targetFile);

            Platform.runLater(() -> handleExportSuccess(result));

        } catch (IOException exception) {

            Platform.runLater(() -> handleExportFailure(exception));

        }
    }


    private void handleExportSuccess(JsonlExportResult result) {
        setExporting(false);
        statusView.showSuccess("%,d sounds exported to %s".formatted(result.exportedCount(), result.file().getFileName()));
    }


    private void handleExportFailure(IOException exception) {
        setExporting(false);
        statusView.showError("JSONL export failed: " + formatException(exception));
    }


    private void updateState() {

        statusView.updateCounts(filteredSounds.size(), sounds.size(), errorCount, oggCount);

        header.setHasSounds(!sounds.isEmpty());
        header.setExportAvailable(!filteredSounds.isEmpty());

        var busy = loading || exporting;
        soundList.setDisable(busy);
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


    private String formatSkippedFiles(int count) {
        return count == 1
                ? "1 WAV file could not be analyzed"
                : "%,d WAV files could not be analyzed".formatted(count);
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
}
