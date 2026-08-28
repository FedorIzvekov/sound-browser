package com.fedorizvekov.soundbrowser.ui;

import java.nio.file.Path;
import java.util.Locale;
import com.fedorizvekov.soundbrowser.model.CatalogResult;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.SoundCatalogService;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public final class SoundBrowserView extends BorderPane {

    private static final String STATUS_WARNING_STYLE = "status-warning";
    private static final String STATUS_ERROR_STYLE = "status-error";

    private final SoundCatalogService soundCatalogService;
    private final AudioPlayer audioPlayer;
    private final WaveformService waveformService;

    private final Label directoryLabel = new Label("No directory selected");
    private final Label foundCountLabel = new Label("0 found");
    private final Label totalCountLabel = new Label("0 total");
    private final Label errorCountLabel = new Label();
    private final Label oggCountLabel = new Label();
    private final Label statusLabel = new Label();

    private final TextField searchField = new TextField();
    private final Button openDirectoryButton = new Button("Open Directory");
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();

    private final ListView<SoundEntry> soundList = new ListView<>();
    private final ObservableList<SoundEntry> sounds = FXCollections.observableArrayList();
    private final FilteredList<SoundEntry> filteredSounds = new FilteredList<>(sounds);

    private int errorCount;
    private int oggCount;


    public SoundBrowserView(
            SoundCatalogService soundCatalogService,
            AudioPlayer audioPlayer,
            WaveformService waveformService
    ) {
        this.soundCatalogService = soundCatalogService;
        this.audioPlayer = audioPlayer;
        this.waveformService = waveformService;

        configureView();
        configureActions();
        updateCountLabels();
    }


    private void configureView() {
        getStyleClass().add("sound-browser");
        setPadding(new Insets(16));

        configureDirectoryLabel();
        configureSearchField();
        configureLoadingIndicator();
        configureStatusLabel();
        configureSoundList();
        configureCountLabels();

        var titleLabel = new Label("Sound Library");
        titleLabel.getStyleClass().add("library-title");

        var separatorLabel = new Label("·");
        separatorLabel.getStyleClass().add("directory-separator");

        var headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        openDirectoryButton.getStyleClass().add("primary-button");

        var directoryBar = new HBox(
                10,
                openDirectoryButton,
                titleLabel,
                separatorLabel,
                directoryLabel,
                headerSpacer,
                loadingIndicator
        );

        directoryBar.setAlignment(Pos.CENTER_LEFT);

        var countBar = new HBox(
                6,
                foundCountLabel,
                totalCountLabel,
                errorCountLabel,
                oggCountLabel
        );

        countBar.setAlignment(Pos.CENTER_LEFT);
        countBar.setMaxWidth(Double.MAX_VALUE);

        var header = new VBox(
                12,
                directoryBar,
                searchField,
                countBar,
                statusLabel
        );

        header.getStyleClass().add("browser-header");

        setTop(header);
        setCenter(soundList);

        BorderPane.setMargin(soundList, new Insets(12, 0, 0, 0));
    }


    private void configureDirectoryLabel() {
        directoryLabel.getStyleClass().add("directory-path");
        directoryLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        directoryLabel.setMaxWidth(Double.MAX_VALUE);
    }


    private void configureSearchField() {
        searchField.setPromptText("Search by filename or path...");
        searchField.setDisable(true);
        searchField.getStyleClass().add("search-field");
    }


    private void configureLoadingIndicator() {
        loadingIndicator.setPrefSize(18, 18);
        loadingIndicator.setMaxSize(18, 18);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
    }


    private void configureStatusLabel() {
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }


    private void configureCountLabels() {

        foundCountLabel.getStyleClass().addAll("count-label", "found-count-label");
        totalCountLabel.getStyleClass().addAll("count-label", "total-count-label");
        errorCountLabel.getStyleClass().add("error-count-label");
        oggCountLabel.getStyleClass().add("ogg-count-label");

        oggCountLabel.setTooltip(new Tooltip("OGG support is not implemented yet"));

        setLabelVisible(errorCountLabel, false);
        setLabelVisible(oggCountLabel, false);
    }


    private void configureSoundList() {
        soundList.setItems(filteredSounds);
        soundList.setPlaceholder(new Label("Select a directory containing WAV files"));
        soundList.setCellFactory(list -> new SoundListCell(audioPlayer, waveformService));
        soundList.getStyleClass().add("sound-list");
    }


    private void configureActions() {

        openDirectoryButton.setOnAction(event -> selectDirectory());

        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> filterSounds(newValue)
        );

        filteredSounds.addListener(
                (ListChangeListener<SoundEntry>) change -> updateCountLabels()
        );
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
        hideStatus();

        directoryLabel.setText(directory.toString());
        directoryLabel.setTooltip(new Tooltip(directory.toString()));

        searchField.clear();
        sounds.clear();

        errorCount = 0;
        oggCount = 0;

        updateCountLabels();
        showStatus("Scanning audio files...", null);

        Thread.startVirtualThread(() -> {

            try {

                var result = soundCatalogService.load(directory);
                Platform.runLater(() -> applyCatalogResult(result));

            } catch (Exception exception) {
                Platform.runLater(() -> handleLoadingFailure(exception));
            }
        });
    }


    private void applyCatalogResult(CatalogResult result) {
        errorCount = result.errors().size();
        oggCount = result.oggFiles().size();
        sounds.setAll(result.entries());

        setLoading(false);
        updateCountLabels();

        if (errorCount == 0) {
            hideStatus();
            return;
        }

        showStatus(formatSkippedFiles(errorCount), STATUS_WARNING_STYLE);
    }


    private void handleLoadingFailure(Exception exception) {
        sounds.clear();
        errorCount = 0;
        oggCount = 0;

        setLoading(false);
        updateCountLabels();

        var message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        showStatus("Failed to load directory: " + message, STATUS_ERROR_STYLE);
    }


    private void filterSounds(String query) {
        var normalizedQuery = normalize(query);

        filteredSounds.setPredicate(entry -> {
            if (normalizedQuery.isEmpty()) {
                return true;
            }

            return normalize(entry.filename()).contains(normalizedQuery) || normalize(entry.relativePath().toString()).contains(normalizedQuery);

        });
    }


    private void updateCountLabels() {
        var found = filteredSounds.size();
        var total = sounds.size() + errorCount + oggCount;

        foundCountLabel.setText("%,d found".formatted(found));
        totalCountLabel.setText("%,d total".formatted(total));
        errorCountLabel.setText(formatErrorCount(errorCount));
        oggCountLabel.setText("%,d OGG found".formatted(oggCount));

        setLabelVisible(errorCountLabel, errorCount > 0);
        setLabelVisible(oggCountLabel, oggCount > 0);
    }


    private String formatErrorCount(int count) {
        return count == 1
                ? "1 WAV error"
                : "%,d WAV errors".formatted(count);
    }


    private String formatSkippedFiles(int count) {
        return count == 1
                ? "1 WAV file could not be analyzed"
                : "%,d WAV files could not be analyzed".formatted(count);
    }


    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }


    private void setLoading(boolean loading) {
        openDirectoryButton.setDisable(loading);
        searchField.setDisable(loading || sounds.isEmpty());
        soundList.setDisable(loading);

        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }


    private void setLabelVisible(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }


    private void showStatus(String text, String additionalStyleClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(STATUS_WARNING_STYLE, STATUS_ERROR_STYLE);

        if (additionalStyleClass != null) {
            statusLabel.getStyleClass().add(additionalStyleClass);
        }

        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }


    private void hideStatus() {
        statusLabel.setText("");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

}
