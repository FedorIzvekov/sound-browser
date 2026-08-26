package com.fedorizvekov.soundbrowser.ui;

import static java.util.Objects.requireNonNull;

import com.fedorizvekov.soundbrowser.service.AudioAnalyzer;
import com.fedorizvekov.soundbrowser.service.AudioPlayer;
import com.fedorizvekov.soundbrowser.service.SoundCatalogService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SoundBrowserApplication extends Application {

    private static final double INITIAL_WIDTH = 1_200;
    private static final double INITIAL_HEIGHT = 800;
    private static final double MINIMUM_WIDTH = 800;
    private static final double MINIMUM_HEIGHT = 600;


    @Override
    public void start(Stage stage) {

        var analyzer = new AudioAnalyzer();
        var catalogService = new SoundCatalogService(analyzer);
        var audioPlayer = new AudioPlayer();

        var view = new SoundBrowserView(catalogService, audioPlayer);

        var scene = new Scene(view, INITIAL_WIDTH, INITIAL_HEIGHT);

        scene.getStylesheets().add(requireNonNull(SoundBrowserApplication.class.getResource("/styles/sound-browser.css")).toExternalForm());
        scene.getStylesheets().add(requireNonNull(SoundBrowserApplication.class.getResource("/styles/player.css")).toExternalForm());

        stage.setScene(scene);
        stage.setTitle("SFX Browser");
        stage.setMinWidth(MINIMUM_WIDTH);
        stage.setMinHeight(MINIMUM_HEIGHT);
        stage.show();
    }
}