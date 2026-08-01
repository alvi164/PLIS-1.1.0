package edu.university.plis.teacher.ui;

import edu.university.plis.shared.dto.LoginResponse;
import edu.university.plis.teacher.client.TeacherGateway;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class TeacherDashboard {
    private final TeacherGateway gateway;
    private final BorderPane root = new BorderPane();
    private final LabDashboardPane labDashboard;
    private final ExamDashboardPane examDashboard;
    private final AccountAdminPane accountAdmin;
    private final Label feedback = new Label();

    public TeacherDashboard(TeacherGateway gateway, LoginResponse login) {
        this.gateway = gateway;
        this.labDashboard = new LabDashboardPane(gateway);
        this.examDashboard = new ExamDashboardPane(gateway);
        this.accountAdmin = new AccountAdminPane(gateway, this::loadStudents);

        Label product = new Label("PLIS");
        product.getStyleClass().add("product-mark-small");
        Label identity = new Label(login.displayName() + "  ·  " + login.role().name().replace("ROLE_", ""));
        identity.getStyleClass().add("muted");
        Label connection = new Label(gateway.isRealtimeConnected() ? "● Live updates" : "Polling fallback");
        connection.getStyleClass().add(gateway.isRealtimeConnected() ? "connected" : "warning-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(14, product, identity, spacer, connection);
        header.setPadding(new Insets(15, 22, 15, 22));
        header.getStyleClass().add("app-header");

        TabPane tabs = new TabPane();
        Tab labTab = new Tab("LabLink", labDashboard.root());
        Tab examTab = new Tab("ExamBeacon", examDashboard.root());
        Tab adminTab = new Tab("Accounts & access", accountAdmin.root());
        labTab.setClosable(false);
        examTab.setClosable(false);
        adminTab.setClosable(false);
        tabs.getTabs().addAll(labTab, examTab, adminTab);

        feedback.setPadding(new Insets(4, 16, 8, 16));
        feedback.getStyleClass().add("error-label");
        root.setTop(header);
        root.setCenter(tabs);
        root.setBottom(feedback);
    }

    public void start() {
        loadStudents();
        labDashboard.refresh();
        examDashboard.refresh();
        labDashboard.startPolling();
        examDashboard.startPolling();
        accountAdmin.start();
    }

    private void loadStudents() {
        FxResponses.observe(gateway.students(), students -> {
            labDashboard.setStudents(students);
            examDashboard.setStudents(students);
        }, feedback);
    }

    public Parent root() {
        return root;
    }
}
