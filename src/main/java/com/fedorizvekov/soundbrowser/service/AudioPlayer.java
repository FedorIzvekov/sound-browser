package com.fedorizvekov.soundbrowser.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class AudioPlayer implements AutoCloseable {

    private final AudioDecoder audioDecoder;

    private Clip currentClip;
    private Path currentFile;
    private boolean paused;

    private Consumer<Path> onPlaybackFinished = file -> {};


    public AudioPlayer(AudioDecoder audioDecoder) {
        this.audioDecoder = audioDecoder;
    }


    public void setOnPlaybackFinished(Consumer<Path> handler) {
        onPlaybackFinished = handler;
    }


    public void toggle(Path file) throws IOException, UnsupportedAudioFileException, LineUnavailableException {

        if (!file.equals(currentFile)) {
            play(file);
            return;
        }

        if (isPlaying()) {
            pause();
        } else {
            resume();
        }
    }


    public void play(Path file) throws IOException, UnsupportedAudioFileException, LineUnavailableException {

        stop();

        var clip = AudioSystem.getClip();

        try (var stream = audioDecoder.open(file)) {

            clip.open(stream);

            var frameLength = clip.getFrameLength();
            var finishedHandler = onPlaybackFinished;

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && event.getFramePosition() >= frameLength) {
                    finishedHandler.accept(file);
                }
            });

            currentClip = clip;
            currentFile = file;
            paused = false;

            clip.setFramePosition(0);
            clip.start();

        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException exception) {
            currentClip = null;
            currentFile = null;
            paused = false;

            clip.close();
            throw exception;
        }
    }


    public void pause() {

        if (!isLoaded()) {
            return;
        }

        paused = true;
        currentClip.stop();
    }


    public void resume() {

        if (!isLoaded() || isPlaying()) {
            return;
        }

        if (isFinished()) {
            currentClip.setFramePosition(0);
        }

        paused = false;
        currentClip.start();
    }


    public void stop() {

        var clip = currentClip;

        currentClip = null;
        currentFile = null;
        paused = false;

        if (clip != null && clip.isOpen()) {
            clip.stop();
            clip.flush();
            clip.close();
        }
    }


    public boolean isPlaying() {
        return currentClip != null && currentClip.isRunning();
    }


    public boolean isPaused() {
        return paused;
    }


    public boolean isLoaded() {
        return currentClip != null && currentClip.isOpen();
    }


    public boolean isFinished() {
        return isLoaded() && currentClip.getFramePosition() >= currentClip.getFrameLength();
    }


    public double getPositionSeconds() {
        return isLoaded() ? currentClip.getMicrosecondPosition() / 1_000_000.0 : 0.0;
    }


    public double getDurationSeconds() {
        return isLoaded() ? currentClip.getMicrosecondLength() / 1_000_000.0 : 0.0;
    }


    public Path getCurrentFile() {
        return currentFile;
    }


    @Override
    public void close() {
        stop();
    }

}