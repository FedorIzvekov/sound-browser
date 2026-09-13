package com.fedorizvekov.soundbrowser.model.analysis;

public record SfxFeatures(
        ActivityMetrics activityMetrics,
        AmplitudeMetrics amplitudeMetrics,
        LoopMetrics loopMetrics,
        SpectralMetrics spectralMetrics,
        Double stereoCorrelation
) implements AudioFeatures {
}