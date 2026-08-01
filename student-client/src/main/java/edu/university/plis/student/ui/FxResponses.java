package edu.university.plis.student.ui;

import edu.university.plis.shared.client.ApiClientException;
import javafx.application.Platform;
import javafx.scene.control.Label;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

final class FxResponses {
    private FxResponses() {
    }

    static <T> void observe(CompletableFuture<T> operation, Consumer<T> success, Label feedback) {
        feedback.setText("Working…");
        operation.whenComplete((result, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                feedback.setText("");
                success.accept(result);
            } else {
                feedback.setText(message(failure));
            }
        }));
    }

    static String message(Throwable failure) {
        Throwable current = unwrap(failure);
        return current.getMessage() == null ? "The operation failed" : current.getMessage();
    }

    static int statusCode(Throwable failure) {
        Throwable current = unwrap(failure);
        return current instanceof ApiClientException apiFailure ? apiFailure.statusCode() : -1;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
