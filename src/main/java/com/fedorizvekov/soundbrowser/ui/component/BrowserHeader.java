package com.fedorizvekov.soundbrowser.ui.component;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.fedorizvekov.soundbrowser.model.AudioFormatFilter;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class BrowserHeader extends VBox {

    private final Button openDirectoryButton = new Button("Open Directory");
    private final Button exportSfxButton = new Button("Export SFX");
    private final Button exportMusicButton = new Button("Export Music");
    private final Label directoryLabel = new Label("No directory selected");
    private final TextField searchField = new TextField();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final ToggleGroup formatGroup = new ToggleGroup();
    private final HBox formatSwitch = new HBox();

    private Consumer<AudioFormatFilter> onFormatChanged = formatFilter -> {};

    private boolean loading;
    private boolean exporting;
    private boolean hasSounds;
    private boolean exportAvailable;
    private boolean directorySelected;


    public BrowserHeader() {

        super(12);

        configureFormatSwitch();
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
                exportMusicButton,
                exportSfxButton
        );

        toolbar.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(toolbar, searchField);
    }


    public HBox getFormatSwitch() {
        return formatSwitch;
    }


    public AudioFormatFilter getFormatFilter() {
        return (AudioFormatFilter) formatGroup.getSelectedToggle().getUserData();
    }


    public void setOnOpenDirectory(Runnable action) {
        openDirectoryButton.setOnAction(event -> action.run());
    }


    public void setOnExportSfx(Runnable action) {
        exportSfxButton.setOnAction(event -> action.run());
    }


    public void setOnExportMusic(Runnable action) {
        exportMusicButton.setOnAction(event -> action.run());
    }


    public void setOnSearch(Consumer<String> action) {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> action.accept(newValue));
    }


    public void setOnFormatChanged(Consumer<AudioFormatFilter> action) {
        onFormatChanged = action;
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


    private void configureFormatSwitch() {

        var wavOnly = createFormatButton("WAV", AudioFormatFilter.WAV_ONLY);
        var wavAndOgg = createFormatButton("WAV + OGG", AudioFormatFilter.WAV_AND_OGG);
        var oggOnly = createFormatButton("OGG", AudioFormatFilter.OGG_ONLY);

        wavOnly.getStyleClass().add("format-first");
        oggOnly.getStyleClass().add("format-last");

        formatSwitch.getStyleClass().add("audio-format-switch");
        formatSwitch.setAlignment(Pos.CENTER_RIGHT);
        formatSwitch.setMinWidth(HBox.USE_PREF_SIZE);
        formatSwitch.getChildren().addAll(wavOnly, wavAndOgg, oggOnly);

        formatGroup.selectToggle(wavAndOgg);

        formatGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {

            if (newToggle == null) {
                formatGroup.selectToggle(oldToggle);
                return;
            }

            if (oldToggle != null) {
                onFormatChanged.accept((AudioFormatFilter) newToggle.getUserData());
            }
        });
    }


    private ToggleButton createFormatButton(String text, AudioFormatFilter formatFilter) {

        var button = new ToggleButton(text);

        button.setToggleGroup(formatGroup);
        button.setUserData(formatFilter);
        button.setMinWidth(ToggleButton.USE_PREF_SIZE);
        button.getStyleClass().add("audio-format-button");

        return button;
    }


    private void configureControls() {

        openDirectoryButton.getStyleClass().add("primary-button");
        exportSfxButton.getStyleClass().add("primary-button");
        exportMusicButton.getStyleClass().add("primary-button");

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
        var exportDisabled = busy || !hasSounds || !exportAvailable;

        openDirectoryButton.setDisable(busy);
        formatSwitch.setDisable(busy);
        searchField.setDisable(busy || !directorySelected);

        exportSfxButton.setDisable(exportDisabled);
        exportMusicButton.setDisable(exportDisabled);

        exportSfxButton.setVisible(directorySelected);
        exportSfxButton.setManaged(directorySelected);

        exportMusicButton.setVisible(directorySelected);
        exportMusicButton.setManaged(directorySelected);

        progressIndicator.setVisible(busy);
        progressIndicator.setManaged(busy);
    }

}