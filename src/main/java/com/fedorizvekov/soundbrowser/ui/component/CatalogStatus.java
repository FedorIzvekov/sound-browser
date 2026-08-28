package com.fedorizvekov.soundbrowser.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class CatalogStatus extends VBox {

    private static final String STATUS_SUCCESS_STYLE = "status-success";
    private static final String STATUS_WARNING_STYLE = "status-warning";
    private static final String STATUS_ERROR_STYLE = "status-error";

    private final Label foundCountLabel = new Label("0 found");
    private final Label totalCountLabel = new Label("0 total");
    private final Label errorCountLabel = new Label();
    private final Label oggCountLabel = new Label();
    private final Label statusLabel = new Label();


    public CatalogStatus() {

        super(12);

        configureLabels();

        var countBar = new HBox(
                6,
                foundCountLabel,
                totalCountLabel,
                errorCountLabel,
                oggCountLabel
        );

        countBar.setAlignment(Pos.CENTER_LEFT);
        countBar.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(countBar, statusLabel);
    }


    public void updateCounts(int found, int successful, int errors, int oggFiles) {
        var total = successful + errors + oggFiles;

        foundCountLabel.setText("%,d found".formatted(found));
        totalCountLabel.setText("%,d total".formatted(total));
        errorCountLabel.setText(formatErrorCount(errors));
        oggCountLabel.setText("%,d OGG found".formatted(oggFiles));

        setLabelVisible(errorCountLabel, errors > 0);
        setLabelVisible(oggCountLabel, oggFiles > 0);
    }


    public void showInfo(String text) {
        showStatus(text, null);
    }


    public void showSuccess(String text) {
        showStatus(text, STATUS_SUCCESS_STYLE);
    }


    public void showWarning(String text) {
        showStatus(text, STATUS_WARNING_STYLE);
    }


    public void showError(String text) {
        showStatus(text, STATUS_ERROR_STYLE);
    }


    public void clearStatus() {
        statusLabel.setText("");
        setLabelVisible(statusLabel, false);
    }


    private void configureLabels() {
        foundCountLabel.getStyleClass().addAll("count-label", "found-count-label");
        totalCountLabel.getStyleClass().addAll("count-label", "total-count-label");
        errorCountLabel.getStyleClass().addAll("count-label", "error-count-label");
        oggCountLabel.getStyleClass().addAll("count-label", "ogg-count-label");
        statusLabel.getStyleClass().add("status-label");

        oggCountLabel.setTooltip(new Tooltip("OGG support is not implemented yet"));

        setLabelVisible(errorCountLabel, false);
        setLabelVisible(oggCountLabel, false);
        setLabelVisible(statusLabel, false);
    }


    private void showStatus(String text, String additionalStyleClass) {
        statusLabel.setText(text);

        statusLabel.getStyleClass().removeAll(STATUS_SUCCESS_STYLE, STATUS_WARNING_STYLE, STATUS_ERROR_STYLE);

        if (additionalStyleClass != null) {
            statusLabel.getStyleClass().add(additionalStyleClass);
        }

        setLabelVisible(statusLabel, true);
    }


    private String formatErrorCount(int count) {
        return count == 1 ? "1 WAV error" : "%,d WAV errors".formatted(count);
    }


    private void setLabelVisible(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }

}
