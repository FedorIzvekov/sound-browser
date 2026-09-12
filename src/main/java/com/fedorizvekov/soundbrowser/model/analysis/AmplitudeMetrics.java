package com.fedorizvekov.soundbrowser.model.analysis;

public record AmplitudeMetrics(
        double peak,
        double rms,
        double crestFactor,
        double rmsVariation,
        double rmsStartRatio,
        double rmsMiddleRatio,
        double rmsEndRatio,
        double peakPosition,
        float[] rmsEnvelope,
        float[] peakEnvelope
) {
}