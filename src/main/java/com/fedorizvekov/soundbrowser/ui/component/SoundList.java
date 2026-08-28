package com.fedorizvekov.soundbrowser.ui.component;

import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public final class SoundList extends ListView<SoundEntry> {

    public SoundList(ObservableList<SoundEntry> sounds, AudioPlayer audioPlayer, WaveformService waveformService) {

        setItems(sounds);
        setPlaceholder(new Label("Select a directory containing WAV files"));

        setCellFactory(list -> new SoundListCell(audioPlayer, waveformService));

        getStyleClass().add("sound-list");

    }
}