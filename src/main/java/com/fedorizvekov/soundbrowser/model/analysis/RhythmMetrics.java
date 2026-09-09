package com.fedorizvekov.soundbrowser.model.analysis;

public record RhythmMetrics(
        double tempoBpm,
        double onsetRate,
        double energyVariation
) {
}
