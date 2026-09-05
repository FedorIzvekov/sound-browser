package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AudioPlayer")
class AudioPlayerTest {

    private static final Path FILE = Path.of("first.audio");
    private static final Path NEXT_FILE = Path.of("second.audio");

    @Mock
    private AudioDecoder audioDecoder;
    @Mock
    private AudioInputStream decodedStream;
    @Mock
    private Clip clip;

    private MockedStatic<AudioSystem> audioSystem;
    private AudioPlayer audioPlayer;


    @BeforeEach
    void setUp() {
        audioSystem = mockStatic(AudioSystem.class);
        audioPlayer = new AudioPlayer(audioDecoder);
    }


    @AfterEach
    void tearDown() {
        audioSystem.close();
    }


    @Test
    @DisplayName("Should play decoded audio")
    void shouldPlayDecodedAudio() throws Exception {

        stubPlayback();

        audioPlayer.play(FILE);

        assertAll(
                () -> verify(audioDecoder).open(FILE),
                () -> verify(clip).open(decodedStream),
                () -> verify(clip).start(),
                () -> verify(decodedStream).close(),
                () -> assertThat(audioPlayer.getCurrentFile()).isEqualTo(FILE)
        );
    }


    @Test
    @DisplayName("Should pause current audio when toggled")
    void shouldPauseCurrentAudioWhenToggled() throws Exception {

        stubPlayback();

        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(true);

        audioPlayer.play(FILE);
        audioPlayer.toggle(FILE);

        verify(clip).stop();
        assertThat(audioPlayer.isPaused()).isTrue();
    }


    @Test
    @DisplayName("Should resume current audio when toggled")
    void shouldResumeCurrentAudioWhenToggled() throws Exception {

        stubPlayback();

        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(false);
        when(clip.getFrameLength()).thenReturn(100);

        audioPlayer.play(FILE);
        audioPlayer.toggle(FILE);

        verify(clip, times(2)).start();
    }


    @Test
    @DisplayName("Should restart finished audio")
    void shouldRestartFinishedAudio() throws Exception {

        stubPlayback();

        when(clip.isOpen()).thenReturn(true);
        when(clip.isRunning()).thenReturn(false);
        when(clip.getFramePosition()).thenReturn(100);
        when(clip.getFrameLength()).thenReturn(100);

        audioPlayer.play(FILE);
        audioPlayer.resume();

        verify(clip, times(2)).setFramePosition(0);
    }


    @Test
    @DisplayName("Should replace current audio")
    void shouldReplaceCurrentAudio() throws Exception {

        var nextClip = mock(Clip.class);
        var nextStream = mock(AudioInputStream.class);

        when(clip.isOpen()).thenReturn(true);
        when(audioDecoder.open(FILE)).thenReturn(decodedStream);
        when(audioDecoder.open(NEXT_FILE)).thenReturn(nextStream);

        audioSystem.when(AudioSystem::getClip).thenReturn(clip, nextClip);

        audioPlayer.play(FILE);
        audioPlayer.toggle(NEXT_FILE);

        verify(clip).close();
        verify(nextClip).start();

        assertThat(audioPlayer.getCurrentFile()).isEqualTo(NEXT_FILE);
    }


    @Test
    @DisplayName("Should notify when playback reaches the end")
    void shouldNotifyWhenPlaybackReachesTheEnd() throws Exception {

        var finishedFile = new AtomicReference<Path>();
        var listenerCaptor = ArgumentCaptor.forClass(LineListener.class);

        stubPlayback();
        when(clip.getFrameLength()).thenReturn(100);

        audioPlayer.setOnPlaybackFinished(finishedFile::set);
        audioPlayer.play(FILE);

        verify(clip).addLineListener(listenerCaptor.capture());

        var listener = listenerCaptor.getValue();

        listener.update(new LineEvent(clip, LineEvent.Type.STOP, 99));
        assertThat(finishedFile).hasNullValue();

        listener.update(new LineEvent(clip, LineEvent.Type.STOP, 100));
        assertThat(finishedFile).hasValue(FILE);
    }


    @Test
    @DisplayName("Should stop current audio")
    void shouldStopCurrentAudio() throws Exception {

        stubPlayback();
        when(clip.isOpen()).thenReturn(true);

        audioPlayer.play(FILE);
        audioPlayer.stop();

        verify(clip).close();
        assertThat(audioPlayer.getCurrentFile()).isNull();
    }


    @Test
    @DisplayName("Should return playback position and duration")
    void shouldReturnPlaybackPositionAndDuration() throws Exception {

        stubPlayback();

        when(clip.isOpen()).thenReturn(true);
        when(clip.getMicrosecondPosition()).thenReturn(250_000L);
        when(clip.getMicrosecondLength()).thenReturn(1_500_000L);

        audioPlayer.play(FILE);

        assertThat(audioPlayer.getPositionSeconds()).isEqualTo(0.25);
        assertThat(audioPlayer.getDurationSeconds()).isEqualTo(1.5);
    }


    @Test
    @DisplayName("Should release resources when playback fails")
    void shouldReleaseResourcesWhenPlaybackFails() throws Exception {

        stubPlayback();

        doThrow(new LineUnavailableException("Audio line unavailable"))
                .when(clip)
                .open(decodedStream);

        assertThatThrownBy(() -> audioPlayer.play(FILE))
                .isInstanceOf(LineUnavailableException.class)
                .hasMessage("Audio line unavailable");

        verify(decodedStream).close();
        verify(clip).close();
    }


    private void stubPlayback() throws IOException, UnsupportedAudioFileException {
        audioSystem.when(AudioSystem::getClip).thenReturn(clip);
        when(audioDecoder.open(FILE)).thenReturn(decodedStream);
    }

}