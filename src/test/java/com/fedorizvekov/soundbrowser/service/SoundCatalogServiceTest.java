package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.AudioMetadata;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SoundCatalogService")
class SoundCatalogServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private AudioAnalyzer audioAnalyzer;


    @Test
    @DisplayName("Should recursively find WAV files")
    void shouldRecursivelyFindWavFiles() throws Exception {

        var nestedDirectory = Files.createDirectories(tempDir.resolve("nested"));
        var firstFile = Files.createFile(tempDir.resolve("first.wav"));
        var secondFile = Files.createFile(nestedDirectory.resolve("second.WAV"));

        Files.createFile(tempDir.resolve("ignore.txt"));

        var metadata = createMetadata();

        when(audioAnalyzer.analyze(firstFile)).thenReturn(metadata);
        when(audioAnalyzer.analyze(secondFile)).thenReturn(metadata);

        var service = new SoundCatalogService(audioAnalyzer);

        var result = service.load(tempDir);

        assertAll(
                () -> assertThat(result.entries()).hasSize(2),
                () -> assertThat(result.entries()).extracting(SoundEntry::filename).containsExactly("first.wav", "second.WAV"),
                () -> assertThat(result.errors()).isEmpty(),
                () -> verify(audioAnalyzer).analyze(firstFile),
                () -> verify(audioAnalyzer).analyze(secondFile)
        );
    }


    @Test
    @DisplayName("Should preserve analyzed metadata")
    void shouldPreserveAnalyzedMetadata() throws Exception {

        var file = Files.createFile(tempDir.resolve("sound.wav"));
        var metadata = createMetadata();

        when(audioAnalyzer.analyze(file)).thenReturn(metadata);

        var service = new SoundCatalogService(audioAnalyzer);

        var result = service.load(tempDir);
        var entry = result.entries().getFirst();

        assertAll(
                () -> assertThat(entry.path()).isEqualTo(file),
                () -> assertThat(entry.relativePath()).isEqualTo(Path.of("sound.wav")),
                () -> assertThat(entry.filename()).isEqualTo("sound.wav"),
                () -> assertThat(entry.sizeBytes()).isEqualTo(0),
                () -> assertThat(entry.metadata()).isSameAs(metadata)
        );
    }


    @Test
    @DisplayName("Should preserve relative file path")
    void shouldPreserveRelativeFilePath() throws Exception {

        var directory = Files.createDirectories(tempDir.resolve(Path.of("ui", "menu")));
        var file = Files.createFile(directory.resolve("click.wav"));

        when(audioAnalyzer.analyze(file)).thenReturn(createMetadata());

        var service = new SoundCatalogService(audioAnalyzer);

        var result = service.load(tempDir);

        assertThat(result.entries().getFirst().relativePath()).isEqualTo(Path.of("ui", "menu", "click.wav"));
    }


    @Test
    @DisplayName("Should ignore non-WAV files")
    void shouldIgnoreNonWavFiles() throws Exception {

        Files.createFile(tempDir.resolve("sound.mp3"));
        Files.createFile(tempDir.resolve("sound.ogg"));
        Files.createFile(tempDir.resolve("readme.txt"));

        var service = new SoundCatalogService(audioAnalyzer);

        var result = service.load(tempDir);

        assertAll(
                () -> assertThat(result.entries()).isEmpty(),
                () -> assertThat(result.errors()).isEmpty(),
                () -> verifyNoInteractions(audioAnalyzer)
        );
    }


    @Test
    @DisplayName("Should continue scanning when WAV analysis fails")
    void shouldContinueScanningWhenWavAnalysisFails() throws Exception {

        var brokenFile = Files.createFile(tempDir.resolve("broken.wav"));
        var validFile = Files.createFile(tempDir.resolve("valid.wav"));

        when(audioAnalyzer.analyze(brokenFile)).thenThrow(new UnsupportedAudioFileException("Invalid WAV"));
        when(audioAnalyzer.analyze(validFile)).thenReturn(createMetadata());

        var service = new SoundCatalogService(audioAnalyzer);

        var result = service.load(tempDir);

        assertAll(
                () -> assertThat(result.entries()).hasSize(1),
                () -> assertThat(result.entries().getFirst().filename()).isEqualTo("valid.wav"),
                () -> assertThat(result.errors()).hasSize(1),
                () -> assertThat(result.errors().getFirst().path()).isEqualTo(brokenFile),
                () -> assertThat(result.errors().getFirst().message()).isEqualTo("Invalid WAV")
        );
    }


    @Test
    @DisplayName("Should reject missing directory")
    void shouldRejectMissingDirectory() {

        var missingDirectory = tempDir.resolve("missing");
        var service = new SoundCatalogService(audioAnalyzer);

        assertThatThrownBy(() -> service.load(missingDirectory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }


    @Test
    @DisplayName("Should reject file instead of directory")
    void shouldRejectFileInsteadOfDirectory() throws Exception {

        var file = Files.createFile(tempDir.resolve("file.txt"));
        var service = new SoundCatalogService(audioAnalyzer);

        assertThatThrownBy(() -> service.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }


    private AudioMetadata createMetadata() {
        return new AudioMetadata(
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
    }

}
