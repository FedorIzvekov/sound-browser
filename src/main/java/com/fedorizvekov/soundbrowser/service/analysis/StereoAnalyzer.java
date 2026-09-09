package com.fedorizvekov.soundbrowser.service.analysis;

public final class StereoAnalyzer {

    private long frameCount;

    private double leftMean;
    private double rightMean;

    private double leftVarianceSum;
    private double rightVarianceSum;
    private double covarianceSum;


    public void accept(double leftSample, double rightSample) {

        frameCount++;

        var leftDelta = leftSample - leftMean;
        leftMean += leftDelta / frameCount;

        var rightDelta = rightSample - rightMean;
        rightMean += rightDelta / frameCount;

        leftVarianceSum += leftDelta * (leftSample - leftMean);
        rightVarianceSum += rightDelta * (rightSample - rightMean);
        covarianceSum += leftDelta * (rightSample - rightMean);
    }


    public Double finish() {

        if (frameCount < 2 || leftVarianceSum <= 0.0 || rightVarianceSum <= 0.0) {
            return null;
        }

        var denominator = Math.sqrt(leftVarianceSum * rightVarianceSum);

        if (denominator <= 0.0) {
            return null;
        }

        var correlation = covarianceSum / denominator;

        return Math.max(-1.0, Math.min(1.0, correlation));
    }

}