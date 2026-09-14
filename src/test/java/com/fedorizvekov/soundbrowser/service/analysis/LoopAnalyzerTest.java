package com.fedorizvekov.soundbrowser.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("LoopAnalyzer")
class LoopAnalyzerTest {

    private static final double SAMPLE_RATE = 1_000.0;
    private static final int TOTAL_FRAMES = 1_000;


    @Test
    @DisplayName("Should return low mismatch for seamless periodic signal")
    void shouldReturnLowMismatchForSeamlessPeriodicSignal() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var sample = Math.sin(2.0 * Math.PI * 20.0 * frame / SAMPLE_RATE);

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.amplitudeMismatch()).isCloseTo(0.0, within(0.000001)),
                () -> assertThat(metrics.waveformMismatch()).isLessThan(0.05),
                () -> assertThat(metrics.spectralMismatch()).isLessThan(0.05)
        );
    }


    @Test
    @DisplayName("Should calculate amplitude mismatch near loop boundary")
    void shouldCalculateAmplitudeMismatchNearLoopBoundary() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var sample = frame < 50 ? 1.0 : frame >= 950 ? 0.5 : 0.75;

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.amplitudeMismatch()).isCloseTo(0.5, within(0.000001));
    }


    @Test
    @DisplayName("Should detect waveform discontinuity")
    void shouldDetectWaveformDiscontinuity() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var sample = frame >= 995 ? -0.5 : 0.5;

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.waveformMismatch()).isGreaterThan(0.9);
    }


    @Test
    @DisplayName("Should detect discontinuity in one stereo channel")
    void shouldDetectDiscontinuityInOneStereoChannel() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 2, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var left = 0.5;

            var right = frame >= 995 ? -0.5 : 0.5;

            var sample = (left * left + right * right) / 2.0;

            analyzer.accept(new double[]{left, right}, sample);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.waveformMismatch()).isGreaterThan(0.9);
    }


    @Test
    @DisplayName("Should not report large mismatch for quiet seamless signal")
    void shouldNotReportLargeMismatchForQuietSeamlessSignal() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var sample = 0.00001 * Math.sin(2.0 * Math.PI * 20.0 * frame / SAMPLE_RATE);

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.waveformMismatch()).isLessThan(0.05);
    }


    @Test
    @DisplayName("Should detect spectral mismatch")
    void shouldDetectSpectralMismatch() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var frequency = frame < 100 ? 50.0 : frame >= 900 ? 300.0 : 100.0;

            var sample = Math.sin(2.0 * Math.PI * frequency * frame / SAMPLE_RATE);

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.spectralMismatch()).isGreaterThan(0.3);
    }


    @Test
    @DisplayName("Should return low spectral mismatch for same boundary spectrum")
    void shouldReturnLowSpectralMismatchForSameBoundarySpectrum() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var sample = Math.sin(2.0 * Math.PI * 50.0 * frame / SAMPLE_RATE);

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var metrics = analyzer.finish();

        assertThat(metrics.spectralMismatch()).isLessThan(0.05);
    }


    @Test
    @DisplayName("Should return zero metrics for silence")
    void shouldReturnZeroMetricsForSilence() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 2, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {
            analyzer.accept(new double[]{0.0, 0.0}, 0.0);
        }

        var metrics = analyzer.finish();

        assertAll(
                () -> assertThat(metrics.amplitudeMismatch()).isZero(),
                () -> assertThat(metrics.waveformMismatch()).isZero(),
                () -> assertThat(metrics.spectralMismatch()).isZero()
        );
    }


    @Test
    @DisplayName("Should return zero metrics without accepted frames")
    void shouldReturnZeroMetricsWithoutAcceptedFrames() {

        var metrics = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE).finish();

        assertAll(
                () -> assertThat(metrics.amplitudeMismatch()).isZero(),
                () -> assertThat(metrics.waveformMismatch()).isZero(),
                () -> assertThat(metrics.spectralMismatch()).isZero()
        );
    }


    @Test
    @DisplayName("Should return same metrics when finished twice")
    void shouldReturnSameMetricsWhenFinishedTwice() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 1, SAMPLE_RATE);

        for (var frame = 0; frame < TOTAL_FRAMES; frame++) {

            var sample = Math.sin(2.0 * Math.PI * 20.0 * frame / SAMPLE_RATE);

            analyzer.accept(new double[]{sample}, sample * sample);
        }

        var first = analyzer.finish();
        var second = analyzer.finish();

        assertThat(second).isEqualTo(first);
    }


    @Test
    @DisplayName("Should reject mismatched channel sample count")
    void shouldRejectMismatchedChannelSampleCount() {

        var analyzer = new LoopAnalyzer(TOTAL_FRAMES, 2, SAMPLE_RATE);

        assertThatThrownBy(() -> analyzer.accept(new double[]{0.5}, 0.25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Channel sample count does not match configured channels");
    }


    @Test
    @DisplayName("Should reject invalid total frame count")
    void shouldRejectInvalidTotalFrameCount() {

        assertThatThrownBy(() -> new LoopAnalyzer(0, 1, SAMPLE_RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Total frames must be greater than zero");
    }


    @ParameterizedTest(name = "[{index}] channels={0}")
    @DisplayName("Should reject invalid channel count")
    @ValueSource(ints = {
            0, -1
    })
    void shouldRejectInvalidChannelCount(int channels) {

        assertThatThrownBy(() -> new LoopAnalyzer(TOTAL_FRAMES, channels, SAMPLE_RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Channels must be greater than zero");
    }


    @ParameterizedTest(name = "[{index}] sampleRate={0}")
    @DisplayName("Should reject invalid sample rate")
    @ValueSource(doubles = {
            0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
    })
    void shouldRejectInvalidSampleRate(double sampleRate) {

        assertThatThrownBy(() -> new LoopAnalyzer(TOTAL_FRAMES, 1, sampleRate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sample rate must be greater than zero");
    }

}