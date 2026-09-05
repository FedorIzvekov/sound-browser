package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AudioDecoder")
class AudioDecoderTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    private final AudioDecoder audioDecoder = new AudioDecoder();

    @TempDir
    Path tempDir;


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should preserve PCM audio format")
    @CsvSource({
            "signal_16bit.wav, PCM_SIGNED, 16, 2, 13230",
            "signal_32bit.wav, PCM_FLOAT,  32, 4, 22050",
            "waveform.wav,     PCM_SIGNED, 16, 2, 11025"
    })
    void shouldPreservePcmAudioFormat(
            String filename,
            String expectedEncoding,
            int expectedSampleSize,
            int expectedFrameSize,
            long expectedFrameLength
    ) throws Exception {

        try (var stream = audioDecoder.open(AUDIO_DIR.resolve(filename))) {

            var format = stream.getFormat();
            var firstFrame = stream.readNBytes(expectedFrameSize);

            assertAll(
                    () -> assertThat(format.getEncoding().toString()).isEqualTo(expectedEncoding),
                    () -> assertThat(format.getSampleRate()).isEqualTo(44_100.0f),
                    () -> assertThat(format.getFrameRate()).isEqualTo(44_100.0f),
                    () -> assertThat(format.getChannels()).isEqualTo(1),
                    () -> assertThat(format.getSampleSizeInBits()).isEqualTo(expectedSampleSize),
                    () -> assertThat(format.getFrameSize()).isEqualTo(expectedFrameSize),
                    () -> assertThat(stream.getFrameLength()).isEqualTo(expectedFrameLength),
                    () -> assertThat(firstFrame).hasSize(expectedFrameSize)
            );
        }
    }


    @Test
    @DisplayName("Should decode OGG Vorbis to signed 16-bit PCM")
    void shouldDecodeOggVorbisToSigned16BitPcm() throws Exception {

        try (var stream = audioDecoder.open(AUDIO_DIR.resolve("waveform.ogg"))) {

            var format = stream.getFormat();
            var decodedBytes = stream.readNBytes(format.getFrameSize() * 32);

            assertAll(
                    () -> assertThat(format.getEncoding()).isEqualTo(AudioFormat.Encoding.PCM_SIGNED),
                    () -> assertThat(format.getSampleRate()).isEqualTo(44_100.0f),
                    () -> assertThat(format.getFrameRate()).isEqualTo(44_100.0f),
                    () -> assertThat(format.getChannels()).isEqualTo(2),
                    () -> assertThat(format.getSampleSizeInBits()).isEqualTo(16),
                    () -> assertThat(format.getFrameSize()).isEqualTo(4),
                    () -> assertThat(format.isBigEndian()).isFalse(),
                    () -> assertThat(stream.getFrameLength()).isPositive(),
                    () -> assertThat(decodedBytes).hasSize(format.getFrameSize() * 32)
            );
        }
    }


    @Test
    @DisplayName("Should decode short OGG Vorbis file completely")
    void shouldDecodeShortOggVorbisFileCompletely() throws Exception {

        try (var stream = audioDecoder.open(AUDIO_DIR.resolve("waveform.ogg"))) {

            var decodedBytes = stream.readAllBytes();

            assertAll(
                    () -> assertThat(stream.getFormat().getEncoding()).isEqualTo(AudioFormat.Encoding.PCM_SIGNED),
                    () -> assertThat(stream.getFormat().getFrameSize()).isEqualTo(4),
                    () -> assertThat(stream.getFrameLength()).isEqualTo(11025L),
                    () -> assertThat(decodedBytes).hasSize(44_100)
            );
        }
    }


    @Test
    @DisplayName("Should reject invalid audio file")
    void shouldRejectInvalidAudioFile() throws Exception {

        var file = Files.writeString(tempDir.resolve("invalid.ogg"), "not an audio file");

        assertThatThrownBy(() -> {
            try (var ignored = audioDecoder.open(file)) {
            }
        }).isInstanceOf(UnsupportedAudioFileException.class);
    }


    @Test
    @DisplayName("Should reject missing audio file")
    void shouldRejectMissingAudioFile() {

        var file = tempDir.resolve("missing.ogg");

        assertThatThrownBy(() -> {
            try (var ignored = audioDecoder.open(file)) {
            }
        }).isInstanceOf(IOException.class);
    }

}