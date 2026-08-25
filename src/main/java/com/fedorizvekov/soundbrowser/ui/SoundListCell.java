package com.fedorizvekov.soundbrowser.ui;

import com.fedorizvekov.soundbrowser.model.SoundEntry;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

final class SoundListCell extends ListCell<SoundEntry> {

    private final Label filenameLabel = new Label();
    private final Label pathLabel = new Label();
    private final Label metadataLabel = new Label();

    private final Tooltip pathTooltip = new Tooltip();
    private final VBox content = new VBox(4);


    SoundListCell() {

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

        content.getStyleClass().add("sound-cell-content");
        content.getChildren().addAll(filenameLabel, pathLabel, metadataLabel);

        content.prefWidthProperty().bind(
                Bindings.createDoubleBinding(() -> Math.max(0.0, getWidth() - 28.0), widthProperty())
        );
    }


    @Override
    protected void updateItem(SoundEntry entry, boolean empty) {

        super.updateItem(entry, empty);

        if (empty || entry == null) {
            clearContent();
            return;
        }

        var relativePath = entry.relativePath().toString();

        filenameLabel.setText(entry.filename());

        pathLabel.setText(relativePath);
        pathTooltip.setText(relativePath);

        metadataLabel.setText(formatMetadata(entry));

        setText(null);
        setGraphic(content);
    }


    private void clearContent() {
        filenameLabel.setText("");
        pathLabel.setText("");
        pathTooltip.setText("");
        metadataLabel.setText("");

        setText(null);
        setGraphic(null);
    }


    private String formatMetadata(SoundEntry entry) {

        var metadata = entry.metadata();

        return "%s · %s · %.1f kHz · %s · %d bit · %s"
                .formatted(
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
