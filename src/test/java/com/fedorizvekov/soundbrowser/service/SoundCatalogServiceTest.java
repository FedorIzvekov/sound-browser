package com.fedorizvekov.soundbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.UnsupportedAudioFileException;
import com.fedorizvekov.soundbrowser.model.AudioFileType;
import com.fedorizvekov.soundbrowser.model.AudioMetadata;
import com.fedorizvekov.soundbrowser.model.CatalogError;
import com.fedorizvekov.soundbrowser.model.SoundEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SoundCatalogService")
class SoundCatalogServiceTest {

    private static final AudioMetadata METADATA = new AudioMetadata(
            0.3, 44_100.0f, 1, 16,
            "PCM_SIGNED", false, 2, 13_230, "WAVE"
    );

    @TempDir
    Path tempDir;

    @Mock
    private AudioAnalyzer audioAnalyzer;

    @InjectMocks
    private SoundCatalogService service;


    @Test
    @DisplayName("Should recursively find supported audio files")
    void shouldRecursivelyFindSupportedAudioFiles() throws Exception {

        var nestedDirectory = Files.createDirectories(tempDir.resolve("nested"));
        var wavFile = Files.createFile(tempDir.resolve("first.WAV"));
        var oggFile = Files.createFile(nestedDirectory.resolve("second.OGG"));

        Files.createFile(tempDir.resolve("ignore.txt"));

        when(audioAnalyzer.analyze(wavFile)).thenReturn(METADATA);
        when(audioAnalyzer.analyze(oggFile)).thenReturn(METADATA);

        var result = service.load(tempDir);

        assertThat(result.entries())
                .extracting(SoundEntry::filename)
                .containsExactly("first.WAV", "second.OGG");

        assertThat(result.errors()).isEmpty();
    }


    @Test
    @DisplayName("Should create sound entry from analyzed file")
    void shouldCreateSoundEntryFromAnalyzedFile() throws Exception {

        var directory = Files.createDirectories(tempDir.resolve(Path.of("ui", "menu")));
        var file = Files.write(directory.resolve("click.ogg"), new byte[]{1, 2, 3});

        when(audioAnalyzer.analyze(file)).thenReturn(METADATA);

        var entry = service.load(tempDir).entries().getFirst();

        assertThat(entry).isEqualTo(new SoundEntry(file, Path.of("ui", "menu", "click.ogg"), "click.ogg", 3L, METADATA));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("Should collect unsupported audio error and continue scanning")
    @CsvSource({
            "broken.wav, WAV",
            "broken.ogg, OGG"
    })
    void shouldCollectUnsupportedAudioError(String filename, AudioFileType fileType) throws Exception {

        var brokenFile = Files.createFile(tempDir.resolve(filename));
        var validFile = Files.createFile(tempDir.resolve("valid.wav"));

        when(audioAnalyzer.analyze(brokenFile))
                .thenThrow(new UnsupportedAudioFileException("Invalid audio"));

        when(audioAnalyzer.analyze(validFile)).thenReturn(METADATA);

        var result = service.load(tempDir);

        assertThat(result.entries())
                .extracting(SoundEntry::filename)
                .containsExactly("valid.wav");

        assertThat(result.errors()).containsExactly(new CatalogError(
                brokenFile,
                fileType,
                CatalogError.Type.UNSUPPORTED_AUDIO,
                "Invalid audio"
        ));
    }


    @Test
    @DisplayName("Should collect unreadable audio error")
    void shouldCollectUnreadableAudioError() throws Exception {

        var file = Files.createFile(tempDir.resolve("unreadable.ogg"));

        when(audioAnalyzer.analyze(file)).thenThrow(new IOException("Cannot read file"));

        var result = service.load(tempDir);

        assertThat(result.entries()).isEmpty();

        assertThat(result.errors()).containsExactly(new CatalogError(
                file,
                AudioFileType.OGG,
                CatalogError.Type.FILE_UNREADABLE,
                "Cannot read file"
        ));
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