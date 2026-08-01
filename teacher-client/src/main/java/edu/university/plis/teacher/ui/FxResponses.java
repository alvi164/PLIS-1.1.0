package edu.university.plis.teacher.ui;

import javafx.application.Platform;
import javafx.scene.control.Label;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

final class FxResponses {
    private FxResponses() {
    }

    static <T> void observe(CompletableFuture<T> operation, Consumer<T> onSuccess, Label feedback) {
        feedback.setText("Working…");
        operation.whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                feedback.setText("");
                onSuccess.accept(result);
            } else {
                feedback.setText(message(failure));
            }
        }));
    }

    static String message(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current.getClass() == RuntimeException.class)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "The operation failed" : current.getMessage();
    }
}
