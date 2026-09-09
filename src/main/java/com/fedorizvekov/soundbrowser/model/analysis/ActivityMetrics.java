package com.fedorizvekov.soundbrowser.model.analysis;

public record ActivityMetrics(
        double leadingSilenceSeconds,
        double trailingSilenceSeconds,
        double activeDurationSeconds,
        double attackSeconds,
        int activitySegmentCount
) {
}