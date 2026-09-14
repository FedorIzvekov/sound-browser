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

@DisplayName("ActivityAnalyzer")
class ActivityAnalyzerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");
    private static final double SAMPLE_RATE = 1_000.0;

    private final SfxFeaturesService sfxFeaturesService = new SfxFeaturesService(new AudioDecoder());
    private final ActivityAnalyzer analyzer = new ActivityAnalyzer(SAMPLE_RATE);


    @Test
    @DisplayName("Should calculate leading silence")
    void shouldCalculateLeadingSilence() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm_offbeat.wav"))
                .orElseThrow()
                .activityMetrics();

        assertThat(metrics.leadingSilenceSeconds()).isCloseTo(0.25, within(0.01));
    }


    @Test
    @DisplayName("Should calculate trailing silence")
    void shouldCalculateTrailingSilence() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm_offbeat.wav"))
                .orElseThrow()
                .activityMetrics();

        assertThat(metrics.trailingSilenceSeconds()).isCloseTo(0.235, within(0.01));
    }


    @Test
    @DisplayName("Should calculate active duration")
    void shouldCalculateActiveDuration() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm.wav"))
                .orElseThrow()
                .activityMetrics();

        assertThat(metrics.activeDurationSeconds()).isCloseTo(9.04, within(0.01));
    }


    @Test
    @DisplayName("Should calculate attack")
    void shouldCalculateAttack() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .activityMetrics();

        assertThat(metrics.attackSeconds()).isCloseTo(6.0, within(0.01));
    }


    @Test
    @DisplayName("Should count activity segments")
    void shouldCountActivitySegments() {

        var metrics = sfxFeaturesService.analyze(AUDIO_DIR.resolve("rhythm_120bpm.wav"))
                .orElseThrow()
                .activityMetrics();

        assertThat(metrics.activitySegmentCount()).isEqualTo(10);
    }


    @Test
    @DisplayName("Should interpret accepted value as frame mean square")
    void shouldInterpretAcceptedValueAsFrameMeanSquare() {

        for (var frame = 0; frame < 100; frame++) {
            analyzer.accept(0.25);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.activeDurationSeconds()).isCloseTo(0.1, within(0.000001));
    }


    @Test
    @DisplayName("Should count multiple onsets inside one activity segment")
    void shouldCountMultipleOnsetsInsideOneActivitySegment() {

        accept(analyzer, 1.0, 100);
        accept(analyzer, 0.2, 200);
        accept(analyzer, 1.0, 100);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.activitySegmentCount()).isEqualTo(1),
                () -> assertThat(metrics.onsetCount()).isEqualTo(2),
                () -> assertThat(metrics.onsetRate()).isCloseTo(5.0, within(0.000001))
        );
    }


    @Test
    @DisplayName("Should ignore small level changes as onsets")
    void shouldIgnoreSmallLevelChangesAsOnsets() {

        accept(analyzer, 1.0, 100);
        accept(analyzer, 0.7, 100);
        accept(analyzer, 1.0, 100);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.activitySegmentCount()).isEqualTo(1),
                () -> assertThat(metrics.onsetCount()).isEqualTo(1)
        );
    }


    @Test
    @DisplayName("Should count separated events as onsets")
    void shouldCountSeparatedEventsAsOnsets() {

        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.0, 90);

        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.0, 90);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.activitySegmentCount()).isEqualTo(2),
                () -> assertThat(metrics.onsetCount()).isEqualTo(2),
                () -> assertThat(metrics.onsetRate()).isCloseTo(10.0, within(0.000001))
        );
    }


    @Test
    @DisplayName("Should return full duration as leading silence for inactive signal")
    void shouldReturnFullDurationAsLeadingSilenceForInactiveSignal() {

        accept(analyzer, 0.0, 1_000);

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.leadingSilenceSeconds()).isEqualTo(1.0),
                () -> assertThat(metrics.trailingSilenceSeconds()).isZero(),
                () -> assertThat(metrics.activeDurationSeconds()).isZero(),
                () -> assertThat(metrics.attackSeconds()).isZero(),
                () -> assertThat(metrics.activitySegmentCount()).isZero(),
                () -> assertThat(metrics.onsetCount()).isZero(),
                () -> assertThat(metrics.onsetRate()).isZero()
        );
    }


    @Test
    @DisplayName("Should ignore activity below relative threshold")
    void shouldIgnoreActivityBelowRelativeThreshold() {

        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.005, 100);

        var metrics = analyzer.finish();

        assertThat(metrics.activeDurationSeconds()).isCloseTo(0.01, within(0.000001));
    }


    @ParameterizedTest(name = "[{index}] gap={0} ms -> {1} segments")
    @DisplayName("Should count events using event gap")
    @CsvSource({
            "70, 1",
            "80, 2"
    })
    void shouldCountEventsUsingEventGap(int gapFrames, int expectedSegments) {

        accept(analyzer, 1.0, 10);
        accept(analyzer, 0.0, gapFrames);
        accept(analyzer, 1.0, 10);

        assertThat(analyzer.finish().activitySegmentCount()).isEqualTo(expectedSegments);
    }


    @Test
    @DisplayName("Should process incomplete final window")
    void shouldProcessIncompleteFinalWindow() {

        accept(analyzer, 1.0, 5);

        assertThat(analyzer.finish().activeDurationSeconds()).isCloseTo(0.005, within(0.000001));
    }


    @Test
    @DisplayName("Should return zero metrics without frames")
    void shouldReturnZeroMetricsWithoutFrames() {

        var metrics = new ActivityAnalyzer(SAMPLE_RATE).finish();

        assertAll(
                () -> assertThat(metrics.leadingSilenceSeconds()).isZero(),
                () -> assertThat(metrics.trailingSilenceSeconds()).isZero(),
                () -> assertThat(metrics.activeDurationSeconds()).isZero(),
                () -> assertThat(metrics.attackSeconds()).isZero(),
                () -> assertThat(metrics.activitySegmentCount()).isZero(),
                () -> assertThat(metrics.onsetCount()).isZero(),
                () -> assertThat(metrics.onsetRate()).isZero()
        );
    }


    @ParameterizedTest(name = "[{index}] sampleRate={0}")
    @DisplayName("Should reject invalid sample rate")
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void shouldRejectInvalidSampleRate(double sampleRate) {

        assertThatThrownBy(() -> new ActivityAnalyzer(sampleRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sample rate must be greater than zero");
    }


    private void accept(ActivityAnalyzer analyzer, double amplitude, int frames) {

        for (var frame = 0; frame < frames; frame++) {
            analyzer.accept(amplitude * amplitude);
        }
    }

}