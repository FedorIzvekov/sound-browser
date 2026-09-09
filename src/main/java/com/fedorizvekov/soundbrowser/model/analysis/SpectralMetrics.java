package com.fedorizvekov.soundbrowser.model.analysis;

public record SpectralMetrics(
        double spectralCentroidHz,
        double subEnergy,
        double lowEnergy,
        double midEnergy,
        double highEnergy,
        double veryHighEnergy
) {
}