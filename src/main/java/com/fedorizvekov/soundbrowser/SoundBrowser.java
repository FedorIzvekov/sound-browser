package com.fedorizvekov.soundbrowser;

import com.fedorizvekov.soundbrowser.service.AudioAnalyzer;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import javafx.application.Application;
import javafx.stage.Stage;

public final class SoundBrowser extends Application {

    @Override
    public void start(Stage stage) {

        var analyzer = new AudioAnalyzer();

        var waveformService = new WaveformService();

        var audioPlayer = new AudioPlayer();

    }

}
