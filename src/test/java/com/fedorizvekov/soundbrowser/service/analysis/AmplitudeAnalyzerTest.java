package com.fedorizvekov.soundbrowser.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import com.fedorizvekov.soundbrowser.model.analysis.AmplitudeMetrics;
import com.fedorizvekov.soundbrowser.service.AudioDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AmplitudeAnalyzer")
class AmplitudeAnalyzerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    private static final double LOW_PEAK = Math.pow(10.0, -24.0 / 20.0);
    private static final double MID_PEAK = Math.pow(10.0, -12.0 / 20.0);
    private static final double HIGH_PEAK = 1.0;

    private static final double LOW_RMS = LOW_PEAK / Math.sqrt(2.0);
    private static final double MID_RMS = MID_PEAK / Math.sqrt(2.0);
    private static final double HIGH_RMS = HIGH_PEAK / Math.sqrt(2.0);

    private static final double DYNAMICS_RMS = Math.sqrt((LOW_RMS * LOW_RMS + MID_RMS * MID_RMS + HIGH_RMS * HIGH_RMS) / 3.0);

    private static final double DYNAMICS_CREST_FACTOR = HIGH_PEAK / DYNAMICS_RMS;


    @Test
    @DisplayName("Should calculate peak")
    void shouldCalculatePeak() {

        var metrics = analyze("dynamics_steps.wav", 3);

        assertThat(metrics.peak()).isCloseTo(HIGH_PEAK, within(0.0001));
    }


    @Test
    @DisplayName("Should calculate RMS")
    void shouldCalculateRms() {

        var metrics = analyze("dynamics_steps.wav", 3);

        assertThat(metrics.rms()).isCloseTo(DYNAMICS_RMS, within(0.0001));
    }


    @Test
    @DisplayName("Should calculate crest factor")
    void shouldCalculateCrestFactor() {

        var metrics = analyze("dynamics_steps.wav", 3);

        assertThat(metrics.crestFactor()).isCloseTo(DYNAMICS_CREST_FACTOR, within(0.0001));
    }


    @Test
    @DisplayName("Should calculate RMS envelope")
    void shouldCalculateRmsEnvelope() {

        var envelope = analyze("dynamics_steps.wav", 3).rmsEnvelope();

        assertAll(
                () -> assertThat(envelope[0]).isCloseTo((float) LOW_RMS, within(0.0001f)),
                () -> assertThat(envelope[1]).isCloseTo((float) MID_RMS, within(0.0001f)),
                () -> assertThat(envelope[2]).isCloseTo((float) HIGH_RMS, within(0.0001f))
        );
    }


    @Test
    @DisplayName("Should calculate peak envelope")
    void shouldCalculatePeakEnvelope() {

        var envelope = analyze("dynamics_steps.wav", 3).peakEnvelope();

        assertAll(
                () -> assertThat(envelope[0]).isCloseTo((float) LOW_PEAK, within(0.0001f)),
                () -> assertThat(envelope[1]).isCloseTo((float) MID_PEAK, within(0.0001f)),
                () -> assertThat(envelope[2]).isCloseTo((float) HIGH_PEAK, within(0.0001f))
        );
    }


    @Test
    @DisplayName("Should calculate RMS across stereo channels")
    void shouldCalculateRmsAcrossStereoChannels() {

        var metrics = analyze("stereo_identical.wav", 1);
        var expectedRms = 0.5 / Math.sqrt(2.0);

        assertThat(metrics.rms()).isCloseTo(expectedRms, within(0.0001));
    }


    @Test
    @DisplayName("Should distribute uneven frames across envelope buckets")
    void shouldDistributeUnevenFramesAcrossEnvelopeBuckets() {

        var analyzer = new AmplitudeAnalyzer(5, 1, 2);

        analyzer.accept(0, 0.0, 0.0);
        analyzer.accept(1, 0.0, 0.0);
        analyzer.accept(2, 0.0, 0.0);
        analyzer.accept(3, 0.0, 0.0);
        analyzer.accept(4, 1.0, 1.0);

        var envelope = analyzer.finish().rmsEnvelope();

        assertAll(
                () -> assertThat(envelope[0]).isZero(),
                () -> assertThat(envelope[1]).isCloseTo((float) (1.0 / Math.sqrt(2.0)), within(0.000001f))
        );
    }


    @Test
    @DisplayName("Should limit envelope size to total frame count")
    void shouldLimitEnvelopeSizeToTotalFrameCount() {

        var metrics = new AmplitudeAnalyzer(4, 1, 64).finish();

        assertAll(
                () -> assertThat(metrics.rmsEnvelope()).hasSize(4),
                () -> assertThat(metrics.peakEnvelope()).hasSize(4)
        );
    }


    @Test
    @DisplayName("Should return zero metrics without accepted frames")
    void shouldReturnZeroMetricsWithoutAcceptedFrames() {

        var metrics = new AmplitudeAnalyzer(4, 1, 4).finish();

        assertAll(
                () -> assertThat(metrics.peak()).isZero(),
                () -> assertThat(metrics.rms()).isZero(),
                () -> assertThat(metrics.crestFactor()).isZero(),
                () -> assertThat(metrics.rmsEnvelope()).containsOnly(0.0f),
                () -> assertThat(metrics.peakEnvelope()).containsOnly(0.0f)
        );
    }


    @ParameterizedTest(name = "[{index}] totalFrames={0}, channels={1}, envelopePoints={2}")
    @DisplayName("Should reject invalid constructor arguments")
    @CsvSource({
            "0, 1, 1, Total frames must be greater than zero",
            "1, 0, 1, Channels must be greater than zero",
            "1, 1, 0, Envelope points must be greater than zero"
    })
    void shouldRejectInvalidConstructorArguments(long totalFrames, int channels, int envelopePoints, String expectedMessage) {

        assertThatThrownBy(() -> new AmplitudeAnalyzer(totalFrames, channels, envelopePoints))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }


    private AmplitudeMetrics analyze(String filename, int envelopePoints) {

        var service = new SfxFeaturesService(new AudioDecoder(), envelopePoints);

        return service.analyze(AUDIO_DIR.resolve(filename)).orElseThrow().amplitudeMetrics();
    }

}