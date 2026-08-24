package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AudioPlayer")
class AudioPlayerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    @Mock
    private Clip clip;

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private MockedStatic<AudioSystem> audioSystem;


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("should play WAV file")
    @ValueSource(strings = {
            "test_signal_16bit.wav",
            "test_signal_32bit.wav",
            "test_waveform.wav"
    })
    void shouldPlayWavFile(String filename) throws Exception {
        var file = AUDIO_DIR.resolve(filename);

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);

        assertAll(
                () -> verify(clip).open(any(AudioInputStream.class)),
                () -> verify(clip).setFramePosition(0),
                () -> verify(clip).start(),
                () -> assertThat(audioPlayer.getCurrentFile()).isEqualTo(file)
        );
    }


    @Test
    @DisplayName("should pause current audio")
    void shouldPauseCurrentAudio() throws Exception {
        var file = AUDIO_DIR.resolve("test_signal_16bit.wav");

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.isRunning()).thenReturn(true);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);
        audioPlayer.pause();

        verify(clip).stop();
    }


    @Test
    @DisplayName("should resume current audio")
    void shouldResumeCurrentAudio() throws Exception {
        var file = AUDIO_DIR.resolve("test_signal_16bit.wav");

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);

        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(false);
        when(clip.getFramePosition()).thenReturn(10);
        when(clip.getFrameLength()).thenReturn(100);

        audioPlayer.resume();

        verify(clip, times(2)).start();
    }


    @Test
    @DisplayName("should restart finished audio")
    void shouldRestartFinishedAudio() throws Exception {
        var file = AUDIO_DIR.resolve("test_signal_16bit.wav");

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);

        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(false);
        when(clip.getFramePosition()).thenReturn(100);
        when(clip.getFrameLength()).thenReturn(100);

        audioPlayer.resume();

        assertAll(
                () -> verify(clip, times(2)).setFramePosition(0),
                () -> verify(clip, times(2)).start()
        );
    }


    @Test
    @DisplayName("should stop current audio")
    void shouldStopCurrentAudio() throws Exception {
        var file = AUDIO_DIR.resolve("test_signal_16bit.wav");

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.isOpen()).thenReturn(true);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);
        audioPlayer.stop();

        assertAll(
                () -> verify(clip).stop(),
                () -> verify(clip).flush(),
                () -> verify(clip).close(),
                () -> assertThat(audioPlayer.getCurrentFile()).isNull()
        );
    }


    @Test
    @DisplayName("should stop previous audio before playing another")
    void shouldStopPreviousAudioBeforePlayingAnother() throws Exception {
        var firstFile = AUDIO_DIR.resolve("test_signal_16bit.wav");
        var secondFile = AUDIO_DIR.resolve("test_waveform.wav");

        var firstClip = mock(Clip.class);
        var secondClip = mock(Clip.class);

        when(firstClip.isOpen()).thenReturn(true);
        audioSystem.when(AudioSystem::getClip).thenReturn(firstClip, secondClip);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(firstFile);
        audioPlayer.play(secondFile);

        assertAll(
                () -> verify(firstClip).stop(),
                () -> verify(firstClip).flush(),
                () -> verify(firstClip).close(),
                () -> verify(secondClip).open(any(AudioInputStream.class)),
                () -> verify(secondClip).setFramePosition(0),
                () -> verify(secondClip).start(),
                () -> assertThat(audioPlayer.getCurrentFile()).isEqualTo(secondFile)
        );
    }


    @Test
    @DisplayName("should return playback position and duration in seconds")
    void shouldReturnPlaybackPositionAndDurationInSeconds() throws Exception {
        var file = AUDIO_DIR.resolve("test_signal_16bit.wav");

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);

        when(clip.isOpen()).thenReturn(true);
        when(clip.getMicrosecondPosition()).thenReturn(250_000L);
        when(clip.getMicrosecondLength()).thenReturn(1_500_000L);

        assertAll(
                () -> assertThat(audioPlayer.getPositionSeconds()).isEqualTo(0.25),
                () -> assertThat(audioPlayer.getDurationSeconds()).isEqualTo(1.5)
        );
    }

}
