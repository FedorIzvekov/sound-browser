package com.fedorizvekov.soundbrowser.model.analysis;

public record AmplitudeMetrics(
        double peak,
        double rms,
        double crestFactor,
        float[] rmsEnvelope,
        float[] peakEnvelope
) {
}