package com.fedorizvekov.soundbrowser.model.analysis;

public record MusicFeatures(
        ActivityMetrics activityMetrics,
        AmplitudeMetrics amplitudeMetrics,
        LoopMetrics loopMetrics,
        RhythmMetrics rhythmMetrics,
        SpectralMetrics spectralMetrics,
        Double stereoCorrelation
) implements AudioFeatures {
}