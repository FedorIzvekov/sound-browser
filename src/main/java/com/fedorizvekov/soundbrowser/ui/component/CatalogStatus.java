package com.fedorizvekov.soundbrowser.ui.component;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class CatalogStatus extends VBox {

    private static final String STATUS_SUCCESS_STYLE = "status-success";
    private static final String STATUS_WARNING_STYLE = "status-warning";
    private static final String STATUS_ERROR_STYLE = "status-error";

    private final Label foundCountLabel = new Label("0 found");
    private final Label totalCountLabel = new Label("0 total");
    private final Label errorCountLabel = new Label();
    private final Label statusLabel = new Label();


    public CatalogStatus(Node formatSwitch) {

        super(12);

        configureLabels();

        var countBar = new HBox(
                6,
                foundCountLabel,
                totalCountLabel,
                errorCountLabel
        );

        countBar.setAlignment(Pos.CENTER_LEFT);
        countBar.setMinWidth(Region.USE_PREF_SIZE);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var summaryBar = new HBox(
                12,
                countBar,
                spacer,
                formatSwitch
        );

        summaryBar.setAlignment(Pos.CENTER_LEFT);
        summaryBar.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(summaryBar, statusLabel);
    }


    public void updateCounts(int found, int total, int errors) {

        foundCountLabel.setText("%,d found".formatted(found));
        totalCountLabel.setText("%,d total".formatted(total));
        errorCountLabel.setText(formatErrorCount(errors));

        setLabelVisible(errorCountLabel, errors > 0);
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
        statusLabel.getStyleClass().add("status-label");

        setLabelVisible(errorCountLabel, false);
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
        return count == 1 ? "1 error" : "%,d errors".formatted(count);
    }


    private void setLabelVisible(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }

}