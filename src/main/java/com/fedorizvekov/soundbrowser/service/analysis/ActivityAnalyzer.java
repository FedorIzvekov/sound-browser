package com.fedorizvekov.soundbrowser.service.analysis;

import java.util.Arrays;

import com.fedorizvekov.soundbrowser.model.analysis.ActivityMetrics;

public final class ActivityAnalyzer {

    private static final double WINDOW_SECONDS = 0.01;

    private static final double ABSOLUTE_ACTIVITY_THRESHOLD_DBFS = -60.0;
    private static final double RELATIVE_ACTIVITY_THRESHOLD_DB = -40.0;

    private static final double ABSOLUTE_ACTIVITY_THRESHOLD = Math.pow(10.0, ABSOLUTE_ACTIVITY_THRESHOLD_DBFS / 20.0);
    private static final double RELATIVE_ACTIVITY_THRESHOLD = Math.pow(10.0, RELATIVE_ACTIVITY_THRESHOLD_DB / 20.0);

    private static final double EVENT_GAP_SECONDS = 0.08;
    private static final double ATTACK_LEVEL_RATIO = 0.9;

    private static final double ONSET_RISE_DB = 6.0;
    private static final double ONSET_RISE_RATIO = Math.pow(10.0, ONSET_RISE_DB / 20.0);
    private static final double ONSET_LOOKBACK_SECONDS = 0.05;
    private static final double ONSET_MIN_INTERVAL_SECONDS = 0.05;

    private static final int INITIAL_WINDOW_CAPACITY = 1024;
    private final double sampleRate;
    private final int windowFrames;

    private double[] windowRms = new double[INITIAL_WINDOW_CAPACITY];
    private int windowRmsSize;

    private long totalFrames;
    private int currentWindowFrames;
    private double currentWindowSquareSum;


    public ActivityAnalyzer(double sampleRate) {

        if (!Double.isFinite(sampleRate) || sampleRate <= 0.0) {
            throw new IllegalArgumentException("Sample rate must be greater than zero");
        }

        this.sampleRate = sampleRate;
        this.windowFrames = Math.max(1, (int) Math.round(sampleRate * WINDOW_SECONDS));
    }


    public void accept(double frameMeanSquare) {

        currentWindowSquareSum += frameMeanSquare;
        currentWindowFrames++;
        totalFrames++;

        if (currentWindowFrames == windowFrames) {
            flushWindow();
        }
    }


    public ActivityMetrics finish() {

        if (currentWindowFrames > 0) {
            flushWindow();
        }

        var durationSeconds = totalFrames / sampleRate;

        if (windowRmsSize == 0) {
            return emptyMetrics();
        }

        var maximumWindowRms = 0.0;

        for (var index = 0; index < windowRmsSize; index++) {
            maximumWindowRms = Math.max(maximumWindowRms, windowRms[index]);
        }

        if (maximumWindowRms <= ABSOLUTE_ACTIVITY_THRESHOLD) {
            return new ActivityMetrics(durationSeconds, 0.0, 0.0, 0.0, 0, 0, 0.0);
        }

        var activityThreshold = Math.max(ABSOLUTE_ACTIVITY_THRESHOLD, maximumWindowRms * RELATIVE_ACTIVITY_THRESHOLD);

        var active = new boolean[windowRmsSize];
        var firstActive = -1;
        var lastActive = -1;

        for (var index = 0; index < windowRmsSize; index++) {

            active[index] = windowRms[index] > activityThreshold;

            if (!active[index]) {
                continue;
            }

            if (firstActive < 0) {
                firstActive = index;
            }

            lastActive = index;
        }

        if (firstActive < 0) {
            return new ActivityMetrics(durationSeconds, 0.0, 0.0, 0.0, 0, 0, 0.0);
        }

        var firstActiveFrame = (long) firstActive * windowFrames;
        var lastActiveFrameExclusive = Math.min(totalFrames, (long) (lastActive + 1) * windowFrames);

        var leadingSilenceSeconds = firstActiveFrame / sampleRate;
        var trailingSilenceSeconds = (totalFrames - lastActiveFrameExclusive) / sampleRate;

        var activeDurationSeconds = calculateActiveDurationSeconds(active, firstActive, lastActive);

        var eventGapWindows = Math.max(1, (int) Math.round(EVENT_GAP_SECONDS * sampleRate / windowFrames));

        var activitySegmentCount = countEvents(active, firstActive, lastActive, eventGapWindows);

        var attackSeconds = calculateAttackSeconds(active, firstActive, lastActive, eventGapWindows);

        var onsetCount = countOnsets(active, firstActive, lastActive, activityThreshold);

        var onsetRate = durationSeconds > 0.0 ? onsetCount / durationSeconds : 0.0;

        return new ActivityMetrics(
                leadingSilenceSeconds,
                trailingSilenceSeconds,
                activeDurationSeconds,
                attackSeconds,
                activitySegmentCount,
                onsetCount,
                onsetRate
        );
    }


    private void flushWindow() {

        addWindowRms(Math.sqrt(currentWindowSquareSum / currentWindowFrames));
        currentWindowSquareSum = 0.0;
        currentWindowFrames = 0;
    }


    private void addWindowRms(double rms) {

        if (windowRmsSize == windowRms.length) {
            windowRms = Arrays.copyOf(windowRms, windowRms.length * 2);
        }

        windowRms[windowRmsSize++] = rms;
    }


    private double calculateActiveDurationSeconds(boolean[] active, int firstActive, int lastActive) {

        long activeFrames = 0;

        for (var index = firstActive; index <= lastActive; index++) {

            if (!active[index]) {
                continue;
            }

            var windowStartFrame = (long) index * windowFrames;
            var windowEndFrame = Math.min(totalFrames, windowStartFrame + windowFrames);

            activeFrames += windowEndFrame - windowStartFrame;
        }

        return activeFrames / sampleRate;
    }


    private int countEvents(boolean[] active, int firstActive, int lastActive, int eventGapWindows) {

        var eventCount = 0;
        var inEvent = false;
        var inactiveWindows = 0;

        for (var index = firstActive; index <= lastActive; index++) {

            if (active[index]) {

                if (!inEvent) {
                    eventCount++;
                    inEvent = true;
                }

                inactiveWindows = 0;

            } else if (inEvent) {

                inactiveWindows++;

                if (inactiveWindows >= eventGapWindows) {
                    inEvent = false;
                }
            }
        }

        return eventCount;
    }


    private int countOnsets(boolean[] active, int firstActive, int lastActive, double activityThreshold) {

        var lookbackWindows = Math.max(1, (int) Math.round(ONSET_LOOKBACK_SECONDS * sampleRate / windowFrames));

        var minimumIntervalWindows = Math.max(1, (int) Math.round(ONSET_MIN_INTERVAL_SECONDS * sampleRate / windowFrames));

        var onsetCount = 1;
        var lastOnset = firstActive;

        for (var index = firstActive + 1; index <= lastActive; index++) {

            if (!active[index]) {
                continue;
            }

            if (index - lastOnset < minimumIntervalWindows) {
                continue;
            }

            var baseline = findOnsetBaseline(index, firstActive, lookbackWindows, activityThreshold);

            if (windowRms[index] < baseline * ONSET_RISE_RATIO) {
                continue;
            }

            onsetCount++;
            lastOnset = index;
        }

        return onsetCount;
    }


    private double findOnsetBaseline(int index, int firstActive, int lookbackWindows, double activityThreshold) {

        var start = Math.max(firstActive, index - lookbackWindows);
        var baseline = Double.POSITIVE_INFINITY;

        for (var previous = start; previous < index; previous++) {
            baseline = Math.min(baseline, windowRms[previous]);
        }

        return Math.max(activityThreshold, baseline);
    }


    private double calculateAttackSeconds(boolean[] active, int firstActive, int lastActive, int eventGapWindows) {

        var firstEventEnd = lastActive;
        var inactiveWindows = 0;

        for (var index = firstActive + 1; index <= lastActive; index++) {

            if (active[index]) {

                inactiveWindows = 0;

            } else {

                inactiveWindows++;

                if (inactiveWindows >= eventGapWindows) {
                    firstEventEnd = index - inactiveWindows;
                    break;
                }
            }
        }

        var eventPeakRms = 0.0;

        for (var index = firstActive; index <= firstEventEnd; index++) {
            eventPeakRms = Math.max(eventPeakRms, windowRms[index]);
        }

        if (eventPeakRms <= 0.0) {
            return 0.0;
        }

        var attackLevel = eventPeakRms * ATTACK_LEVEL_RATIO;

        for (var index = firstActive; index <= firstEventEnd; index++) {

            if (windowRms[index] >= attackLevel) {
                return (index - firstActive) * windowFrames / sampleRate;
            }
        }

        return 0.0;
    }


    private ActivityMetrics emptyMetrics() {
        return new ActivityMetrics(0.0, 0.0, 0.0, 0.0, 0, 0, 0.0);
    }

}