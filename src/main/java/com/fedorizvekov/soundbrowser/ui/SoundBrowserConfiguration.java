package com.fedorizvekov.soundbrowser.ui;

import com.fedorizvekov.soundbrowser.service.AudioAnalyzer;
import com.fedorizvekov.soundbrowser.service.AudioDecoder;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.SoundCatalogService;
import com.fedorizvekov.soundbrowser.service.WaveformService;
import com.fedorizvekov.soundbrowser.service.export.AudioFeaturesService;
import com.fedorizvekov.soundbrowser.service.export.JsonlExportService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SoundBrowserConfiguration extends Application {

    private static final double INITIAL_WIDTH = 1_200;
    private static final double INITIAL_HEIGHT = 800;
    private static final double MINIMUM_WIDTH = 800;
    private static final double MINIMUM_HEIGHT = 600;


    @Override
    public void start(Stage stage) {

        var analyzer = new AudioAnalyzer();
        var catalogService = new SoundCatalogService(analyzer);

        var audioDecoder = new AudioDecoder();
        var audioPlayer = new AudioPlayer(audioDecoder);
        var waveformService = new WaveformService(audioDecoder);
        var audioFeaturesService = new AudioFeaturesService(audioDecoder);
        var jsonlExportService = new JsonlExportService(audioFeaturesService);

        var view = new SoundBrowserView(catalogService, jsonlExportService, audioPlayer, waveformService);

        var scene = new Scene(view, INITIAL_WIDTH, INITIAL_HEIGHT);

        scene.getStylesheets().add(SoundBrowserConfiguration.class.getResource("/styles/sound-browser.css").toExternalForm());
        scene.getStylesheets().add(SoundBrowserConfiguration.class.getResource("/styles/audio-filter.css").toExternalForm());
        scene.getStylesheets().add(SoundBrowserConfiguration.class.getResource("/styles/list.css").toExternalForm());
        scene.getStylesheets().add(SoundBrowserConfiguration.class.getResource("/styles/player.css").toExternalForm());
        scene.getStylesheets().add(SoundBrowserConfiguration.class.getResource("/styles/waveform.css").toExternalForm());

        stage.setScene(scene);
        stage.setTitle("Sound Browser");
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);
        stage.setOnCloseRequest(event -> audioPlayer.close());
        stage.show();
    }

}