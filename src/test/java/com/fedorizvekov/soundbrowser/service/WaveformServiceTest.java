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

@DisplayName("WaveformService")
class WaveformServiceTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");
    private static final int WAVEFORM_POINTS = 1_000;

    @TempDir
    Path tempDir;


    @ParameterizedTest(name = "[{index}] {0}, bucket {1}")
    @DisplayName("should build expected waveform bucket")
    @CsvSource({
            "test_signal_16bit.wav, 0,      0.0000000,          0.9972229",
            "test_signal_16bit.wav, 250,    0.046966553,        0.74902344",
            "test_signal_16bit.wav, 500,    -0.000030517578,    0.4981079",
            "test_signal_16bit.wav, 750,    0.015655518,        0.24902344",
            "test_signal_16bit.wav, 999,    -0.0010070801,      -0.000030517578",

            "test_signal_32bit.wav, 0,      0.0000000,          0.7995233",
            "test_signal_32bit.wav, 250,    -0.51461846,        0.59888226",
            "test_signal_32bit.wav, 500,    -0.39952582,        0.000000000000059188304",
            "test_signal_32bit.wav, 750,    -0.19961534,        0.17109288",
            "test_signal_32bit.wav, 999,    -0.0005447906,      -0.000004383081",

            "test_waveform.wav, 0,          0.0000000,          0.024536133",
            "test_waveform.wav, 250,        0.046936035,        0.48757935",
            "test_waveform.wav, 500,        0.031311035,        0.48388672",
            "test_waveform.wav, 750,        0.015655518,        0.4797058",
            "test_waveform.wav, 999,        -0.024536133,       -0.0002746582"
    })
    void shouldBuildExpectedWaveformBucket(
            String filename,
            int bucket,
            float expectedMinimum,
            float expectedMaximum
    ) {
        var waveformService = new WaveformService(WAVEFORM_POINTS);

        var result = waveformService.analyze(AUDIO_DIR.resolve(filename));

        assertThat(result).isPresent();

        var waveform = result.orElseThrow();

        assertAll(
                () -> assertThat(waveform.minimums()).hasSize(WAVEFORM_POINTS),
                () -> assertThat(waveform.maximums()).hasSize(WAVEFORM_POINTS),
                () -> assertThat(waveform.minimums()[bucket]).isEqualTo(expectedMinimum),
                () -> assertThat(waveform.maximums()[bucket]).isEqualTo(expectedMaximum)
        );
    }


    @Test
    @DisplayName("should return empty result for invalid WAV file")
    void shouldReturnEmptyResultForInvalidWavFile() throws Exception {
        var file = tempDir.resolve("invalid.wav");
        Files.writeString(file, "not a wav file");

        var waveformService = new WaveformService();

        var result = waveformService.analyze(file);

        assertThat(result).isEmpty();
    }


    @Test
    @DisplayName("should return empty result for missing WAV file")
    void shouldReturnEmptyResultForMissingWavFile() {
        var file = tempDir.resolve("missing.wav");
        var waveformService = new WaveformService();

        var result = waveformService.analyze(file);

        assertThat(result).isEmpty();
    }


    @Test
    @DisplayName("should reject zero waveform points")
    void shouldRejectZeroWaveformPoints() {
        assertThatThrownBy(() -> new WaveformService(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }


    @Test
    @DisplayName("should reject negative waveform points")
    void shouldRejectNegativeWaveformPoints() {
        assertThatThrownBy(() -> new WaveformService(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

}
