package com.fedorizvekov.soundbrowser.ui.component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public final class SoundList extends ListView<SoundEntry> {

    private final Set<Path> excludedSoundPaths = new HashSet<>();


    public SoundList(ObservableList<SoundEntry> sounds, AudioPlayer audioPlayer, WaveformService waveformService) {

        setItems(sounds);
        setPlaceholder(new Label("Select a directory containing WAV files"));
        setCellFactory(list -> new SoundListCell(audioPlayer, waveformService));
        getStyleClass().add("sound-list");
    }


    boolean isIncluded(SoundEntry entry) {
        return !excludedSoundPaths.contains(entry.path());
    }


    void setIncluded(SoundEntry entry, boolean included) {

        if (included) {
            excludedSoundPaths.remove(entry.path());
        } else {
            excludedSoundPaths.add(entry.path());
        }
    }


    public List<SoundEntry> getIncludedEntries() {

        return getItems().stream()
                .filter(this::isIncluded)
                .toList();
    }


    public Optional<SoundEntry> getNextIncludedEntry(Path currentFile) {

        var currentFound = false;

        for (var entry : getItems()) {

            if (currentFound && isIncluded(entry)) {
                return Optional.of(entry);
            }

            if (entry.path().equals(currentFile)) {
                currentFound = true;
            }
        }

        return Optional.empty();
    }


    public void resetInclusion() {
        excludedSoundPaths.clear();
        refresh();
    }

}