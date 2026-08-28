package com.fedorizvekov.soundbrowser.ui.component;

import com.fedorizvekov.soundbrowser.model.Waveform;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public final class WaveformView extends Region {

    private static final double VIEW_WIDTH = 360.0;
    private static final double VIEW_HEIGHT = 56.0;

    private static final Color WAVEFORM_COLOR = Color.web("#ff7a18");
    private static final Color CENTER_LINE_COLOR = Color.web("#d3d7d8");

    private final Canvas canvas = new Canvas();

    private Waveform waveform;


    public WaveformView() {
        getStyleClass().add("waveform-view");
        getChildren().add(canvas);

        setMinSize(VIEW_WIDTH, VIEW_HEIGHT);
        setPrefSize(VIEW_WIDTH, VIEW_HEIGHT);
        setMaxSize(VIEW_WIDTH, VIEW_HEIGHT);
        setMouseTransparent(true);
    }


    public void setWaveform(Waveform waveform) {
        this.waveform = waveform;
        redraw();
    }


    @Override
    protected void layoutChildren() {

        var insets = getInsets();
        var width = Math.max(0.0, getWidth() - insets.getLeft() - insets.getRight());
        var height = Math.max(0.0, getHeight() - insets.getTop() - insets.getBottom());

        canvas.setLayoutX(insets.getLeft());
        canvas.setLayoutY(insets.getTop());
        canvas.setWidth(width);
        canvas.setHeight(height);

        redraw();
    }


    private void redraw() {

        var graphics = canvas.getGraphicsContext2D();
        var width = canvas.getWidth();
        var height = canvas.getHeight();

        graphics.clearRect(0, 0, width, height);

        if (waveform == null || width <= 0.0 || height <= 0.0) {
            return;
        }

        var minimums = waveform.minimums();
        var maximums = waveform.maximums();
        var pointCount = Math.min(minimums.length, maximums.length);

        if (pointCount == 0) {
            return;
        }

        var centerY = height / 2.0;
        var amplitude = Math.max(0.0, centerY - 2.0);
        var step = width / pointCount;

        graphics.setStroke(CENTER_LINE_COLOR);
        graphics.setLineWidth(1.0);
        graphics.strokeLine(0.0, centerY, width, centerY);

        graphics.setStroke(WAVEFORM_COLOR);
        graphics.setLineWidth(Math.max(1.0, step));

        for (var index = 0; index < pointCount; index++) {
            var minimum = clamp(minimums[index]);
            var maximum = clamp(maximums[index]);

            var x = (index + 0.5) * step;
            var top = centerY - maximum * amplitude;
            var bottom = centerY - minimum * amplitude;

            graphics.strokeLine(x, top, x, bottom);
        }
    }


    private float clamp(float value) {

        if (!Float.isFinite(value)) {
            return 0.0f;
        }

        return Math.clamp(value, -1.0f, 1.0f);
    }

}
