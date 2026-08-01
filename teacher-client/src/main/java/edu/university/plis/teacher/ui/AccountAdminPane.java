package edu.university.plis.teacher.ui;

import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.DeviceConnectionStatus;
import edu.university.plis.teacher.client.TeacherGateway;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public final class AccountAdminPane {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final TeacherGateway gateway;
    private final Runnable directoryChanged;
    private final TabPane root = new TabPane();
    private final ListView<StudentAccountView> accounts = new ListView<>();
    private final ListView<ConnectionRequestView> connections = new ListView<>();
    private final ListView<StudentAccountView> auditStudents = new ListView<>();
    private final TextArea sessionEvents = readonlyArea();
    private final TextArea securityAudit = readonlyArea();
    private final Label accountFeedback = feedbackLabel();
    private final Label connectionFeedback = feedbackLabel();
    private final Label passwordFeedback = feedbackLabel();
    private final Timeline polling = new Timeline(new KeyFrame(Duration.seconds(3), event -> refreshConnections()));

    public AccountAdminPane(TeacherGateway gateway, Runnable directoryChanged) {
        this.gateway = gateway;
        this.directoryChanged = directoryChanged;
        root.getTabs().addAll(tab("Student accounts", accountsPane()),
                tab("Access requests", connectionsPane()), tab("Complete activity log", auditPane()),
                tab("My password", passwordPane()));
        polling.setCycleCount(Timeline.INDEFINITE);
    }

    private Parent accountsPane() {
        accounts.setPrefWidth(390);
        TextField loginId = new TextField();
        loginId.setPromptText("Student login ID");
        TextField name = new TextField();
        name.setPromptText("Full name");
        TextField studentNumber = new TextField();
        studentNumber.setPromptText("Student/roll number (optional)");
        TextField program = new TextField();
        program.setPromptText("Program (optional)");
        Spinner<Integer> semester = new Spinner<>(1, 20, 1);
        PasswordField initialPassword = new PasswordField();
        initialPassword.setText("12345");
        initialPassword.setPromptText("Initial password");
        Button create = new Button("Create student account");
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> FxResponses.observe(gateway.createStudentAccount(
                new CreateStudentAccountRequest(loginId.getText(), name.getText(), studentNumber.getText(),
                        program.getText(), semester.getValue(), initialPassword.getText())), created -> {
            loginId.clear();
            name.clear();
            studentNumber.clear();
            program.clear();
            initialPassword.setText("12345");
            refreshAccounts();
            directoryChanged.run();
        }, accountFeedback));

        Button resetPassword = new Button("Reset password");
        Button toggleEnabled = new Button("Enable / disable");
        Button terminate = new Button("Terminate connections");
        resetPassword.setOnAction(event -> withAccount(selected -> {
            TextInputDialog dialog = new TextInputDialog("12345");
            dialog.setTitle("Reset student password");
            dialog.setHeaderText("New password for " + selected.username());
            dialog.showAndWait().ifPresent(password -> FxResponses.observe(
                    gateway.resetStudentPassword(selected.profileId(), new PasswordResetRequest(password)),
                    ignored -> refreshAccounts(), accountFeedback));
        }));
        toggleEnabled.setOnAction(event -> withAccount(selected -> FxResponses.observe(
                gateway.setStudentEnabled(selected.profileId(), !selected.enabled()),
                ignored -> refreshAccounts(), accountFeedback)));
        terminate.setOnAction(event -> withAccount(selected -> FxResponses.observe(
                gateway.terminateStudent(selected.profileId()), ignored -> refreshConnections(), accountFeedback)));

        TextArea bulkRows = new TextArea();
        bulkRows.setPromptText("student01,Student One,CSE-001,CSE,1,12345\n"
                + "student02,Student Two,CSE-002,CSE,1,12345");
        bulkRows.setPrefRowCount(6);
        bulkRows.setWrapText(false);
        Button createBulk = new Button("Create all pasted accounts");
        createBulk.setMaxWidth(Double.MAX_VALUE);
        createBulk.setOnAction(event -> {
            try {
                List<CreateStudentAccountRequest> requests = parseBulkAccounts(bulkRows.getText());
                FxResponses.observe(gateway.createStudentAccounts(new BulkStudentAccountsRequest(requests)),
                        created -> {
                            bulkRows.clear();
                            accountFeedback.setText("Created " + created.size() + " student accounts");
                            refreshAccounts();
                            directoryChanged.run();
                        }, accountFeedback);
            } catch (IllegalArgumentException exception) {
                accountFeedback.setText(exception.getMessage());
            }
        });

        VBox createForm = new VBox(8, new Label("Create login"), loginId, name, studentNumber, program,
                new Label("Semester"), semester, new Label("Initial password"), initialPassword, create,
                new Separator(), new Label("Bulk account creation"),
                new Label("Paste one comma- or tab-separated student per line:\n"
                        + "login ID, name, number, program, semester, password"),
                bulkRows, createBulk,
                new Separator(), new Label("Selected account controls"), resetPassword, toggleEnabled,
                terminate, accountFeedback);
        createForm.setPadding(new Insets(16));
        createForm.setPrefWidth(360);
        ScrollPane formScroll = new ScrollPane(createForm);
        formScroll.setFitToWidth(true);
        formScroll.setPrefWidth(390);
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(14));
        pane.setCenter(accounts);
        pane.setRight(formScroll);
        return pane;
    }

    private Parent connectionsPane() {
        connections.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(ConnectionRequestView item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.studentName() + " (" + item.studentUsername() + ")\n"
                        + item.deviceName() + " · " + item.status() + " · last seen "
                        + (item.lastSeenAt() == null ? "never" : TIME.format(item.lastSeenAt())));
            }
        });
        Button approve = new Button("Approve selected");
        approve.getStyleClass().add("primary-button");
        Button terminate = new Button("Terminate selected");
        terminate.getStyleClass().add("danger-button");
        Button refresh = new Button("Refresh");
        approve.setOnAction(event -> withConnection(selected -> FxResponses.observe(
                gateway.decideConnection(selected.id(), true), ignored -> refreshConnections(), connectionFeedback)));
        terminate.setOnAction(event -> withConnection(selected -> FxResponses.observe(
                gateway.decideConnection(selected.id(), false), ignored -> refreshConnections(), connectionFeedback)));
        refresh.setOnAction(event -> refreshConnections());
        Label explanation = new Label("A student enters a teacher-created ID and password. New computers remain "
                + "pending until you approve them. Termination invalidates that computer immediately.");
        explanation.setWrapText(true);
        VBox pane = new VBox(10, explanation, connections, new HBox(8, approve, terminate, refresh),
                connectionFeedback);
        pane.setPadding(new Insets(16));
        VBox.setVgrow(connections, Priority.ALWAYS);
        return pane;
    }

    private Parent auditPane() {
        auditStudents.setPrefWidth(340);
        auditStudents.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> loadAudit(selected));
        Tab eventTab = tab("Class activity", sessionEvents);
        Tab accountTab = tab("Login and access security", securityAudit);
        TabPane logs = new TabPane(eventTab, accountTab);
        SplitPane split = new SplitPane(auditStudents, logs);
        split.setDividerPositions(0.28);
        BorderPane pane = new BorderPane(split);
        pane.setPadding(new Insets(14));
        return pane;
    }

    private Parent passwordPane() {
        PasswordField current = new PasswordField();
        current.setPromptText("Current password");
        PasswordField replacement = new PasswordField();
        replacement.setPromptText("New password (minimum 5 characters)");
        Button change = new Button("Change my password");
        change.getStyleClass().add("primary-button");
        change.setOnAction(event -> FxResponses.observe(
                gateway.changePassword(current.getText(), replacement.getText()), ignored -> {
                    current.clear();
                    replacement.clear();
                    passwordFeedback.setText("Password changed successfully");
                }, passwordFeedback));
        VBox pane = new VBox(12, new Label("Teacher owner password"), current, replacement, change,
                passwordFeedback);
        pane.setPadding(new Insets(24));
        pane.setMaxWidth(520);
        return pane;
    }

    public void refreshAccounts() {
        FxResponses.observe(gateway.studentAccounts(), list -> {
            accounts.setItems(FXCollections.observableArrayList(list));
            auditStudents.setItems(FXCollections.observableArrayList(list));
        }, accountFeedback);
    }

    public void refreshConnections() {
        gateway.connections().whenComplete((list, failure) -> javafx.application.Platform.runLater(() -> {
            if (failure == null) {
                connections.setItems(FXCollections.observableArrayList(list));
            }
        }));
    }

    public void start() {
        refreshAccounts();
        refreshConnections();
        polling.play();
    }

    private void loadAudit(StudentAccountView selected) {
        if (selected == null) {
            return;
        }
        gateway.allStudentEvents(selected.profileId()).whenComplete((events, failure) ->
                javafx.application.Platform.runLater(() -> sessionEvents.setText(failure == null
                        ? formatEvents(events) : FxResponses.message(failure))));
        gateway.audit(selected.username()).whenComplete((logs, failure) ->
                javafx.application.Platform.runLater(() -> securityAudit.setText(failure == null
                        ? formatAudit(logs) : FxResponses.message(failure))));
    }

    private String formatEvents(List<EventLogView> events) {
        return events.stream().map(event -> TIME.format(event.timestamp()) + "  " + event.sessionType()
                + " #" + event.sessionId() + "  " + event.eventType()
                + (event.payload() == null ? "" : "  " + event.payload())).collect(Collectors.joining("\n"));
    }

    private String formatAudit(List<AuditLogView> logs) {
        return logs.stream().map(log -> TIME.format(log.timestamp()) + "  " + log.action()
                + (log.deviceId() == null ? "" : "  device=" + log.deviceId())
                + (log.details() == null ? "" : "  " + log.details())).collect(Collectors.joining("\n"));
    }

    private void withAccount(java.util.function.Consumer<StudentAccountView> operation) {
        StudentAccountView selected = accounts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            accountFeedback.setText("Select a student account first");
        } else {
            operation.accept(selected);
        }
    }

    private void withConnection(java.util.function.Consumer<ConnectionRequestView> operation) {
        ConnectionRequestView selected = connections.getSelectionModel().getSelectedItem();
        if (selected == null) {
            connectionFeedback.setText("Select a connection first");
        } else {
            operation.accept(selected);
        }
    }

    private static TextArea readonlyArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("audit-log");
        return area;
    }

    private static Label feedbackLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add("error-label");
        return label;
    }

    private List<CreateStudentAccountRequest> parseBulkAccounts(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paste at least one student row");
        }
        List<CreateStudentAccountRequest> requests = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split(line.contains("\t") ? "\\t" : ",", -1);
            if (columns.length < 2 || columns.length > 6) {
                throw new IllegalArgumentException("Bulk row " + (index + 1)
                        + " must contain 2 to 6 columns");
            }
            String username = columns[0].strip();
            String displayName = columns[1].strip();
            String number = columns.length > 2 ? columns[2].strip() : "";
            String program = columns.length > 3 ? columns[3].strip() : "";
            int semester;
            try {
                semester = columns.length > 4 && !columns[4].isBlank()
                        ? Integer.parseInt(columns[4].strip()) : 1;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Bulk row " + (index + 1)
                        + " has an invalid semester");
            }
            String password = columns.length > 5 && !columns[5].isBlank()
                    ? columns[5].strip() : "12345";
            requests.add(new CreateStudentAccountRequest(
                    username, displayName, number, program, semester, password));
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("Paste at least one student row");
        }
        return requests;
    }

    private static Tab tab(String title, Parent content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    public Parent root() { return root; }
}
