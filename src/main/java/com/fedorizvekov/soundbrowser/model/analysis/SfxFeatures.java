package com.fedorizvekov.soundbrowser.model.analysis;

public record SfxFeatures(
        ActivityMetrics activityMetrics,
        AmplitudeMetrics amplitudeMetrics,
        Double stereoCorrelation,
        SpectralMetrics spectralMetrics
) implements AudioFeatures {
}