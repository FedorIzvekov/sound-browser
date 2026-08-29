package com.fedorizvekov.soundbrowser.ui.component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public final class SoundList extends ListView<SoundEntry> {

    private final Set<Path> excludedFromExport = new HashSet<>();


    public SoundList(ObservableList<SoundEntry> sounds, AudioPlayer audioPlayer, WaveformService waveformService) {

        setItems(sounds);
        setPlaceholder(new Label("Select a directory containing WAV files"));
        setCellFactory(list -> new SoundListCell(audioPlayer, waveformService));
        getStyleClass().add("sound-list");

    }


    boolean isSelectedForExport(SoundEntry entry) {
        return !excludedFromExport.contains(entry.path());
    }


    void setSelectedForExport(SoundEntry entry, boolean selected) {

        if (selected) {
            excludedFromExport.remove(entry.path());
        } else {
            excludedFromExport.add(entry.path());
        }

    }


    public List<SoundEntry> getEntriesForExport() {
        return getItems().stream()
                .filter(this::isSelectedForExport)
                .toList();
    }


    public void resetExportSelection() {
        excludedFromExport.clear();
        refresh();
    }

}