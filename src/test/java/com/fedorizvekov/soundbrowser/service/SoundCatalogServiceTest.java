package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.AudioMetadata;
import com.fedorizvekov.soundbrowser.model.CatalogError;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SoundCatalogService")
class SoundCatalogServiceTest {

    private final AudioMetadata metadata = new AudioMetadata(
            0.3,
            44_100.0f,
            1,
            16,
            "PCM_SIGNED",
            false,
            2,
            13_230,
            "WAVE"
    );

    @TempDir
    Path tempDir;

    @Mock
    private AudioAnalyzer audioAnalyzer;

    @InjectMocks
    private SoundCatalogService service;


    @Test
    @DisplayName("Should recursively find WAV files")
    void shouldRecursivelyFindWavFiles() throws Exception {

        var nestedDirectory = Files.createDirectories(tempDir.resolve("nested"));
        var firstFile = Files.createFile(tempDir.resolve("first.wav"));
        var secondFile = Files.createFile(nestedDirectory.resolve("second.WAV"));

        Files.createFile(tempDir.resolve("ignore.txt"));

        when(audioAnalyzer.analyze(firstFile)).thenReturn(metadata);
        when(audioAnalyzer.analyze(secondFile)).thenReturn(metadata);

        var result = service.load(tempDir);

        assertAll(
                () -> assertThat(result.entries()).hasSize(2),
                () -> assertThat(result.entries())
                        .extracting(SoundEntry::filename)
                        .containsExactly("first.wav", "second.WAV"),
                () -> assertThat(result.errors()).isEmpty(),
                () -> assertThat(result.oggFiles()).isEmpty(),
                () -> verify(audioAnalyzer).analyze(firstFile),
                () -> verify(audioAnalyzer).analyze(secondFile)
        );
    }


    @Test
    @DisplayName("Should preserve analyzed metadata")
    void shouldPreserveAnalyzedMetadata() throws Exception {

        var file = Files.createFile(tempDir.resolve("sound.wav"));

        when(audioAnalyzer.analyze(file)).thenReturn(metadata);

        var result = service.load(tempDir).entries().getFirst();

        assertAll(
                () -> assertThat(result.path()).isEqualTo(file),
                () -> assertThat(result.relativePath()).isEqualTo(Path.of("sound.wav")),
                () -> assertThat(result.filename()).isEqualTo("sound.wav"),
                () -> assertThat(result.sizeBytes()).isZero(),
                () -> assertThat(result.metadata()).isSameAs(metadata)
        );
    }


    @Test
    @DisplayName("Should preserve relative file path")
    void shouldPreserveRelativeFilePath() throws Exception {

        var directory = Files.createDirectories(tempDir.resolve(Path.of("ui", "menu")));
        var file = Files.createFile(directory.resolve("click.wav"));

        when(audioAnalyzer.analyze(file)).thenReturn(metadata);

        var result = service.load(tempDir).entries().getFirst();

        assertThat(result.relativePath()).isEqualTo(Path.of("ui", "menu", "click.wav"));
    }


    @Test
    @DisplayName("Should collect OGG files without treating them as errors")
    void shouldCollectOggFilesWithoutTreatingThemAsErrors() throws Exception {

        var nestedDirectory = Files.createDirectories(tempDir.resolve("nested"));
        var firstOgg = Files.createFile(tempDir.resolve("first.ogg"));
        var secondOgg = Files.createFile(nestedDirectory.resolve("second.OGG"));

        var result = service.load(tempDir);

        assertAll(
                () -> assertThat(result.entries()).isEmpty(),
                () -> assertThat(result.errors()).isEmpty(),
                () -> assertThat(result.oggFiles()).containsExactly(firstOgg, secondOgg),
                () -> verifyNoInteractions(audioAnalyzer)
        );
    }


    @Test
    @DisplayName("Should continue scanning when WAV format is unsupported")
    void shouldContinueScanningWhenWavFormatIsUnsupported() throws Exception {

        var brokenFile = Files.createFile(tempDir.resolve("broken.wav"));
        var validFile = Files.createFile(tempDir.resolve("valid.wav"));

        when(audioAnalyzer.analyze(brokenFile)).thenThrow(new UnsupportedAudioFileException("Invalid WAV"));
        when(audioAnalyzer.analyze(validFile)).thenReturn(metadata);

        var result = service.load(tempDir);
        var error = result.errors().getFirst();

        assertAll(
                () -> assertThat(result.entries()).hasSize(1),
                () -> assertThat(result.entries().getFirst().filename()).isEqualTo("valid.wav"),
                () -> assertThat(result.errors()).hasSize(1),
                () -> assertThat(error.file()).isEqualTo(brokenFile),
                () -> assertThat(error.type()).isEqualTo(CatalogError.Type.UNSUPPORTED_AUDIO),
                () -> assertThat(error.message()).isEqualTo("Invalid WAV")
        );
    }


    @Test
    @DisplayName("Should classify unreadable WAV file")
    void shouldClassifyUnreadableWavFile() throws Exception {

        var file = Files.createFile(tempDir.resolve("unreadable.wav"));

        when(audioAnalyzer.analyze(file)).thenThrow(new IOException("Cannot read file"));

        var result = service.load(tempDir);
        var error = result.errors().getFirst();

        assertAll(
                () -> assertThat(result.entries()).isEmpty(),
                () -> assertThat(result.errors()).hasSize(1),
                () -> assertThat(error.file()).isEqualTo(file),
                () -> assertThat(error.type()).isEqualTo(CatalogError.Type.FILE_UNREADABLE),
                () -> assertThat(error.message()).isEqualTo("Cannot read file")
        );
    }


    @Test
    @DisplayName("Should reject missing directory")
    void shouldRejectMissingDirectory() {

        var missingDirectory = tempDir.resolve("missing");

        assertThatThrownBy(() -> service.load(missingDirectory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }


    @Test
    @DisplayName("Should reject file instead of directory")
    void shouldRejectFileInsteadOfDirectory() throws Exception {

        var file = Files.createFile(tempDir.resolve("file.txt"));

        assertThatThrownBy(() -> service.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

}
