package com.fedorizvekov.soundbrowser.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;

import com.fedorizvekov.soundbrowser.service.AudioDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MusicRhythmAnalyzer generated music")
class MusicRhythmGeneratedAudioTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    private final MusicFeaturesService musicFeaturesService = new MusicFeaturesService(new AudioDecoder());


    @ParameterizedTest(name = "[{index}] {0} -> {1} BPM")
    @DisplayName("Should detect BPM from generated musical audio")
    @CsvSource({
            "rhythm_060bpm.wav, 60.0, 0.5",
            "rhythm_090bpm.wav, 90.0, 0.5",
            "rhythm_110bpm.wav, 110.0, 1.0",
            "rhythm_120bpm.wav, 120.0, 0.5",
            "rhythm_128bpm.wav, 128.0, 0.5",
            "rhythm_140bpm.wav, 140.0, 0.5",
            "rhythm_160bpm.wav, 160.0, 2.5",
            "rhythm_180bpm.wav, 180.0, 2.0"
    })
    void shouldDetectBpmFromGeneratedMusic(String filename, double expectedBpm, double tolerance) {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isCloseTo(expectedBpm, within(tolerance));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should preserve 120 BPM under realistic rhythm variations")
    @CsvSource({
            "rhythm_120bpm_swing.wav, 120.0",
            "rhythm_120bpm_jitter.wav, 120.0",
            "rhythm_120bpm_missing.wav, 120.0",
            "rhythm_120bpm_alternating.wav, 120.0"
    })
    void shouldPreserveBpmUnderRhythmVariations(String filename, double expectedBpm) {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isCloseTo(expectedBpm, within(0.5));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should detect BPM in dense mixes")
    @CsvSource({
            "rhythm_140bpm_metal_dense.wav, 140.0"
    })
    void shouldDetectBpmInDenseMix(String filename, double expectedBpm) {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isCloseTo(expectedBpm, within(0.5));
    }

}