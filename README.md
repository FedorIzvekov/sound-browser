# Sound Browser v1.0.14
A desktop application for browsing, previewing, filtering, and analyzing sound effects in a selected directory.

## ✨ Features

- Recursively scans a selected directory for WAV sound effects.
- Displays duration, sample rate, channel count, bit depth, encoding, and file size.
- Generates waveform previews for easier sound effect selection.
- Plays included sound effects sequentially from the current search results.
- Allows sounds to be manually included in or excluded from playback and export.
- Exports selected sounds to JSONL with metadata, audio features, and compact amplitude envelopes.

## ⚙️ Requirements

- Java 25 LTS

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
java -jar target/sound-browser-1.0.14.jar
```

### Windows

```powershell
.\mvnw.cmd clean package
java -jar target\sound-browser-1.0.14.jar
```