package com.fedorizvekov.soundbrowser.ui.component;

import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class BrowserHeader extends VBox {

    private final Button openDirectoryButton = new Button("Open Directory");
    private final Button exportJsonlButton = new Button("Export JSONL");
    private final Label directoryLabel = new Label("No directory selected");
    private final TextField searchField = new TextField();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    private boolean loading;
    private boolean exporting;
    private boolean hasSounds;
    private boolean exportAvailable;
    private boolean directorySelected;

    public BrowserHeader() {

        super(12);

        configureControls();

        var titleLabel = new Label("Sound Library");
        titleLabel.getStyleClass().add("library-title");

        var separatorLabel = new Label("·");
        separatorLabel.getStyleClass().add("directory-separator");

        var toolbar = new HBox(
                10,
                openDirectoryButton,
                titleLabel,
                separatorLabel,
                directoryLabel,
                progressIndicator,
                exportJsonlButton
        );

        toolbar.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(toolbar, searchField);
    }


    public void setOnOpenDirectory(Runnable action) {
        openDirectoryButton.setOnAction(event -> action.run());
    }


    public void setOnExportJsonl(Runnable action) {
        exportJsonlButton.setOnAction(event -> action.run());
    }


    public void setOnSearch(Consumer<String> action) {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> action.accept(newValue));
    }


    public void setDirectory(Path directory) {
        var path = directory.toString();

        directorySelected = true;

        directoryLabel.setText(path);
        directoryLabel.setTooltip(new Tooltip(path));

        updateState();
    }


    public void clearSearch() {
        searchField.clear();
    }


    public void setLoading(boolean loading) {
        this.loading = loading;
        updateState();
    }


    public void setExporting(boolean exporting) {
        this.exporting = exporting;
        updateState();
    }


    public void setHasSounds(boolean hasSounds) {
        this.hasSounds = hasSounds;
        updateState();
    }


    public void setExportAvailable(boolean exportAvailable) {
        this.exportAvailable = exportAvailable;
        updateState();
    }


    private void configureControls() {

        openDirectoryButton.getStyleClass().add("primary-button");
        exportJsonlButton.getStyleClass().add("primary-button");

        directoryLabel.getStyleClass().add("directory-path");
        directoryLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        directoryLabel.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(directoryLabel, Priority.ALWAYS);

        searchField.setPromptText("Search by filename or path...");
        searchField.getStyleClass().add("search-field");

        progressIndicator.setPrefSize(18, 18);
        progressIndicator.setMaxSize(18, 18);

        updateState();
    }


    private void updateState() {

        var busy = loading || exporting;

        openDirectoryButton.setDisable(busy);
        searchField.setDisable(busy || !hasSounds);
        exportJsonlButton.setDisable(busy || !exportAvailable);

        exportJsonlButton.setText(exporting ? "Exporting..." : "Export JSONL");

        exportJsonlButton.setVisible(directorySelected);
        exportJsonlButton.setManaged(directorySelected);

        progressIndicator.setVisible(busy);
        progressIndicator.setManaged(busy);
    }

}
