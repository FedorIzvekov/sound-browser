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


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should detect tempo")
    @CsvSource({
            "rhythm_120bpm.wav, 120.0, 1.0",
            "rhythm_120bpm_half_double.wav, 120.0, 2.0",
            "rhythm_120bpm_swing.wav, 120.0, 2.0",
            "rhythm_120bpm_offbeat.wav, 120.0, 2.0"
    })
    void shouldDetectTempo(String filename, double expectedTempo, double tolerance) {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm()).isCloseTo(expectedTempo, within(tolerance));
    }


    @Test
    @DisplayName("Should calculate onset rate")
    void shouldCalculateOnsetRate() {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve("rhythm_120bpm_offbeat.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.onsetRate()).isCloseTo(2.0, within(0.01));
    }


    @Test
    @DisplayName("Should count onset at beginning of audio")
    void shouldCountOnsetAtBeginningOfAudio() {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve("rhythm_120bpm.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.onsetRate()).isCloseTo(2.0, within(0.01));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should calculate energy variation")
    @CsvSource({
            "dynamics_steps.wav, 0.923725",
            "rhythm_120bpm.wav, 1.6328491676280101"
    })
    void shouldCalculateEnergyVariation(String filename, double expectedCorrelation) {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.energyVariation()).isCloseTo(expectedCorrelation, within(0.001));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should not infer tempo from non-rhythmic audio")
    @ValueSource(strings = {
            "density_spectrum.wav",
            "key_c_major_a_minor.wav",
            "temporal_evolution.wav",
            "dynamics_steps.wav"
    })
    void shouldNotInferTempoFromNonRhythmicAudio(String filename) {

        var metrics = musicFeaturesService
                .analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .rhythmMetrics();

        assertThat(metrics.tempoBpm())
                .as(filename)
                .isZero();
    }


    @ParameterizedTest(name = "[{index}] {0} BPM")
    @DisplayName("Should detect tempo across supported range")
    @ValueSource(
            doubles = {50.0, 60.0, 90.0, 120.0, 130.0, 150.0, 180.0, 200.0}
    )
    void shouldDetectTempoAcrossSupportedRange(double bpm) {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        acceptClickTrack(analyzer, bpm, 12.0);

        assertThat(analyzer.finish().tempoBpm()).isCloseTo(bpm, within(2.0));
    }


    @Test
    @DisplayName("Should detect weak rhythm after strong transients")
    void shouldDetectWeakRhythmAfterStrongTransients() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        var totalFrames = (int) Math.round(SAMPLE_RATE * 12.0);
        var beatFrames = (int) Math.round(SAMPLE_RATE * 0.5);
        var samples = new double[totalFrames];

        for (var frame = 0; frame < samples.length; frame++) {

            samples[frame] = 0.5;
        }

        for (var beat = 0; beat < totalFrames; beat += beatFrames) {

            for (var frame = beat; frame < Math.min(totalFrames, beat + 10); frame++) {

                samples[frame] = 0.54;
            }
        }

        addTransient(samples, 200, 1.0);
        addTransient(samples, 700, 0.9);

        for (var sample : samples) {
            analyzer.accept(sample);
        }

        assertThat(analyzer.finish().tempoBpm()).isCloseTo(120.0, within(2.0));
    }


    @Test
    @DisplayName("Should return zero metrics for silence")
    void shouldReturnZeroMetricsForSilence() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.0, 5_000);

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

        accept(analyzer, 0.5, 5_000);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.tempoBpm()).isZero(),
                () -> assertThat(metrics.onsetRate()).isZero(),
                () -> assertThat(metrics.energyVariation()).isZero()
        );
    }


    @Test
    @DisplayName("Should return zero tempo with insufficient onsets")
    void shouldReturnZeroTempoWithInsufficientOnsets() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.0, 500);
        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.0, 1_000);
        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.0, 1_000);

        assertThat(analyzer.finish().tempoBpm()).isZero();
    }


    @Test
    @DisplayName("Should process incomplete final window")
    void shouldProcessIncompleteFinalWindow() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        accept(analyzer, 0.0, 100);
        accept(analyzer, 1.0, 5);

        var metrics = analyzer.finish();

        assertThat(metrics.energyVariation()).isPositive();
    }


    @Test
    @DisplayName("Should return same metrics when finished twice")
    void shouldReturnSameMetricsWhenFinishedTwice() {

        var analyzer = new MusicRhythmAnalyzer(SAMPLE_RATE);

        acceptClickTrack(analyzer, 120.0, 10.0);

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


    private void acceptClickTrack(MusicRhythmAnalyzer analyzer, double bpm, double durationSeconds) {

        var totalFrames = (int) Math.round(SAMPLE_RATE * durationSeconds);
        var beatFrames = (int) Math.round(SAMPLE_RATE * 60.0 / bpm);
        var clickFrames = (int) Math.round(SAMPLE_RATE * 0.01);

        for (var frame = 0; frame < totalFrames; frame++) {

            analyzer.accept(frame % beatFrames < clickFrames ? 1.0 : 0.0);
        }
    }


    private void addTransient(double[] samples, int start, double amplitude) {

        for (var frame = start; frame < Math.min(samples.length, start + 10); frame++) {

            samples[frame] = amplitude;
        }
    }


    private void accept(MusicRhythmAnalyzer analyzer, double amplitude, int frames) {

        for (var frame = 0; frame < frames; frame++) {

            analyzer.accept(amplitude);
        }
    }

}