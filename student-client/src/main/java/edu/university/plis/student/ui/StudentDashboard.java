package edu.university.plis.student.ui;

import edu.university.plis.shared.dto.LoginResponse;
import edu.university.plis.student.client.StudentGateway;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public final class StudentDashboard implements AutoCloseable {
    private final BorderPane root = new BorderPane();
    private final LabWorkspacePane labWorkspace;
    private final ExamWorkspacePane examWorkspace;

    public StudentDashboard(StudentGateway gateway, LoginResponse login, Stage stage) {
        labWorkspace = new LabWorkspacePane(gateway);
        examWorkspace = new ExamWorkspacePane(gateway, stage);

        Label product = new Label("PLIS");
        product.getStyleClass().add("product-mark-small");
        Label identity = new Label(login.displayName() + "  ·  " + login.username());
        identity.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label privacy = new Label("Activity is reported only inside joined sessions");
        privacy.getStyleClass().add("muted");
        HBox header = new HBox(14, product, identity, spacer, privacy);
        header.setPadding(new Insets(15, 22, 15, 22));
        header.getStyleClass().add("app-header");

        Tab lab = new Tab("LabLink", labWorkspace.root());
        Tab exam = new Tab("ExamBeacon", examWorkspace.root());
        Tab account = new Tab("My account", accountPane(gateway));
        lab.setClosable(false);
        exam.setClosable(false);
        account.setClosable(false);
        TabPane tabs = new TabPane(lab, exam, account);
        root.setTop(header);
        root.setCenter(tabs);
    }

    private Parent accountPane(StudentGateway gateway) {
        Label device = new Label("Approved computer: " + gateway.deviceName() + "\nDevice ID: "
                + gateway.deviceId());
        device.setWrapText(true);
        device.getStyleClass().add("muted");
        PasswordField current = new PasswordField();
        current.setPromptText("Current password");
        PasswordField replacement = new PasswordField();
        replacement.setPromptText("New password (minimum 5 characters)");
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("error-label");
        Button change = new Button("Change password");
        change.getStyleClass().add("primary-button");
        change.setOnAction(event -> FxResponses.observe(
                gateway.changePassword(current.getText(), replacement.getText()), ignored -> {
                    current.clear();
                    replacement.clear();
                    feedback.setText("Password changed successfully");
                }, feedback));
        VBox pane = new VBox(12, new Label("Student account security"), device, new Separator(),
                current, replacement, change, feedback);
        pane.setPadding(new Insets(24));
        pane.setMaxWidth(560);
        return pane;
    }

    public void start() {
        labWorkspace.refresh();
        examWorkspace.refresh();
    }

    public Parent root() {
        return root;
    }

    @Override
    public void close() {
        labWorkspace.close();
    }
}
