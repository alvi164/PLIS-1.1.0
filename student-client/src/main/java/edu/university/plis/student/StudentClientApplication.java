package edu.university.plis.student;

import edu.university.plis.student.client.StudentGateway;
import edu.university.plis.student.ui.LoginView;
import edu.university.plis.student.ui.StudentDashboard;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class StudentClientApplication extends Application {
    private StudentDashboard dashboard;
    private StudentGateway gateway;

    @Override
    public void start(Stage stage) {
        gateway = new StudentGateway(serverUrl());
        StackPane loginShell = new StackPane();
        loginShell.setAlignment(Pos.CENTER);
        LoginView login = new LoginView(gateway, response -> {
            dashboard = new StudentDashboard(gateway, response, stage);
            stage.setScene(createScene(dashboard.root(), 1180, 760));
            stage.setTitle("PLIS Student Workspace — " + response.displayName());
            dashboard.start();
        });
        loginShell.getChildren().add(login.root());
        stage.setScene(createScene(loginShell, 900, 650));
        stage.setTitle("PLIS Student Workspace");
        stage.setMinWidth(820);
        stage.setMinHeight(600);
        stage.show();
    }

    @Override
    public void stop() {
        if (dashboard != null) {
            dashboard.close();
        }
        if (gateway != null) {
            gateway.close();
        }
    }

    private Scene createScene(javafx.scene.Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/student.css").toExternalForm());
        return scene;
    }

    private String serverUrl() {
        String configured = System.getProperty("plis.server.url");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getenv().getOrDefault("PLIS_SERVER_URL", "http://localhost:8080");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
