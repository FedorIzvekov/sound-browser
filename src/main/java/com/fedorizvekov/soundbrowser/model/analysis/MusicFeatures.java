package com.fedorizvekov.soundbrowser.model.analysis;

public record MusicFeatures(
        AmplitudeMetrics amplitudeMetrics,
        Double stereoCorrelation,
        RhythmMetrics rhythmMetrics,
        SpectralMetrics spectralMetrics
) implements AudioFeatures {
}