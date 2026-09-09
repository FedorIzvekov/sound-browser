package com.fedorizvekov.soundbrowser.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.stream.Stream;

import com.fedorizvekov.soundbrowser.service.AudioDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StereoAnalyzer")
class StereoAnalyzerTest {

    private static final Path AUDIO_DIR = Path.of("src", "test", "resources", "audio");

    private final SfxFeaturesService sfxFeaturesService = new SfxFeaturesService(new AudioDecoder());


    private static Stream<Arguments> constantChannels() {

        return Stream.of(
                Arguments.of(new double[]{0.5, 0.5, 0.5, 0.5}, new double[]{-1.0, -0.5, 0.5, 1.0}),
                Arguments.of(new double[]{-1.0, -0.5, 0.5, 1.0}, new double[]{0.5, 0.5, 0.5, 0.5})
        );
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should calculate stereo correlation")
    @CsvSource({
            "stereo_identical.wav, 1.0, 0.000001",
            "stereo_phase_inverted.wav, -1.0, 0.000001",
            "stereo_independent_tones.wav, 0.0, 0.00001",
            "stereo_panning_sweep.wav, 0.636619772, 0.0001"
    })
    void shouldCalculateStereoCorrelation(String filename, double expectedCorrelation, double tolerance) {

        var correlation = sfxFeaturesService.analyze(AUDIO_DIR.resolve(filename))
                .orElseThrow()
                .stereoCorrelation();

        assertThat(correlation).isCloseTo(expectedCorrelation, within(tolerance));
    }


    @Test
    @DisplayName("Should detect uncorrelated stereo noise")
    void shouldDetectUncorrelatedStereoNoise() {

        var correlation = sfxFeaturesService.analyze(AUDIO_DIR.resolve("stereo_wide_noise.wav"))
                .orElseThrow()
                .stereoCorrelation();

        assertThat(correlation).isCloseTo(0.0, within(0.01));
    }


    @Test
    @DisplayName("Should ignore DC offset")
    void shouldIgnoreDcOffset() {

        var analyzer = new StereoAnalyzer();

        analyzer.accept(10.0, 20.0);
        analyzer.accept(11.0, 21.0);
        analyzer.accept(12.0, 22.0);
        analyzer.accept(13.0, 23.0);

        assertThat(analyzer.finish()).isCloseTo(1.0, within(0.000001));
    }


    @ParameterizedTest(name = "[{index}] left={0}, right={1}")
    @MethodSource("constantChannels")
    @DisplayName("Should return null when a channel has zero variance")
    void shouldReturnNullWhenChannelHasZeroVariance(double[] left, double[] right) {

        var analyzer = new StereoAnalyzer();

        for (var frame = 0; frame < left.length; frame++) {
            analyzer.accept(left[frame], right[frame]);
        }

        assertThat(analyzer.finish()).isNull();
    }


    @Test
    @DisplayName("Should return null without frames")
    void shouldReturnNullWithoutFrames() {

        assertThat(new StereoAnalyzer().finish()).isNull();
    }


    @Test
    @DisplayName("Should return null with one frame")
    void shouldReturnNullWithOneFrame() {

        var analyzer = new StereoAnalyzer();

        analyzer.accept(0.5, -0.5);

        assertThat(analyzer.finish()).isNull();
    }

}