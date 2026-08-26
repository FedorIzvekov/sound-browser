package com.fedorizvekov.soundbrowser.service;

import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class AudioPlayer implements AutoCloseable {

    private Clip currentClip;
    private Path currentFile;


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

        try (var stream = AudioSystem.getAudioInputStream(file.toFile())) {

            clip.open(stream);

        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | RuntimeException exception) {
            clip.close();
            throw exception;
        }

        currentClip = clip;
        currentFile = file;

        currentClip.setFramePosition(0);
        currentClip.start();
    }


    public void pause() {
        if (isPlaying()) {
            currentClip.stop();
        }
    }


    public void resume() {
        if (!isLoaded() || isPlaying()) {
            return;
        }

        if (isFinished()) {
            currentClip.setFramePosition(0);
        }

        currentClip.start();
    }


    public void stop() {
        if (currentClip == null) {
            currentFile = null;
            return;
        }

        if (currentClip.isOpen()) {
            currentClip.stop();
            currentClip.flush();
            currentClip.close();
        }

        currentClip = null;
        currentFile = null;
    }


    public boolean isPlaying() {
        return currentClip != null && currentClip.isRunning();
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
