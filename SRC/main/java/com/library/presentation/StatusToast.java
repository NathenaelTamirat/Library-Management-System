package com.library.presentation;

import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public final class StatusToast {
    static final long DISPLAY_MILLIS = 2_500;
    static final long FADE_MILLIS = 300;

    private StatusToast() {
    }

    public static void show(Window owner, String message) {
        Objects.requireNonNull(owner, "owner");

        Label label = new Label(format(message));
        label.setWrapText(true);
        label.setMaxWidth(360);
        label.setStyle("-fx-background-color: rgba(30, 30, 30, 0.92);"
                + "-fx-background-radius: 8;"
                + "-fx-padding: 10 16;"
                + "-fx-text-fill: white;");

        StackPane root = new StackPane(label);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: transparent; -fx-padding: 8;");

        Stage toast = new Stage(StageStyle.TRANSPARENT);
        toast.initOwner(owner);
        toast.initModality(Modality.NONE);
        toast.setAlwaysOnTop(true);
        toast.setScene(new Scene(root));
        toast.getScene().setFill(Color.TRANSPARENT);
        toast.setOnShown(ignored -> {
            toast.setX(owner.getX() + (owner.getWidth() - toast.getWidth()) / 2);
            toast.setY(owner.getY() + owner.getHeight() - toast.getHeight() - 36);
        });

        PauseTransition pause = new PauseTransition(Duration.millis(DISPLAY_MILLIS));
        FadeTransition fade = new FadeTransition(Duration.millis(FADE_MILLIS), root);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(ignored -> toast.close());
        pause.setOnFinished(ignored -> fade.play());

        toast.show();
        pause.play();
    }

    static String format(String message) {
        return Objects.requireNonNull(message, "message").strip();
    }
}
