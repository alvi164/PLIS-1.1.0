package edu.university.plis.teacher.ui;

import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.LabSessionStatus;
import edu.university.plis.shared.model.NetworkConnectionType;
import edu.university.plis.teacher.client.TeacherGateway;
import edu.university.plis.teacher.model.StudentActivityViewModel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class LabDashboardPane {
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final TeacherGateway gateway;
    private final BorderPane root = new BorderPane();
    private final ListView<LabSessionSummary> sessions = new ListView<>();
    private final ListView<StudentSummary> students = new ListView<>();
    private final TilePane activityTiles = new TilePane();
    private final Label feedback = new Label();
    private final Spinner<Integer> sessionLimit = new Spinner<>(1, 500, 1);
    private final ComboBox<NetworkConnectionType> sessionConnection = connectionPolicyBox();
    private final Map<Long, StudentActivitySnapshot> snapshots = new LinkedHashMap<>();
    private final Timeline polling = new Timeline(new KeyFrame(Duration.seconds(3), event -> refreshActivity()));

    public LabDashboardPane(TeacherGateway gateway) {
        this.gateway = gateway;
        root.setPadding(new Insets(18));
        root.setLeft(buildSidebar());
        root.setCenter(buildActivityArea());
        sessions.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> selectSession(selected));
        polling.setCycleCount(Timeline.INDEFINITE);
    }

    private Parent buildSidebar() {
        Label title = new Label("Lab sessions");
        title.getStyleClass().add("section-title");
        sessions.setPrefHeight(180);
        sessions.setPrefWidth(330);

        TextField courseCode = new TextField();
        courseCode.setPromptText("Course code");
        TextField section = new TextField();
        section.setPromptText("Section");
        Spinner<Integer> capacity = new Spinner<>(1, 500, 12);
        capacity.setEditable(true);
        ComboBox<NetworkConnectionType> connectionPolicy = connectionPolicyBox();
        students.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        students.setPrefHeight(145);
        Button selectAllStudents = new Button("Select all");
        Button clearStudents = new Button("Clear");
        selectAllStudents.setOnAction(event -> students.getSelectionModel().selectAll());
        clearStudents.setOnAction(event -> students.getSelectionModel().clearSelection());
        HBox studentSelectionActions = new HBox(8, selectAllStudents, clearStudents);

        Button create = new Button("Create draft");
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> {
            Set<Long> assigned = students.getSelectionModel().getSelectedItems().stream()
                    .map(StudentSummary::id).collect(Collectors.toSet());
            FxResponses.observe(gateway.createLab(new CreateLabSessionRequest(
                    courseCode.getText(), section.getText(), assigned,
                    capacity.getValue(), connectionPolicy.getValue())), created -> {
                courseCode.clear();
                section.clear();
                refresh();
            }, feedback);
        });

        Button start = new Button("Start");
        Button end = new Button("End");
        Button refresh = new Button("Refresh");
        HBox actions = new HBox(8, start, end, refresh);
        start.setOnAction(event -> withSelected(session ->
                FxResponses.observe(gateway.startLab(session.id()), ignored -> this.refresh(), feedback)));
        end.setOnAction(event -> withSelected(session ->
                FxResponses.observe(gateway.endLab(session.id()), ignored -> this.refresh(), feedback)));
        refresh.setOnAction(event -> refresh());

        sessionLimit.setEditable(true);
        Button applyPolicy = new Button("Apply access policy");
        applyPolicy.setMaxWidth(Double.MAX_VALUE);
        applyPolicy.setOnAction(event -> withSelected(session -> FxResponses.observe(
                gateway.updateLabPolicy(session.id(), new UpdateLabPolicyRequest(
                        sessionLimit.getValue(), sessionConnection.getValue())),
                ignored -> refresh(), feedback)));

        feedback.setWrapText(true);
        feedback.getStyleClass().add("error-label");
        VBox sidebarContent = new VBox(8, title, sessions, actions, new Separator(),
                new Label("Selected lab: maximum joined"), sessionLimit,
                new Label("Selected lab: allowed network"), sessionConnection, applyPolicy,
                new Separator(), new Label("New lab"), courseCode, section,
                new Label("Maximum joined students"), capacity,
                new Label("Allowed network"), connectionPolicy,
                new Label("Assigned students"), studentSelectionActions, students, create, feedback);
        ScrollPane sidebar = new ScrollPane(sidebarContent);
        sidebar.setFitToWidth(true);
        sidebar.setPrefWidth(355);
        sidebar.setPadding(new Insets(0, 18, 0, 0));
        sidebar.getStyleClass().add("transparent-scroll");
        return sidebar;
    }

    private Parent buildActivityArea() {
        Label title = new Label("Live student activity and code");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Click a student tile to inspect live code, edit it, or review activity.");
        hint.getStyleClass().add("muted");
        HBox heading = new HBox(12, title, hint);
        heading.setAlignment(Pos.BASELINE_LEFT);
        activityTiles.setHgap(12);
        activityTiles.setVgap(12);
        activityTiles.setPrefColumns(3);
        ScrollPane scroll = new ScrollPane(activityTiles);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(14, 0, 0, 0));
        VBox area = new VBox(heading, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return area;
    }

    public void setStudents(List<StudentSummary> availableStudents) {
        students.setItems(FXCollections.observableArrayList(availableStudents));
        students.getSelectionModel().selectAll();
    }

    public void refresh() {
        FxResponses.observe(gateway.labs(), labs -> {
            Long selectedId = sessions.getSelectionModel().getSelectedItem() == null
                    ? null : sessions.getSelectionModel().getSelectedItem().id();
            sessions.setItems(FXCollections.observableArrayList(labs));
            if (selectedId != null) {
                labs.stream().filter(lab -> lab.id() == selectedId).findFirst()
                        .ifPresent(lab -> sessions.getSelectionModel().select(lab));
            }
        }, feedback);
    }

    public void startPolling() {
        polling.play();
    }

    private void selectSession(LabSessionSummary selected) {
        snapshots.clear();
        renderTiles();
        if (selected == null) {
            return;
        }
        sessionLimit.getValueFactory().setValue(selected.maxParticipants());
        sessionConnection.getSelectionModel().select(selected.connectionPolicy());
        gateway.subscribeToLab(selected.id(), update -> Platform.runLater(() -> {
            LabSessionSummary current = sessions.getSelectionModel().getSelectedItem();
            if (current != null && current.id() == update.labSessionId()) {
                snapshots.put(update.studentId(), update);
                renderTiles();
            }
        }));
        refreshActivity();
    }

    private void refreshActivity() {
        LabSessionSummary selected = sessions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        gateway.labActivity(selected.id()).whenComplete((activity, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                activity.forEach(snapshot -> snapshots.put(snapshot.studentId(), snapshot));
                renderTiles();
            }
        }));
    }

    private void renderTiles() {
        activityTiles.getChildren().clear();
        snapshots.values().forEach(snapshot -> {
            StudentActivityViewModel viewModel = new StudentActivityViewModel(snapshot);
            Label name = new Label(viewModel.heading());
            name.getStyleClass().add("tile-heading");
            Label status = new Label(viewModel.statusText());
            status.getStyleClass().add("status-badge");
            Label time = new Label(viewModel.lastEventText());
            time.getStyleClass().add("muted");
            Label detail = new Label(snapshot.detail() == null ? "" : snapshot.detail());
            detail.setWrapText(true);
            NetworkConnectionType connectionType = snapshot.connectionType() == null
                    ? NetworkConnectionType.UNKNOWN : snapshot.connectionType();
            Label connection = new Label("Network: " + connectionType
                    + "  ·  Code revision: " + snapshot.codeRevision());
            connection.getStyleClass().add("muted");
            VBox tile = new VBox(8, name, status, time, detail, connection);
            tile.getStyleClass().addAll("student-tile", "status-" + snapshot.status().name().toLowerCase());
            tile.setOnMouseClicked(event -> showStudentDetails(snapshot));
            activityTiles.getChildren().add(tile);
        });
    }

    private void showStudentDetails(StudentActivitySnapshot snapshot) {
        LabSessionSummary selected = sessions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        TextArea code = new TextArea();
        code.setWrapText(false);
        code.getStyleClass().add("code-editor");
        code.setPrefSize(780, 500);
        code.setEditable(selected.status() == LabSessionStatus.ACTIVE);
        Label codeStatus = new Label("Loading live code…");
        codeStatus.getStyleClass().add("muted");
        Button saveTeacherEdit = new Button("Send teacher edit");
        saveTeacherEdit.getStyleClass().add("primary-button");
        saveTeacherEdit.setDisable(selected.status() != LabSessionStatus.ACTIVE);
        SimpleLongProperty revision = new SimpleLongProperty(-1);
        AtomicBoolean applyingServerText = new AtomicBoolean();
        AtomicBoolean dirty = new AtomicBoolean();
        code.textProperty().addListener((observable, previous, current) -> {
            if (!applyingServerText.get()) {
                dirty.set(true);
                codeStatus.setText("Unsaved teacher edit");
            }
        });

        Runnable refreshCode = () -> gateway.labCode(selected.id(), snapshot.studentId())
                .whenComplete((latest, failure) -> Platform.runLater(() -> {
                    if (failure != null) {
                        codeStatus.setText(FxResponses.message(failure));
                    } else if (!dirty.get() && latest.revision() != revision.get()) {
                        applyingServerText.set(true);
                        code.setText(latest.codeText());
                        applyingServerText.set(false);
                        revision.set(latest.revision());
                        codeStatus.setText("Revision " + latest.revision() + " · last changed by "
                                + latest.lastAuthorName() + " (" + latest.lastAuthorType() + ")");
                    }
                }));
        saveTeacherEdit.setOnAction(event -> {
            saveTeacherEdit.setDisable(true);
            gateway.updateLabCode(selected.id(), snapshot.studentId(),
                            new LabCodeUpdateRequest(code.getText(), revision.get()))
                    .whenComplete((saved, failure) -> Platform.runLater(() -> {
                        saveTeacherEdit.setDisable(selected.status() != LabSessionStatus.ACTIVE);
                        if (failure != null) {
                            codeStatus.setText(FxResponses.message(failure));
                            dirty.set(false);
                            refreshCode.run();
                        } else {
                            revision.set(saved.revision());
                            dirty.set(false);
                            codeStatus.setText("Teacher edit sent as revision " + saved.revision());
                        }
                    }));
        });
        VBox codePane = new VBox(8, codeStatus, code, saveTeacherEdit);
        VBox.setVgrow(code, Priority.ALWAYS);

        TextArea history = new TextArea();
        history.setEditable(false);
        history.setWrapText(true);
        FxResponses.observe(gateway.labHistory(selected.id(), snapshot.studentId()), events ->
                history.setText(events.stream().map(this::formatEvent).collect(Collectors.joining("\n"))), feedback);

        Tab codeTab = new Tab("Live code", codePane);
        Tab historyTab = new Tab("Activity history", history);
        codeTab.setClosable(false);
        historyTab.setClosable(false);
        TabPane tabs = new TabPane(codeTab, historyTab);
        tabs.setPrefSize(820, 580);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Student workspace — " + snapshot.studentName());
        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Timeline codePolling = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshCode.run()));
        codePolling.setCycleCount(Timeline.INDEFINITE);
        dialog.setOnShown(event -> {
            refreshCode.run();
            codePolling.play();
        });
        dialog.setOnHidden(event -> codePolling.stop());
        dialog.show();
    }

    private String formatEvent(EventLogView event) {
        String payload = event.payload() == null || event.payload().isBlank() ? "" : " — " + event.payload();
        return EVENT_TIME.format(event.timestamp()) + "  " + event.eventType() + payload;
    }

    private void withSelected(java.util.function.Consumer<LabSessionSummary> operation) {
        LabSessionSummary selected = sessions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select a lab session first");
        } else {
            operation.accept(selected);
        }
    }

    private ComboBox<NetworkConnectionType> connectionPolicyBox() {
        ComboBox<NetworkConnectionType> policy = new ComboBox<>(FXCollections.observableArrayList(
                NetworkConnectionType.ANY, NetworkConnectionType.WIRED_LAN, NetworkConnectionType.WIFI));
        policy.getSelectionModel().select(NetworkConnectionType.ANY);
        policy.setMaxWidth(Double.MAX_VALUE);
        return policy;
    }

    public Parent root() {
        return root;
    }
}
