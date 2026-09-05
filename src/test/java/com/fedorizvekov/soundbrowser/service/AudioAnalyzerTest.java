package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AudioAnalyzer")
class AudioAnalyzerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");
    private final AudioAnalyzer audioAnalyzer = new AudioAnalyzer();

    @TempDir
    Path tempDir;


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should analyze audio metadata")
    @CsvSource({
            "signal_16bit.wav, 0.300, 44100, 1, 16, PCM_SIGNED, false, 2, 13230, WAVE",
            "signal_32bit.wav, 0.500, 44100, 1, 32, PCM_FLOAT,  false, 4, 22050, WAVE",
            "waveform.wav,     0.250, 44100, 1, 16, PCM_SIGNED, false, 2, 11025, WAVE",
            "signal_16bit.ogg, 0.300, 44100, 1, -1, VORBISENC, false, 1, -1, OGG",
            "waveform.ogg,     0.250, 44100, 2, -1, VORBISENC, false, 1, -1, OGG"
    })
    void shouldAnalyzeAudioMetadata(
            String filename,
            double expectedDuration,
            float expectedSampleRate,
            int expectedChannels,
            int expectedSampleSize,
            String expectedEncoding,
            boolean expectedBigEndian,
            int expectedFrameSize,
            int expectedFrameLength,
            String expectedType
    ) throws Exception {

        var metadata = audioAnalyzer.analyze(AUDIO_DIR.resolve(filename));

        assertAll(
                () -> assertThat(metadata.durationSeconds()).isCloseTo(expectedDuration, within(0.0001)),
                () -> assertThat(metadata.sampleRate()).isEqualTo(expectedSampleRate),
                () -> assertThat(metadata.channels()).isEqualTo(expectedChannels),
                () -> assertThat(metadata.sampleSizeBits()).isEqualTo(expectedSampleSize),
                () -> assertThat(metadata.encoding()).isEqualTo(expectedEncoding),
                () -> assertThat(metadata.bigEndian()).isEqualTo(expectedBigEndian),
                () -> assertThat(metadata.frameSizeBytes()).isEqualTo(expectedFrameSize),
                () -> assertThat(metadata.frameLength()).isEqualTo(expectedFrameLength),
                () -> assertThat(metadata.type()).isEqualTo(expectedType)
        );

    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should reject invalid audio file")
    @ValueSource(strings = {"invalid.wav", "invalid.ogg"})
    void shouldRejectInvalidAudioFile(String filename) throws Exception {

        var file = tempDir.resolve(filename);

        Files.writeString(file, "not an audio file");

        assertThatThrownBy(() -> audioAnalyzer.analyze(file)).isInstanceOf(UnsupportedAudioFileException.class);
    }

}