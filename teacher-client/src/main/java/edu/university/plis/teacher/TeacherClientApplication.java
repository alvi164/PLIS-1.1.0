package edu.university.plis.teacher;

import edu.university.plis.teacher.client.TeacherGateway;
import edu.university.plis.teacher.ui.LoginView;
import edu.university.plis.teacher.ui.TeacherDashboard;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class TeacherClientApplication extends Application {
    private TeacherGateway gateway;

    @Override
    public void start(Stage stage) {
        gateway = new TeacherGateway(serverUrl());
        StackPane loginShell = new StackPane();
        loginShell.setAlignment(Pos.CENTER);
        LoginView loginView = new LoginView(gateway, login -> {
            TeacherDashboard dashboard = new TeacherDashboard(gateway, login);
            Scene scene = createScene(dashboard.root(), 1280, 820);
            stage.setScene(scene);
            stage.setTitle("PLIS Teacher Console — " + login.displayName());
            dashboard.start();
        });
        loginShell.getChildren().add(loginView.root());
        stage.setScene(createScene(loginShell, 900, 650));
        stage.setTitle("PLIS Teacher Console");
        stage.setMinWidth(860);
        stage.setMinHeight(620);
        stage.show();
    }

    @Override
    public void stop() {
        if (gateway != null) {
            gateway.close();
        }
    }

    private Scene createScene(javafx.scene.Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/teacher.css").toExternalForm());
        return scene;
    }

    private String serverUrl() {
        String systemProperty = System.getProperty("plis.server.url");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        return System.getenv().getOrDefault("PLIS_SERVER_URL", "http://localhost:8080");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
