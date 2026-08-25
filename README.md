# Sound Browser
A desktop application for browsing, previewing, and analyzing sound effects in a selected directory.

## ✨ Features

- Recursively scans a selected directory for WAV sound effects.
- Displays audio metadata such as duration, sample rate, channel count, bit depth, encoding, and file size.
- Generates waveform previews for easier sound effect selection.
- Allows discovered sound effects to be played directly from the application.
- Allows generating a plain-text list of discovered sound effects for further analysis.

## ⚙️ Requirements

- Java 25 LTS
- Maven 3.9+

## ▶️ Run

```shell
mvn clean package

java -jar target/sound-browser-1.0.0.jar
```