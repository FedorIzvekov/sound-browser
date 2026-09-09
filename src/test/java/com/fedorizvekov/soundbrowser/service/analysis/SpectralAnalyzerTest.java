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
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SpectralAnalyzer")
class SpectralAnalyzerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    private static final double SAMPLE_RATE = 44_100.0;
    private static final double NYQUIST_HZ = SAMPLE_RATE / 2.0;
    private static final double MIN_ANALYSIS_HZ = 20.0;
    private static final double SUB_MAX_HZ = 80.0;
    private static final double LOW_MAX_HZ = 250.0;
    private static final double MID_MAX_HZ = 2_000.0;
    private static final double HIGH_MAX_HZ = 8_000.0;
    private static final double MAX_ANALYSIS_HZ = 20_000.0;
    private static final double FULL_SINE_POWER = 1.0 / 2.0;
    private static final double QUIET_SINE_POWER = 0.2 * 0.2 / 2.0;
    private static final double WHITE_NOISE_POWER = 1.0 / 3.0;
    private static final double DENSITY_ANALYZED_POWER = FULL_SINE_POWER + 5.0 * QUIET_SINE_POWER + noisePower(MIN_ANALYSIS_HZ, MAX_ANALYSIS_HZ);

    private final SfxFeaturesService sfxFeaturesService = new SfxFeaturesService(new AudioDecoder());


    private static double expectedDensityEnergy(double minFrequencyHz, double maxFrequencyHz, double tonePower) {

        return (tonePower + noisePower(minFrequencyHz, maxFrequencyHz)) / DENSITY_ANALYZED_POWER;
    }


    private static double noisePower(double minFrequencyHz, double maxFrequencyHz) {

        return WHITE_NOISE_POWER * (maxFrequencyHz - minFrequencyHz) / NYQUIST_HZ;
    }


    @Test
    @DisplayName("Should calculate spectral centroid")
    void shouldCalculateSpectralCentroid() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .spectralMetrics();

        assertThat(metrics.spectralCentroidHz()).isCloseTo(1_000.0, within(20.0));
    }


    @Test
    @DisplayName("Should calculate sub energy")
    void shouldCalculateSubEnergy() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("density_spectrum.wav"))
                .orElseThrow()
                .spectralMetrics();

        var expected = expectedDensityEnergy(MIN_ANALYSIS_HZ, SUB_MAX_HZ, 0.0);

        assertThat(metrics.subEnergy()).isCloseTo(expected, within(0.0002));
    }


    @Test
    @DisplayName("Should calculate low energy")
    void shouldCalculateLowEnergy() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("density_spectrum.wav"))
                .orElseThrow()
                .spectralMetrics();

        var expected = expectedDensityEnergy(SUB_MAX_HZ, LOW_MAX_HZ, QUIET_SINE_POWER);

        assertThat(metrics.lowEnergy()).isCloseTo(expected, within(0.001));
    }


    @Test
    @DisplayName("Should calculate mid energy")
    void shouldCalculateMidEnergy() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("density_spectrum.wav"))
                .orElseThrow()
                .spectralMetrics();

        var expected = expectedDensityEnergy(LOW_MAX_HZ, MID_MAX_HZ, FULL_SINE_POWER + 2.0 * QUIET_SINE_POWER);

        assertThat(metrics.midEnergy()).isCloseTo(expected, within(0.004));
    }


    @Test
    @DisplayName("Should calculate high energy")
    void shouldCalculateHighEnergy() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("density_spectrum.wav"))
                .orElseThrow()
                .spectralMetrics();

        var expected = expectedDensityEnergy(MID_MAX_HZ, HIGH_MAX_HZ, 2.0 * QUIET_SINE_POWER);

        assertThat(metrics.highEnergy()).isCloseTo(expected, within(0.003));
    }


    @Test
    @DisplayName("Should calculate very high energy")
    void shouldCalculateVeryHighEnergy() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("density_spectrum.wav"))
                .orElseThrow()
                .spectralMetrics();

        var expected = expectedDensityEnergy(HIGH_MAX_HZ, MAX_ANALYSIS_HZ, 0.0);

        assertThat(metrics.veryHighEnergy()).isCloseTo(expected, within(0.004));
    }


    @Test
    @DisplayName("Should normalize spectral energy")
    void shouldNormalizeSpectralEnergy() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("density_spectrum.wav"))
                .orElseThrow()
                .spectralMetrics();

        var totalEnergy = metrics.subEnergy() + metrics.lowEnergy() + metrics.midEnergy() + metrics.highEnergy() + metrics.veryHighEnergy();

        assertThat(totalEnergy).isCloseTo(1.0, within(0.000001));
    }


    @Test
    @DisplayName("Should place 1 kHz tone in mid band")
    void shouldPlaceOneKilohertzToneInMidBand() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .spectralMetrics();

        assertThat(metrics.midEnergy()).isGreaterThan(0.999);
    }


    @Test
    @DisplayName("Should return zero metrics for silence")
    void shouldReturnZeroMetricsForSilence() {

        var analyzer = new SpectralAnalyzer(SAMPLE_RATE);

        for (var index = 0; index < 4_096; index++) {
            analyzer.accept(0.0);
        }

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.spectralCentroidHz()).isZero(),
                () -> assertThat(metrics.subEnergy()).isZero(),
                () -> assertThat(metrics.lowEnergy()).isZero(),
                () -> assertThat(metrics.midEnergy()).isZero(),
                () -> assertThat(metrics.highEnergy()).isZero(),
                () -> assertThat(metrics.veryHighEnergy()).isZero()
        );
    }


    @Test
    @DisplayName("Should process incomplete final window")
    void shouldProcessIncompleteFinalWindow() {

        var analyzer = new SpectralAnalyzer(SAMPLE_RATE);

        acceptSine(analyzer, 1_000.0, 1_000);

        var metrics = analyzer.finish();

        assertThat(metrics.midEnergy()).isGreaterThan(0.98);
    }


    @Test
    @DisplayName("Should return same metrics when finished twice")
    void shouldReturnSameMetricsWhenFinishedTwice() {

        var analyzer = new SpectralAnalyzer(SAMPLE_RATE);

        acceptSine(analyzer, 440.0, 4_096);
        acceptSine(analyzer, 5_000.0, 1_000);

        var first = analyzer.finish();
        var second = analyzer.finish();

        assertThat(second).isEqualTo(first);
    }


    @Test
    @DisplayName("Should return zero metrics without samples")
    void shouldReturnZeroMetricsWithoutSamples() {

        var metrics = new SpectralAnalyzer(SAMPLE_RATE).finish();

        assertAll(
                () -> assertThat(metrics.spectralCentroidHz()).isZero(),
                () -> assertThat(metrics.subEnergy()).isZero(),
                () -> assertThat(metrics.lowEnergy()).isZero(),
                () -> assertThat(metrics.midEnergy()).isZero(),
                () -> assertThat(metrics.highEnergy()).isZero(),
                () -> assertThat(metrics.veryHighEnergy()).isZero()
        );
    }


    @ParameterizedTest(name = "[{index}] sampleRate={0}")
    @DisplayName("Should reject invalid sample rate")
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void shouldRejectInvalidSampleRate(double sampleRate) {

        assertThatThrownBy(() -> new SpectralAnalyzer(sampleRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sample rate must be greater than zero");
    }


    private void acceptSine(SpectralAnalyzer analyzer, double frequencyHz, int sampleCount) {

        for (var index = 0; index < sampleCount; index++) {
            analyzer.accept(Math.sin(2.0 * Math.PI * frequencyHz * index / SAMPLE_RATE));
        }
    }

}