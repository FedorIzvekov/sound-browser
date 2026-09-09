package com.fedorizvekov.soundbrowser.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import com.fedorizvekov.soundbrowser.service.AudioDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MusicRhythmAnalyzer")
class MusicRhythmAnalyzerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");
    private static final double SAMPLE_RATE = 1_000.0;

    private final MusicFeaturesService musicFeaturesService = new MusicFeaturesService(new AudioDecoder());


    @ParameterizedTest(name = "[{index}] {0} -> {1} BPM")
    @DisplayName("Should detect tempo")
    @CsvSource({
            "rhythm_120bpm.wav, 120.0, 1.0",
            "rhythm_120bpm_half_double.wav, 120.0, 2.0",
            "rhythm_120bpm_swing.wav, 120.0, 2.0",
            "rhythm_120bpm_offbeat.wav, 120.0, 2.0"
    })
    void shouldDetectTempo(String filename, double expectedTempo, double tolerance) {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isCloseTo(expectedTempo, within(tolerance));
    }


    @Test
    @DisplayName("Should calculate onset rate")
    void shouldCalculateOnsetRate() {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm_offbeat.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.onsetRate()).isCloseTo(2.0, within(0.01));
    }


    @Test
    @DisplayName("Should count onset at beginning of audio")
    void shouldCountOnsetAtBeginningOfAudio() {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.onsetRate()).isCloseTo(2.0, within(0.01));
    }


    @Test
    @DisplayName("Should calculate energy variation")
    void shouldCalculateEnergyVariation() {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.energyVariation()).isCloseTo(0.923725, within(0.001));
    }


    @Test
    @DisplayName("Should calculate high energy variation for sparse rhythm")
    void shouldCalculateHighEnergyVariationForSparseRhythm() {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.energyVariation()).isCloseTo(7.0, within(0.02));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "density_spectrum.wav",
            "key_c_major_a_minor.wav",
            "temporal_evolution.wav"
    })
    @DisplayName("Should not infer tempo from non-rhythmic audio")
    void shouldNotInferTempoFromNonRhythmicAudio(String filename) {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isZero();
    }


    @Test
    @DisplayName("Should not infer tempo from dynamic level changes")
    void shouldNotInferTempoFromDynamicLevelChanges() {

        var metrics = musicFeaturesService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isZero();
    }


    @Test
    @DisplayName("Should return zero metrics for silence")
    void shouldReturnZeroMetricsForSilence() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.0, 1_000);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.tempoBpm()).isZero(),
                () -> assertThat(metrics.onsetRate()).isZero(),
                () -> assertThat(metrics.energyVariation()).isZero()
        );
    }


    @Test
    @DisplayName("Should return zero rhythm for constant signal")
    void shouldReturnZeroRhythmForConstantSignal() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.5, 1_000);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.tempoBpm()).isZero(),
                () -> assertThat(metrics.onsetRate()).isZero(),
                () -> assertThat(metrics.energyVariation()).isZero()
        );
    }


    @Test
    @DisplayName("Should process incomplete final window")
    void shouldProcessIncompleteFinalWindow() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.0, 10);
        accept(analyzer, 1.0, 5);

        var metrics = analyzer.finish();

        assertThat(metrics.energyVariation()).isPositive();
    }


    @Test
    @DisplayName("Should return same metrics when finished twice")
    void shouldReturnSameMetricsWhenFinishedTwice() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.0, 100);
        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.0, 100);

        var first = analyzer.finish();
        var second = analyzer.finish();

        assertThat(second).isEqualTo(first);
    }


    @Test
    @DisplayName("Should return zero metrics without frames")
    void shouldReturnZeroMetricsWithoutFrames() {

        var metrics = new MusicRhythmAnalyzer(SAMPLE_RATE).finish();

        assertAll(
                () -> assertThat(metrics.tempoBpm()).isZero(),
                () -> assertThat(metrics.onsetRate()).isZero(),
                () -> assertThat(metrics.energyVariation()).isZero()
        );
    }


    @ParameterizedTest(name = "[{index}] sampleRate={0}")
    @DisplayName("Should reject invalid sample rate")
    @ValueSource(
            doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}
    )
    void shouldRejectInvalidSampleRate(double sampleRate) {

        assertThatThrownBy(() -> new MusicRhythmAnalyzer(sampleRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sample rate must be greater than zero");
    }


    private void accept(MusicRhythmAnalyzer analyzer, double amplitude, int frames) {

        for (var frame = 0; frame < frames; frame++) {
            analyzer.accept(amplitude);
        }
    }

}