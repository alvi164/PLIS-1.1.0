package edu.university.plis.desktop;

import edu.university.plis.shared.client.PlisApiClient;
import edu.university.plis.shared.dto.InitialTeacherSetupRequest;
import edu.university.plis.shared.dto.SetupStatus;
import edu.university.plis.student.client.StudentGateway;
import edu.university.plis.student.ui.LoginView;
import edu.university.plis.student.ui.StudentDashboard;
import edu.university.plis.teacher.client.TeacherGateway;
import edu.university.plis.teacher.ui.TeacherDashboard;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlisDesktopApplication extends Application {
    private final DesktopStorage storage = new DesktopStorage();
    private Stage stage;
    private EmbeddedTeacherServer teacherServer;
    private TeacherDiscoveryBroadcaster broadcaster;
    private TeacherDiscoveryListener discoveryListener;
    private TeacherGateway teacherGateway;
    private StudentGateway studentGateway;
    private StudentDashboard studentDashboard;
    private String themeResource;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("PLIS - Programming Lab Integrity Suite");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        showWelcome();
        stage.show();
    }

    private void showWelcome() {
        themeResource = null;
        stopDiscoveryListener();
        Label brand = new Label("PLIS");
        brand.getStyleClass().add("unified-brand");
        Label title = new Label("Programming Lab Integrity Suite");
        title.getStyleClass().add("unified-title");
        Label subtitle = new Label("One application for teacher hosting and student workstations");
        subtitle.getStyleClass().add("muted");

        Button teacher = roleButton("Host as Teacher",
                "Start the secure classroom server, manage accounts, approve computers, and supervise activity.");
        teacher.setOnAction(event -> startTeacherHost());
        Button student = roleButton("Connect as Student",
                "Automatically find approved teacher hosts on this LAN or enter an address manually.");
        student.setOnAction(event -> showDiscovery());
        HBox roles = new HBox(22, teacher, student);
        roles.setAlignment(Pos.CENTER);

        Hyperlink about = new Hyperlink("About, creator and license");
        about.setOnAction(event -> showAbout());
        VBox root = new VBox(15, brand, title, subtitle, roles, about);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        setScene(root, 1080, 720);
    }

    private Button roleButton(String heading, String description) {
        Button button = new Button(heading + "\n\n" + description);
        button.setWrapText(true);
        button.setPrefSize(390, 190);
        button.getStyleClass().add("role-card");
        return button;
    }

    private void startTeacherHost() {
        themeResource = null;
        Label message = new Label("Starting the local encrypted-credential database and classroom server…");
        message.setWrapText(true);
        ProgressIndicator progress = new ProgressIndicator();
        Button back = new Button("Back");
        back.setOnAction(event -> showWelcome());
        VBox loading = new VBox(16, progress, message, back);
        loading.setAlignment(Pos.CENTER);
        setScene(loading, 900, 650);
        if (teacherServer == null) {
            teacherServer = new EmbeddedTeacherServer();
        }
        CompletableFuture.supplyAsync(() -> teacherServer.start(storage))
                .thenCompose(url -> new PlisApiClient(url).setupStatus()
                        .thenApply(status -> new HostStart(url, status)))
                .whenComplete((host, failure) -> Platform.runLater(() -> {
                    if (failure != null) {
                        message.setText("Teacher host could not start: " + failureMessage(failure)
                                + "\nData directory: " + storage.root());
                        progress.setVisible(false);
                    } else if (host.status().setupRequired()) {
                        showTeacherSetup(host.url());
                    } else {
                        startBroadcasting(host.status());
                        showTeacherLogin(host.url());
                    }
                }));
    }

    private void showTeacherSetup(String serverUrl) {
        themeResource = null;
        TextField username = new TextField("teacher");
        username.setPromptText("Teacher login ID");
        TextField displayName = new TextField();
        displayName.setPromptText("Teacher full name");
        PasswordField password = new PasswordField();
        password.setText("12345");
        password.setPromptText("Initial password");
        PasswordField confirmation = new PasswordField();
        confirmation.setText("12345");
        confirmation.setPromptText("Confirm password");
        Label feedback = new Label("This is a one-time owner setup. Change the initial password after sign-in.");
        feedback.setWrapText(true);
        Button create = new Button("Create teacher owner account");
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> {
            if (!password.getText().equals(confirmation.getText())) {
                feedback.setText("Password confirmation does not match");
                return;
            }
            create.setDisable(true);
            PlisApiClient api = new PlisApiClient(serverUrl);
            api.initializeTeacher(new InitialTeacherSetupRequest(
                            username.getText(), displayName.getText(), password.getText()))
                    .whenComplete((status, failure) -> Platform.runLater(() -> {
                        create.setDisable(false);
                        if (failure != null) {
                            feedback.setText(failureMessage(failure));
                        } else {
                            startBroadcasting(status);
                            showTeacherLogin(serverUrl);
                        }
                    }));
        });
        Label title = new Label("First-run teacher setup");
        title.getStyleClass().add("unified-title");
        VBox card = new VBox(11, title, new Label("Owner login ID"), username,
                new Label("Professional display name"), displayName, new Label("Initial password"), password,
                confirmation, create, feedback);
        card.getStyleClass().add("setup-card");
        card.setPadding(new Insets(30));
        card.setMaxWidth(520);
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(30));
        setScene(root, 900, 700);
    }

    private void showTeacherLogin(String serverUrl) {
        themeResource = "/teacher.css";
        if (teacherGateway != null) {
            teacherGateway.close();
        }
        teacherGateway = new TeacherGateway(serverUrl);
        StackPane shell = new StackPane();
        shell.setAlignment(Pos.CENTER);
        edu.university.plis.teacher.ui.LoginView login = new edu.university.plis.teacher.ui.LoginView(
                teacherGateway, response -> {
            TeacherDashboard dashboard = new TeacherDashboard(teacherGateway, response);
            Label hostInformation = new Label("Student discovery is active. Manual connection: "
                    + String.join("  |  ", hostAddresses()));
            hostInformation.setWrapText(true);
            hostInformation.getStyleClass().add("host-information");
            VBox teacherShell = new VBox(hostInformation, dashboard.root());
            VBox.setVgrow(dashboard.root(), Priority.ALWAYS);
            setScene(teacherShell, 1360, 860);
            stage.setTitle("PLIS Teacher Host - " + response.displayName() + " - port " + teacherServer.port());
            dashboard.start();
        });
        shell.getChildren().add(login.root());
        setScene(shell, 900, 680);
        stage.setTitle("PLIS Teacher Host");
    }

    private void startBroadcasting(SetupStatus status) {
        if (broadcaster != null) {
            broadcaster.close();
        }
        broadcaster = new TeacherDiscoveryBroadcaster(status.installationId(),
                status.teacherName() == null ? "PLIS Teacher" : status.teacherName(), teacherServer.port());
        broadcaster.start();
    }

    private void showDiscovery() {
        themeResource = null;
        stopDiscoveryListener();
        ListView<DiscoveredTeacher> teachers = new ListView<>();
        teachers.setPrefHeight(360);
        Label status = new Label("Searching the local Ethernet/Wi-Fi network for teacher hosts…");
        status.setWrapText(true);
        TextField manualAddress = new TextField();
        manualAddress.setPromptText("Manual address, e.g. http://192.168.1.20:8080");
        Button connectSelected = new Button("Connect to selected teacher");
        connectSelected.getStyleClass().add("primary-button");
        Button connectManual = new Button("Connect manually");
        Button back = new Button("Back");
        connectSelected.setOnAction(event -> {
            DiscoveredTeacher selected = teachers.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a discovered teacher first");
            } else {
                verifyStudentHost(selected.serverUrl(), status);
            }
        });
        connectManual.setOnAction(event -> verifyStudentHost(normalizedUrl(manualAddress.getText()), status));
        back.setOnAction(event -> showWelcome());
        VBox root = new VBox(12, new Label("Discovered teacher accounts"), status, teachers,
                connectSelected, new Separator(), manualAddress, new HBox(8, connectManual, back));
        root.setPadding(new Insets(24));
        VBox.setVgrow(teachers, Priority.ALWAYS);
        setScene(root, 900, 700);
        discoveryListener = new TeacherDiscoveryListener(current -> Platform.runLater(() -> {
            String selectedId = teachers.getSelectionModel().getSelectedItem() == null ? null
                    : teachers.getSelectionModel().getSelectedItem().installationId();
            teachers.setItems(FXCollections.observableArrayList(current));
            if (selectedId != null) {
                current.stream().filter(item -> item.installationId().equals(selectedId)).findFirst()
                        .ifPresent(item -> teachers.getSelectionModel().select(item));
            }
            status.setText(current.isEmpty() ? "No teacher host found yet. Keep this window open or use a manual address."
                    : current.size() + " teacher host(s) available");
        }));
        discoveryListener.start();
    }

    private void verifyStudentHost(String serverUrl, Label status) {
        if (serverUrl == null || serverUrl.isBlank()) {
            status.setText("Enter or select a teacher address");
            return;
        }
        status.setText("Verifying teacher host…");
        new PlisApiClient(serverUrl).setupStatus().whenComplete((setup, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                status.setText("Could not connect: " + failureMessage(failure));
            } else if (setup.setupRequired()) {
                status.setText("That host has not completed teacher setup");
            } else {
                stopDiscoveryListener();
                showStudentLogin(serverUrl, setup.teacherName());
            }
        }));
    }

    private void showStudentLogin(String serverUrl, String teacherName) {
        themeResource = "/student.css";
        if (studentGateway != null) {
            studentGateway.close();
        }
        studentGateway = new StudentGateway(serverUrl);
        StackPane shell = new StackPane();
        shell.setAlignment(Pos.CENTER);
        LoginView login = new LoginView(studentGateway, response -> {
            studentDashboard = new StudentDashboard(studentGateway, response, stage);
            setScene(studentDashboard.root(), 1240, 800);
            stage.setTitle("PLIS Student - " + response.displayName() + " / " + teacherName);
            studentDashboard.start();
        });
        shell.getChildren().add(login.root());
        setScene(shell, 900, 680);
        stage.setTitle("PLIS Student - Connecting to " + teacherName);
    }

    private void showAbout() {
        Hyperlink github = new Hyperlink("github.com/alvi164");
        github.setOnAction(event -> getHostServices().showDocument("https://github.com/alvi164"));
        Hyperlink orcid = new Hyperlink("ORCID 0009-0001-7332-6816");
        orcid.setOnAction(event -> getHostServices().showDocument("https://orcid.org/0009-0001-7332-6816"));
        Label text = new Label("PLIS 1.1.0\n\nCreated and owned by Syad Mehedi Hasan Alvi\n"
                + "Backend & Systems Engineer · Dhaka, Bangladesh\n"
                + "Java / Spring Boot / C++ / Python / IoT / Machine Learning\n\n"
                + "Copyright © 2026 Syad Mehedi Hasan Alvi. All rights reserved.\n"
                + "Proprietary commercial software. See LICENSE.txt.");
        text.setWrapText(true);
        VBox content = new VBox(10, text, github, orcid);
        content.setPadding(new Insets(16));
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("About PLIS");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void setScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        addStylesheet(scene, "/desktop.css");
        if (themeResource != null) {
            addStylesheet(scene, themeResource);
        }
        stage.setScene(scene);
    }

    private void addStylesheet(Scene scene, String resource) {
        var url = getClass().getResource(resource);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    private String normalizedUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String clean = value.strip().replaceAll("/+$", "");
        return clean.startsWith("http://") || clean.startsWith("https://") ? clean : "http://" + clean;
    }

    private List<String> hostAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Collections.list(networkInterface.getInetAddresses()).stream()
                        .filter(Inet4Address.class::isInstance)
                        .map(address -> "http://" + address.getHostAddress() + ":" + teacherServer.port())
                        .forEach(addresses::add);
            }
        } catch (Exception ignored) {
            // Automatic discovery remains available even when adapters cannot be enumerated.
        }
        if (addresses.isEmpty()) {
            addresses.add("port " + teacherServer.port());
        }
        return addresses;
    }

    private String failureMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current != current.getCause()) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }

    private void stopDiscoveryListener() {
        if (discoveryListener != null) {
            discoveryListener.close();
            discoveryListener = null;
        }
    }

    @Override
    public void stop() {
        stopDiscoveryListener();
        if (studentDashboard != null) {
            studentDashboard.close();
        }
        if (studentGateway != null) {
            studentGateway.close();
        }
        if (teacherGateway != null) {
            teacherGateway.close();
        }
        if (broadcaster != null) {
            broadcaster.close();
        }
        if (teacherServer != null) {
            teacherServer.close();
        }
    }

    private record HostStart(String url, SetupStatus status) { }
}
