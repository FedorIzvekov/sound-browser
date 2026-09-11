package com.fedorizvekov.soundbrowser.model.analysis;

public record TempoStability(
        double meanCorrelation,
        double standardDeviation,
        int segmentCount
) {

    public static final TempoStability EMPTY = new TempoStability(0.0, 0.0, 0);

}