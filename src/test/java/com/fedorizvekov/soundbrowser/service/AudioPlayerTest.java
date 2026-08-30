package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AudioPlayer")
class AudioPlayerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    @Mock
    private Clip clip;

    private MockedStatic<AudioSystem> audioSystem;

    private final Path file = AUDIO_DIR.resolve("test_signal_16bit.wav");


    @BeforeEach
    void setUp() {
        audioSystem = mockStatic(AudioSystem.class, CALLS_REAL_METHODS);
    }


    @AfterEach
    void tearDown() {
        audioSystem.close();
    }


    @Test
    @DisplayName("should call play when toggled file is not current")
    void shouldCallPlayWhenToggledFileIsNotCurrent() throws Exception {

        var audioPlayer = spy(new AudioPlayer());

        doNothing().when(audioPlayer).play(file);

        audioPlayer.toggle(file);

        assertAll(
                () -> verify(audioPlayer).play(file),
                () -> verify(audioPlayer, never()).isPlaying(),
                () -> verify(audioPlayer, never()).pause(),
                () -> verify(audioPlayer, never()).resume()
        );
    }


    @Test
    @DisplayName("should call pause when current audio is playing")
    void shouldCallPauseWhenCurrentAudioIsPlaying() throws Exception {

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);

        var audioPlayer = spy(new AudioPlayer());

        audioPlayer.play(file);

        clearInvocations(audioPlayer);
        doReturn(true).when(audioPlayer).isPlaying();
        doNothing().when(audioPlayer).pause();

        audioPlayer.toggle(file);

        assertAll(
                () -> verify(audioPlayer).isPlaying(),
                () -> verify(audioPlayer).pause(),
                () -> verify(audioPlayer, never()).play(any(Path.class)),
                () -> verify(audioPlayer, never()).resume()
        );
    }


    @Test
    @DisplayName("should call resume when current audio is not playing")
    void shouldCallResumeWhenCurrentAudioIsNotPlaying() throws Exception {

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);

        var audioPlayer = spy(new AudioPlayer());

        audioPlayer.play(file);

        clearInvocations(audioPlayer);
        doReturn(false).when(audioPlayer).isPlaying();
        doNothing().when(audioPlayer).resume();

        audioPlayer.toggle(file);

        assertAll(
                () -> verify(audioPlayer).isPlaying(),
                () -> verify(audioPlayer).resume(),
                () -> verify(audioPlayer, never()).play(any(Path.class)),
                () -> verify(audioPlayer, never()).pause()
        );
    }


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
                () -> verify(clip).addLineListener(any(LineListener.class)),
                () -> verify(clip).setFramePosition(0),
                () -> verify(clip).start(),
                () -> assertThat(audioPlayer.getCurrentFile()).isEqualTo(file),
                () -> assertThat(audioPlayer.isPaused()).isFalse()
        );
    }


    @Test
    @DisplayName("should notify when playback reaches the end")
    void shouldNotifyWhenPlaybackReachesTheEnd() throws Exception {

        var finishedFile = new AtomicReference<Path>();
        var listenerCaptor = ArgumentCaptor.forClass(LineListener.class);

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.getFrameLength()).thenReturn(100);

        var audioPlayer = new AudioPlayer();
        audioPlayer.setOnPlaybackFinished(finishedFile::set);

        audioPlayer.play(file);

        verify(clip).addLineListener(listenerCaptor.capture());

        var event = new LineEvent(clip, LineEvent.Type.STOP, 100);
        listenerCaptor.getValue().update(event);

        assertThat(finishedFile).hasValue(file);
    }


    @Test
    @DisplayName("should not notify when playback stops before the end")
    void shouldNotNotifyWhenPlaybackStopsBeforeTheEnd() throws Exception {

        var finishedFile = new AtomicReference<Path>();
        var listenerCaptor = ArgumentCaptor.forClass(LineListener.class);

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.getFrameLength()).thenReturn(100);

        var audioPlayer = new AudioPlayer();
        audioPlayer.setOnPlaybackFinished(finishedFile::set);

        audioPlayer.play(file);

        verify(clip).addLineListener(listenerCaptor.capture());

        var event = new LineEvent(clip, LineEvent.Type.STOP, 50);
        listenerCaptor.getValue().update(event);

        assertThat(finishedFile).hasNullValue();
    }


    @Test
    @DisplayName("should pause current audio")
    void shouldPauseCurrentAudio() throws Exception {

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.isOpen()).thenReturn(true);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);
        audioPlayer.pause();

        assertAll(
                () -> verify(clip).stop(),
                () -> assertThat(audioPlayer.isPaused()).isTrue()
        );
    }


    @Test
    @DisplayName("should resume current audio")
    void shouldResumeCurrentAudio() throws Exception {

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(false);
        when(clip.getFramePosition()).thenReturn(10);
        when(clip.getFrameLength()).thenReturn(100);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);
        audioPlayer.pause();
        audioPlayer.resume();

        assertAll(
                () -> verify(clip).stop(),
                () -> verify(clip, times(2)).start(),
                () -> assertThat(audioPlayer.isPaused()).isFalse()
        );
    }


    @Test
    @DisplayName("should restart finished audio")
    void shouldRestartFinishedAudio() throws Exception {

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(false);
        when(clip.getFramePosition()).thenReturn(100);
        when(clip.getFrameLength()).thenReturn(100);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);
        audioPlayer.resume();

        assertAll(
                () -> verify(clip, times(2)).setFramePosition(0),
                () -> verify(clip, times(2)).start(),
                () -> assertThat(audioPlayer.isPaused()).isFalse()
        );
    }


    @Test
    @DisplayName("should stop current audio")
    void shouldStopCurrentAudio() throws Exception {

        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(clip.isOpen()).thenReturn(true);

        var audioPlayer = new AudioPlayer();

        audioPlayer.play(file);
        audioPlayer.pause();
        audioPlayer.stop();

        assertAll(
                () -> verify(clip, times(2)).stop(),
                () -> verify(clip).flush(),
                () -> verify(clip).close(),
                () -> assertThat(audioPlayer.getCurrentFile()).isNull(),
                () -> assertThat(audioPlayer.isPaused()).isFalse()
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
                () -> verify(secondClip).addLineListener(any(LineListener.class)),
                () -> verify(secondClip).setFramePosition(0),
                () -> verify(secondClip).start(),
                () -> assertThat(audioPlayer.getCurrentFile()).isEqualTo(secondFile)
        );
    }


    @Test
    @DisplayName("should return playback position and duration in seconds")
    void shouldReturnPlaybackPositionAndDurationInSeconds() throws Exception {

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