package com.fedorizvekov.soundbrowser.model.analysis;

public record SpectralMetrics(
        double spectralCentroidHz,
        double spectralFlatness,
        double spectralRolloffHz,
        double spectralBandwidthHz,
        double spectralFlux,
        double spectralCentroidVariationHz,
        double subEnergy,
        double lowEnergy,
        double midEnergy,
        double highEnergy,
        double veryHighEnergy
) {
}