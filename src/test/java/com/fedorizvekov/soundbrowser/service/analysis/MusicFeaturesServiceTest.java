package com.fedorizvekov.soundbrowser.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Files;
import java.nio.file.Path;
import com.fedorizvekov.soundbrowser.service.AudioDecoder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MusicFeaturesService")
class MusicFeaturesServiceTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    private static final double LOW_PEAK = Math.pow(10.0, -24.0 / 20.0);
    private static final double MID_PEAK = Math.pow(10.0, -12.0 / 20.0);
    private static final double HIGH_PEAK = 1.0;

    private static final double LOW_RMS = LOW_PEAK / Math.sqrt(2.0);
    private static final double MID_RMS = MID_PEAK / Math.sqrt(2.0);
    private static final double HIGH_RMS = HIGH_PEAK / Math.sqrt(2.0);

    private static final double DYNAMICS_RMS = Math.sqrt((LOW_RMS * LOW_RMS + MID_RMS * MID_RMS + HIGH_RMS * HIGH_RMS) / 3.0);

    private static final double DYNAMICS_CREST_FACTOR = HIGH_PEAK / DYNAMICS_RMS;

    private static final double DYNAMICS_MEAN_RMS = (LOW_RMS + MID_RMS + HIGH_RMS) / 3.0;

    private static final double DYNAMICS_RMS_VARIATION = Math.sqrt((Math.pow(LOW_RMS - DYNAMICS_MEAN_RMS, 2) + Math.pow(MID_RMS - DYNAMICS_MEAN_RMS, 2) + Math.pow(HIGH_RMS - DYNAMICS_MEAN_RMS, 2)) / 3.0) / DYNAMICS_MEAN_RMS;

    private final AudioDecoder audioDecoder = new AudioDecoder();
    private final MusicFeaturesService service = new MusicFeaturesService(audioDecoder);

    @TempDir
    Path tempDir;


    @Test
    @DisplayName("Should calculate amplitude features")
    void shouldCalculateAmplitudeFeatures() {

        var configuredService = new MusicFeaturesService(audioDecoder, 3);

        var metrics = configuredService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .amplitudeMetrics();

        assertAll(
                () -> assertThat(metrics.peak()).isCloseTo(HIGH_PEAK, within(0.0001)),
                () -> assertThat(metrics.rms()).isCloseTo(DYNAMICS_RMS, within(0.0001)),
                () -> assertThat(metrics.crestFactor()).isCloseTo(DYNAMICS_CREST_FACTOR, within(0.001)),
                () -> assertThat(metrics.rmsVariation()).isCloseTo(DYNAMICS_RMS_VARIATION, within(0.001)),
                () -> assertThat(metrics.rmsStartRatio()).isPositive(),
                () -> assertThat(metrics.rmsMiddleRatio()).isGreaterThan(metrics.rmsStartRatio()),
                () -> assertThat(metrics.rmsEndRatio()).isGreaterThan(metrics.rmsMiddleRatio()),
                () -> assertThat(metrics.peakPosition()).isBetween(0.6, 1.0),
                () -> assertThat(metrics.rmsEnvelope()).hasSize(3),
                () -> assertThat(metrics.rmsEnvelope()[0]).isCloseTo((float) LOW_RMS, within(0.0001f)),
                () -> assertThat(metrics.rmsEnvelope()[1]).isCloseTo((float) MID_RMS, within(0.0001f)),
                () -> assertThat(metrics.rmsEnvelope()[2]).isCloseTo((float) HIGH_RMS, within(0.0001f)),
                () -> assertThat(metrics.peakEnvelope()).hasSize(3),
                () -> assertThat(metrics.peakEnvelope()[0]).isCloseTo((float) LOW_PEAK, within(0.0001f)),
                () -> assertThat(metrics.peakEnvelope()[1]).isCloseTo((float) MID_PEAK, within(0.0001f)),
                () -> assertThat(metrics.peakEnvelope()[2]).isCloseTo((float) HIGH_PEAK, within(0.0001f))
        );
    }


    @Test
    @DisplayName("Should calculate stereo correlation")
    void shouldCalculateStereoCorrelation() {

        var correlation = service.analyze(AUDIO_DIR.resolve("stereo_phase_inverted.wav"))
                .orElseThrow()
                .stereoCorrelation();

        assertThat(correlation).isCloseTo(-1.0, within(0.000001));
    }


    @Test
    @DisplayName("Should calculate rhythm features")
    void shouldCalculateRhythmFeatures() {

        var metrics = service.analyze(AUDIO_DIR.resolve("rhythm_120bpm.wav"))
                .orElseThrow()
                .rhythmMetrics();

        assertAll(
                () -> assertThat(metrics.tempoBpm()).isCloseTo(120.0, within(1.0)),
                () -> assertThat(metrics.onsetRate()).isCloseTo(2.0, within(0.01)),
                () -> assertThat(metrics.energyVariation()).isCloseTo(1.6328491676280101, within(0.02))
        );
    }


    @Test
    @DisplayName("Should calculate spectral features")
    void shouldCalculateSpectralFeatures() {

        var metrics = service.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .spectralMetrics();

        assertAll(
                () -> assertThat(metrics.spectralCentroidHz()).isCloseTo(1_000.0, within(20.0)),
                () -> assertThat(metrics.spectralFlatness()).isLessThan(0.01),
                () -> assertThat(metrics.spectralRolloffHz()).isCloseTo(1_000.0, within(30.0)),
                () -> assertThat(metrics.spectralBandwidthHz()).isLessThan(100.0),
                () -> assertThat(metrics.spectralFlux()).isGreaterThanOrEqualTo(0.0),
                () -> assertThat(metrics.spectralCentroidVariationHz()).isGreaterThanOrEqualTo(0.0),
                () -> assertThat(metrics.subEnergy()).isLessThan(0.001),
                () -> assertThat(metrics.lowEnergy()).isLessThan(0.001),
                () -> assertThat(metrics.midEnergy()).isGreaterThan(0.999),
                () -> assertThat(metrics.highEnergy()).isLessThan(0.001),
                () -> assertThat(metrics.veryHighEnergy()).isLessThan(0.001)
        );
    }


    @Test
    @DisplayName("Should analyze 32-bit floating point PCM")
    void shouldAnalyzeFloatingPointPcm() {

        var features = service.analyze(AUDIO_DIR.resolve("signal_32bit.wav"))
                .orElseThrow();

        assertThat(features.amplitudeMetrics().peak()).isCloseTo(0.8, within(0.001));
    }


    // TODO after fix & update dependency vorbisspi
    @Disabled
    @Test
    @DisplayName("Should analyze OGG audio")
    void shouldAnalyzeOggAudio() {

        var features = service.analyze(AUDIO_DIR.resolve("signal_16bit.ogg"))
                .orElseThrow();

        assertAll(
                () -> assertThat(features.amplitudeMetrics().peak()).isPositive(),
                () -> assertThat(features.amplitudeMetrics().rms()).isPositive(),
                () -> assertThat(features.rhythmMetrics()).isNotNull(),
                () -> assertThat(features.spectralMetrics().spectralCentroidHz()).isPositive()
        );
    }


    @Test
    @DisplayName("Should return null stereo correlation for mono audio")
    void shouldReturnNullStereoCorrelationForMonoAudio() {

        var features = service.analyze(AUDIO_DIR.resolve("signal_16bit.wav"))
                .orElseThrow();

        assertThat(features.stereoCorrelation()).isNull();
    }


    @Test
    @DisplayName("Should use default envelope size")
    void shouldUseDefaultEnvelopeSize() {

        var metrics = service.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .amplitudeMetrics();

        assertAll(
                () -> assertThat(metrics.rmsEnvelope()).hasSize(64),
                () -> assertThat(metrics.peakEnvelope()).hasSize(64)
        );
    }


    @Test
    @DisplayName("Should use configured envelope size")
    void shouldUseConfiguredEnvelopeSize() {

        var configuredService = new MusicFeaturesService(audioDecoder, 16);

        var metrics = configuredService.analyze(AUDIO_DIR.resolve("dynamics_steps.wav"))
                .orElseThrow()
                .amplitudeMetrics();

        assertAll(
                () -> assertThat(metrics.rmsEnvelope()).hasSize(16),
                () -> assertThat(metrics.peakEnvelope()).hasSize(16)
        );
    }


    @Test
    @DisplayName("Should return empty for missing audio file")
    void shouldReturnEmptyForMissingAudioFile() {

        var result = service.analyze(tempDir.resolve("missing.wav"));

        assertThat(result).isEmpty();
    }


    @Test
    @DisplayName("Should return empty for invalid audio file")
    void shouldReturnEmptyForInvalidAudioFile() throws Exception {

        var file = tempDir.resolve("invalid.wav");
        Files.writeString(file, "not an audio file");

        var result = service.analyze(file);

        assertThat(result).isEmpty();
    }


    @ParameterizedTest(name = "[{index}] envelopePoints={0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("Should reject invalid envelope points")
    void shouldRejectInvalidEnvelopePoints(int envelopePoints) {

        assertThatThrownBy(() -> new MusicFeaturesService(audioDecoder, envelopePoints))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Envelope points must be greater than zero");
    }

}