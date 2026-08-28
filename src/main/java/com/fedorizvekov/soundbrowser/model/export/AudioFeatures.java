package com.fedorizvekov.soundbrowser.model.export;

public record AudioFeatures(
        double peak,
        double rms,
        double crestFactor,
        double silenceRatio,
        double peakTimeRatio,
        float[] rmsEnvelope,
        float[] peakEnvelope
) {
}
