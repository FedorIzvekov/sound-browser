package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("WaveformService")
class WaveformServiceTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");
    private static final int WAVEFORM_POINTS = 1_000;

    private final AudioDecoder audioDecoder = new AudioDecoder();
    private final WaveformService waveformService = new WaveformService(audioDecoder, WAVEFORM_POINTS);

    @TempDir
    Path tempDir;


    @ParameterizedTest(name = "[{index}] {0}, bucket {1}")
    @DisplayName("Should build expected waveform bucket")
    @CsvSource({
            "signal_16bit.wav,  0,      0.0000000,          0.9972229",
            "signal_16bit.wav,  250,    0.046966553,        0.74902344",
            "signal_16bit.wav,  500,    -0.000030517578,    0.4981079",
            "signal_16bit.wav,  750,    0.015655518,        0.24902344",
            "signal_16bit.wav,  999,    -0.0010070801,      -0.000030517578",

            "signal_32bit.wav,  0,      0.0000000,          0.7995233",
            "signal_32bit.wav,  250,    -0.51461846,        0.59888226",
            "signal_32bit.wav,  500,    -0.39952582,        0.000000000000059188304",
            "signal_32bit.wav,  750,    -0.19961534,        0.17109288",
            "signal_32bit.wav,  999,    -0.0005447906,      -0.000004383081",

            "waveform.wav,      0,      0.0000000,          0.024536133",
            "waveform.wav,      250,    0.046936035,        0.48757935",
            "waveform.wav,      500,    0.031311035,        0.48388672",
            "waveform.wav,      750,    0.015655518,        0.4797058",
            "waveform.wav,      999,    -0.024536133,       -0.0002746582",

//            "signal_16bit.ogg,  0,      0.00091552734,      0.9999695",
//            "signal_16bit.ogg,  250,    0.047912598,        0.7561035",
//            "signal_16bit.ogg,  500,    0.00091552734,      0.50289917",
//            "signal_16bit.ogg,  750,    0.015258789,        0.2515564",
//            "signal_16bit.ogg,  999,    -0.00091552734,     -0.000061035156",

            "waveform.ogg,      0,      -0.000061035156,    0.02420044",
            "waveform.ogg,      250,    0.047180176,        0.4935913",
            "waveform.ogg,      500,    0.03112793,         0.48886108",
            "waveform.ogg,      750,    0.01651001,         0.48602295",
            "waveform.ogg,      999,    -0.025756836,       -0.0010375977"
    })
    void shouldBuildExpectedWaveformBucket(String filename, int bucket, float expectedMinimum, float expectedMaximum) {

        var result = waveformService.analyze(AUDIO_DIR.resolve(filename));

        assertThat(result).as("Waveform should be created for %s", filename).isPresent();

        var waveform = result.orElseThrow();

        assertAll(
                () -> assertThat(waveform.minimums()).hasSize(WAVEFORM_POINTS),
                () -> assertThat(waveform.maximums()).hasSize(WAVEFORM_POINTS),
                () -> assertThat(waveform.minimums()[bucket]).isEqualTo(expectedMinimum),
                () -> assertThat(waveform.maximums()[bucket]).isEqualTo(expectedMaximum)
        );
    }


    @Test
    @DisplayName("Should return empty result for invalid audio file")
    void shouldReturnEmptyResultForInvalidAudioFile() throws Exception {

        var file = Files.writeString(tempDir.resolve("invalid.ogg"), "not an audio file");

        assertThat(waveformService.analyze(file)).isEmpty();
    }


    @Test
    @DisplayName("Should return empty result for missing audio file")
    void shouldReturnEmptyResultForMissingAudioFile() {

        var file = tempDir.resolve("missing.ogg");

        assertThat(waveformService.analyze(file)).isEmpty();
    }


    @ParameterizedTest(name = "[{index}] points = {0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("Should reject non-positive waveform points")
    void shouldRejectNonPositiveWaveformPoints(int points) {

        assertThatThrownBy(() -> new WaveformService(audioDecoder, points))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

}