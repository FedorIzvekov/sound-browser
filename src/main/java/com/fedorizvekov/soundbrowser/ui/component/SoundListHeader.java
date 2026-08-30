package com.fedorizvekov.soundbrowser.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class SoundListHeader extends HBox {

    public static final double INCLUDE_WIDTH = 52.0;
    public static final double PREVIEW_WIDTH = 38.0;
    public static final double WAVEFORM_WIDTH = 360.0;


    public SoundListHeader() {

        super(12);

        getStyleClass().add("sound-list-header");
        setAlignment(Pos.CENTER_LEFT);

        var includeLabel = createLabel("Include");
        var previewLabel = createLabel("Preview");
        var soundLabel = createLabel("Sound");
        var waveformLabel = createLabel("Waveform");

        setFixedWidth(includeLabel, INCLUDE_WIDTH);
        setFixedWidth(previewLabel, PREVIEW_WIDTH);
        setFixedWidth(waveformLabel, WAVEFORM_WIDTH);

        includeLabel.setAlignment(Pos.CENTER);
        previewLabel.setAlignment(Pos.CENTER);
        waveformLabel.setAlignment(Pos.CENTER_LEFT);

        soundLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(soundLabel, Priority.ALWAYS);

        getChildren().addAll(includeLabel, previewLabel, soundLabel, waveformLabel);
    }


    private Label createLabel(String text) {
        var label = new Label(text);
        label.getStyleClass().add("sound-list-column-title");

        return label;
    }


    private void setFixedWidth(Label label, double width) {
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
    }

}