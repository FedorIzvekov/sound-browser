package com.fedorizvekov.soundbrowser.ui.component;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class SoundListCell extends ListCell<SoundEntry> {

    private static final PseudoClass PLAYING_PSEUDO_CLASS = PseudoClass.getPseudoClass("playing");

    private final AudioPlayer audioPlayer;
    private final WaveformService waveformService;

    private final CheckBox exportCheckBox = new CheckBox();
    private final StackPane exportColumn = new StackPane(exportCheckBox);
    private final Button playButton = new Button("▶");
    private final Label filenameLabel = new Label();
    private final Label pathLabel = new Label();
    private final Label metadataLabel = new Label();

    private final Tooltip pathTooltip = new Tooltip();
    private final VBox soundInfo = new VBox(4);
    private final WaveformView waveformView = new WaveformView();
    private final HBox content = new HBox(12);

    private Path waveformFile;


    public SoundListCell(AudioPlayer audioPlayer, WaveformService waveformService) {

        this.audioPlayer = audioPlayer;
        this.waveformService = waveformService;

        configureExportCheckBox();
        configurePlayButton();
        configureSoundInfo();

        exportColumn.setMinWidth(SoundListHeader.EXPORT_WIDTH);
        exportColumn.setPrefWidth(SoundListHeader.EXPORT_WIDTH);
        exportColumn.setMaxWidth(SoundListHeader.EXPORT_WIDTH);

        content.getStyleClass().add("sound-cell-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(exportColumn, playButton, soundInfo, waveformView);

        HBox.setHgrow(soundInfo, Priority.ALWAYS);

        content.prefWidthProperty().bind(

                Bindings.createDoubleBinding(
                        () -> Math.max(0.0, getWidth() - getInsets().getLeft() - getInsets().getRight()),
                        widthProperty(),
                        insetsProperty()
                )
        );

    }


    private void configureExportCheckBox() {

        exportCheckBox.getStyleClass().add("export-checkbox");
        exportCheckBox.setTooltip(new Tooltip("Include sound in JSONL export"));
        exportCheckBox.setAccessibleText("Include sound in JSONL export");
        exportCheckBox.setFocusTraversable(false);
        exportCheckBox.setDisable(true);

        exportCheckBox.setOnAction(event -> {

            var entry = getItem();

            if (entry != null) {
                ((SoundList) getListView()).setSelectedForExport(entry, exportCheckBox.isSelected());
            }

            event.consume();
        });
    }


    @Override
    protected void updateItem(SoundEntry entry, boolean empty) {

        super.updateItem(entry, empty);

        if (empty || entry == null) {
            clearContent();
            return;
        }

        exportCheckBox.setSelected(((SoundList) getListView()).isSelectedForExport(entry));
        exportCheckBox.setDisable(false);

        var relativePath = entry.relativePath().toString();

        filenameLabel.setText(entry.filename());

        pathLabel.setText(relativePath);
        pathTooltip.setText(relativePath);

        metadataLabel.setText(formatMetadata(entry));

        setText(null);
        setGraphic(content);

        updatePlaybackState();
        updateWaveform(entry);
    }


    private void configurePlayButton() {

        playButton.getStyleClass().add("play-button");
        playButton.setFocusTraversable(false);
        playButton.setDisable(true);

        playButton.setOnAction(event -> {

            var entry = getItem();

            if (entry == null) {
                return;
            }

            getListView().getSelectionModel().select(entry);

            try {

                audioPlayer.toggle(entry.path());
                playButton.setTooltip(null);

            } catch (IOException | UnsupportedAudioFileException | LineUnavailableException exception) {

                showPlaybackError(exception);

            } finally {
                getListView().refresh();
            }

            event.consume();
        });
    }


    private void configureSoundInfo() {

        filenameLabel.getStyleClass().add("sound-name");
        pathLabel.getStyleClass().add("sound-path");
        metadataLabel.getStyleClass().add("sound-metadata");

        filenameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        filenameLabel.setMaxWidth(Double.MAX_VALUE);

        pathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        pathLabel.setTooltip(pathTooltip);

        metadataLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        metadataLabel.setMaxWidth(Double.MAX_VALUE);

        soundInfo.getChildren().addAll(filenameLabel, pathLabel, metadataLabel);
    }


    private void updatePlaybackState() {

        var entry = getItem();

        if (isEmpty() || entry == null) {
            resetPlayButton();
            return;
        }

        var current = entry.path().equals(audioPlayer.getCurrentFile());
        var playing = current && audioPlayer.isPlaying();

        playButton.setText(playing ? "⏸" : "▶");
        playButton.setAccessibleText(playing ? "Pause sound" : "Play sound");
        playButton.setDisable(false);
        playButton.pseudoClassStateChanged(PLAYING_PSEUDO_CLASS, playing);
    }


    private void updateWaveform(SoundEntry entry) {

        var file = entry.path();

        if (file.equals(waveformFile)) {
            return;
        }

        waveformFile = file;
        waveformView.setWaveform(null);

        Thread.ofVirtual().name("waveform-loader").start(() -> loadWaveform(file));
    }


    private void loadWaveform(Path file) {

        var waveform = waveformService.analyze(file).orElse(null);

        Platform.runLater(() -> {

            var currentEntry = getItem();

            if (isEmpty()
                    || currentEntry == null
                    || !file.equals(waveformFile)
                    || !file.equals(currentEntry.path())) {
                return;
            }

            waveformView.setWaveform(waveform);
        });
    }


    private void showPlaybackError(Exception exception) {

        var message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        playButton.setTooltip(new Tooltip("Playback failed: " + message));
    }


    private void resetPlayButton() {
        playButton.setText("▶");
        playButton.setAccessibleText("Play sound");
        playButton.setTooltip(null);
        playButton.setDisable(true);
        playButton.pseudoClassStateChanged(PLAYING_PSEUDO_CLASS, false);
    }


    private void clearContent() {
        exportCheckBox.setSelected(false);
        exportCheckBox.setDisable(true);

        filenameLabel.setText("");
        pathLabel.setText("");
        pathTooltip.setText("");
        metadataLabel.setText("");

        waveformFile = null;
        waveformView.setWaveform(null);

        resetPlayButton();

        setText(null);
        setGraphic(null);
    }


    private String formatMetadata(SoundEntry entry) {

        var metadata = entry.metadata();

        return "%s · %s · %.1f kHz · %s · %d bit · %s".formatted(
                metadata.type(),
                formatDuration(metadata.durationSeconds()),
                metadata.sampleRate() / 1_000.0,
                formatChannels(metadata.channels()),
                metadata.sampleSizeBits(),
                formatSize(entry.sizeBytes())
        );
    }


    private String formatChannels(int channels) {
        return switch (channels) {
            case 1 -> "Mono";
            case 2 -> "Stereo";
            default -> channels + " channels";
        };
    }


    private String formatDuration(double seconds) {

        if (!Double.isFinite(seconds) || seconds < 0.0) {
            return "Unknown duration";
        }

        if (seconds < 60.0) {
            return "%.2f s".formatted(seconds);
        }

        var minutes = (int) (seconds / 60.0);
        var remainingSeconds = seconds % 60.0;

        return "%d:%05.2f".formatted(minutes, remainingSeconds);
    }


    private String formatSize(long bytes) {

        if (bytes < 1_024) {
            return bytes + " B";
        }

        var kilobytes = bytes / 1_024.0;

        if (kilobytes < 1_024.0) {
            return "%.1f KB".formatted(kilobytes);
        }

        var megabytes = kilobytes / 1_024.0;

        if (megabytes < 1_024.0) {
            return "%.1f MB".formatted(megabytes);
        }

        return "%.1f GB".formatted(megabytes / 1_024.0);
    }

}