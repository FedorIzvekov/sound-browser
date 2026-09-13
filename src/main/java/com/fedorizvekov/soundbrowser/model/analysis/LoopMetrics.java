package com.fedorizvekov.soundbrowser.model.analysis;

public record LoopMetrics(
        double amplitudeMismatch,
        double waveformMismatch,
        double spectralMismatch
) {
}