package com.fedorizvekov.soundbrowser.ui;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import com.fedorizvekov.soundbrowser.model.CatalogResult;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.SoundCatalogService;
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
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

public final class SoundBrowserView extends BorderPane {

    private static final String STATUS_WARNING_STYLE = "status-warning";
    private static final String STATUS_ERROR_STYLE = "status-error";

    private final SoundCatalogService soundCatalogService;
    private final AudioPlayer audioPlayer;

    private final Label directoryLabel = new Label("No directory selected");
    private final Label countLabel = new Label("0 total");
    private final Label statusLabel = new Label();

    private final TextField searchField = new TextField();
    private final Button openDirectoryButton = new Button("Open Directory");
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();

    private final ListView<SoundEntry> soundList = new ListView<>();
    private final ObservableList<SoundEntry> sounds = FXCollections.observableArrayList();
    private final FilteredList<SoundEntry> filteredSounds = new FilteredList<>(sounds);


    public SoundBrowserView(
            SoundCatalogService soundCatalogService,
            AudioPlayer audioPlayer
    ) {
        this.soundCatalogService = Objects.requireNonNull(soundCatalogService);
        this.audioPlayer = Objects.requireNonNull(audioPlayer);

        configureView();
        configureActions();
        updateCountLabel();
    }

    private void configureView() {
        getStyleClass().add("sound-browser");

        configureDirectoryLabel();
        configureSearchField();
        configureLoadingIndicator();
        configureStatusLabel();
        configureSoundList();

        var titleLabel = new Label("Sound Library");
        titleLabel.getStyleClass().add("library-title");

        var separatorLabel = new Label("·");
        separatorLabel.getStyleClass().add("directory-separator");

        openDirectoryButton.getStyleClass().add("primary-button");

        var directoryBar = new HBox(
                10,
                openDirectoryButton,
                loadingIndicator,
                titleLabel,
                separatorLabel,
                directoryLabel
        );

        directoryBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(directoryLabel, Priority.ALWAYS);

        countLabel.getStyleClass().add("count-label");

        var countBar = new HBox(countLabel);
        countBar.setAlignment(Pos.CENTER_RIGHT);

        var searchBlock = new VBox(6, searchField, countBar);

        var header = new VBox(12, directoryBar, searchBlock, statusLabel);
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


    private void configureSoundList() {
        soundList.setItems(filteredSounds);
        soundList.setPlaceholder(new Label("Select a directory containing WAV files"));
        soundList.setCellFactory(list -> new SoundListCell(audioPlayer));
        soundList.getStyleClass().add("sound-list");
    }


    private void configureActions() {
        openDirectoryButton.setOnAction(event -> selectDirectory());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterSounds(newValue));
        filteredSounds.addListener((ListChangeListener<SoundEntry>) change -> updateCountLabel());
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

        showStatus("Scanning WAV files...", null);

        Thread.ofVirtual().name("sound-catalog-loader").start(() -> {

            try {
                var result = soundCatalogService.load(directory);

                Platform.runLater(() -> applyCatalogResult(result));

            } catch (Exception exception) {
                Platform.runLater(() -> handleLoadingFailure(exception));
            }
        });
    }


    private void applyCatalogResult(CatalogResult result) {
        sounds.setAll(result.entries());

        setLoading(false);
        updateCountLabel();

        if (result.errors().isEmpty()) {
            hideStatus();
            return;
        }

        showStatus(formatSkippedFiles(result.errors().size()), STATUS_WARNING_STYLE);
    }


    private void handleLoadingFailure(Exception exception) {
        sounds.clear();
        setLoading(false);
        updateCountLabel();

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


    private void updateCountLabel() {
        var total = sounds.size();
        var found = filteredSounds.size();
        var hasQuery = !searchField.getText().isBlank();

        if (hasQuery) {
            countLabel.setText("%,d found · %,d total".formatted(found, total));
        } else {
            countLabel.setText("%,d total".formatted(total));
        }
    }


    private String formatSkippedFiles(int count) {
        return count == 1
                ? "1 file could not be analyzed"
                : "%,d files could not be analyzed".formatted(count);
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
