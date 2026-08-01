package edu.university.plis.student.ui;

import edu.university.plis.shared.dto.LoginResponse;
import edu.university.plis.student.client.StudentGateway;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class LoginView {
    private final VBox root = new VBox(14);

    public LoginView(StudentGateway gateway, Consumer<LoginResponse> authenticated) {
        root.getStyleClass().add("login-card");
        root.setPadding(new Insets(36));
        root.setMaxWidth(420);
        root.setAlignment(Pos.CENTER_LEFT);

        Label product = new Label("PLIS");
        product.getStyleClass().add("product-mark");
        Label title = new Label("Student Workspace");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("Join your programming lab or secure exam session");
        subtitle.getStyleClass().add("muted");
        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Button signIn = new Button("Sign in");
        signIn.getStyleClass().add("primary-button");
        signIn.setMaxWidth(Double.MAX_VALUE);
        Label feedback = new Label();
        feedback.getStyleClass().add("error-label");
        feedback.setWrapText(true);
        gateway.setLoginStatusListener(status -> Platform.runLater(() -> feedback.setText(status)));

        Runnable login = () -> {
            signIn.setDisable(true);
            var operation = gateway.login(username.getText(), password.getText());
            operation.whenComplete((ignored, failure) -> Platform.runLater(() -> signIn.setDisable(false)));
            FxResponses.observe(operation, response -> {
                if (response.role() == edu.university.plis.shared.model.RoleName.ROLE_STUDENT) {
                    authenticated.accept(response);
                } else {
                    feedback.setText("This application requires a student account");
                }
            }, feedback);
        };
        signIn.setOnAction(event -> login.run());
        password.setOnAction(event -> login.run());
        root.getChildren().addAll(product, title, subtitle, new Separator(), username, password, signIn, feedback);
    }

    public Parent root() {
        return root;
    }
}
