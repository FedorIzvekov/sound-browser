# Sound Browser v1.0.20

A desktop application designed to help game developers quickly browse, preview, analyze, and select audio content from large sound libraries.

Sound Browser makes it easier to explore large collections of sound effects, find suitable assets, preview them without switching between applications, and prepare structured audio data for AI-assisted analysis without uploading the original audio files.

<img src="/.img/browser.png" alt="Sound Browser" width="500">

## ✨ Features

* Recursively scans selected directories for supported audio files, including WAV and OGG.
* Displays duration, sample rate, channel count, bit depth, encoding, and file size.
* Generates waveform previews for easier sound selection.
* Plays sound effects directly from the current search results.
* Allows sounds to be manually included in or excluded from playback and export.
* Exports selected sounds to JSONL with metadata, audio features, and compact amplitude envelopes, making large sound libraries easier to analyze with AI without uploading the original audio files.

## ⚙️ Requirements

* Java 25 LTS

## ▶️ Run

### macOS / Linux

```bash
./mvnw javafx:run
```

### Windows

```powershell
.\mvnw.cmd javafx:run
```

## 📦 Build

### macOS / Linux

```bash
./mvnw clean package
java -jar target/sound-browser-1.0.20.jar
```

### Windows

```powershell
.\mvnw.cmd clean package
java -jar target\sound-browser-1.0.20.jar
```