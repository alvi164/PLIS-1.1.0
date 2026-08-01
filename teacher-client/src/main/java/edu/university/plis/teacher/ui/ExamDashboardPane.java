package edu.university.plis.teacher.ui;

import edu.university.plis.shared.dto.*;
import edu.university.plis.teacher.client.TeacherGateway;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ExamDashboardPane {
    private final TeacherGateway gateway;
    private final BorderPane root = new BorderPane();
    private final ListView<ExamSummary> exams = new ListView<>();
    private final ListView<StudentSummary> students = new ListView<>();
    private final ObservableList<ExamParticipantSnapshot> participants = FXCollections.observableArrayList();
    private final TableView<ExamParticipantSnapshot> dashboard = new TableView<>(participants);
    private final TextArea integrityReport = new TextArea();
    private final TextArea codeViewer = new TextArea();
    private final Label codeInformation = new Label("Select a student to view live or submitted code");
    private final Label feedback = new Label();
    private final Timeline polling = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshDashboard()));
    private ExamCodeSnapshot displayedCode;

    public ExamDashboardPane(TeacherGateway gateway) {
        this.gateway = gateway;
        root.setPadding(new Insets(18));
        root.setLeft(buildSidebar());
        root.setCenter(buildDashboard());
        exams.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> selectExam(selected));
        dashboard.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> refreshSelectedCode());
        polling.setCycleCount(Timeline.INDEFINITE);
    }

    private Parent buildSidebar() {
        Label title = new Label("Exam sessions");
        title.getStyleClass().add("section-title");
        exams.setPrefHeight(190);
        exams.setPrefWidth(340);
        Button start = new Button("Start");
        Button end = new Button("End");
        Button refresh = new Button("Refresh");
        start.setOnAction(event -> withSelected(exam ->
                FxResponses.observe(gateway.startExam(exam.id()), ignored -> this.refresh(), feedback)));
        end.setOnAction(event -> withSelected(exam ->
                FxResponses.observe(gateway.endExam(exam.id()), ignored -> this.refresh(), feedback)));
        refresh.setOnAction(event -> refresh());
        HBox actions = new HBox(8, start, end, refresh);

        TextField examTitle = new TextField();
        examTitle.setPromptText("Exam title");
        TextField courseCode = new TextField();
        courseCode.setPromptText("Course code");
        DatePicker date = new DatePicker(LocalDate.now().plusDays(1));
        TextField time = new TextField("09:00");
        time.setPromptText("HH:mm");
        Spinner<Integer> duration = new Spinner<>(1, 480, 120, 5);
        duration.setEditable(true);
        TextArea question = new TextArea();
        question.setPromptText("Problem statement or questions");
        question.setWrapText(true);
        question.setPrefRowCount(6);
        students.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        students.setPrefHeight(150);
        Button create = new Button("Schedule exam");
        create.getStyleClass().add("primary-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> createExam(examTitle, courseCode, date, time, duration, question));

        feedback.getStyleClass().add("error-label");
        feedback.setWrapText(true);
        VBox form = new VBox(9, title, exams, actions, new Separator(), new Label("New exam"),
                examTitle, courseCode, new Label("Scheduled date and local time"), date, time,
                new Label("Duration (minutes)"), duration, question, new Label("Assigned students"),
                students, create, feedback);
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(360);
        scroll.setPadding(new Insets(0, 18, 0, 0));
        return scroll;
    }

    private Parent buildDashboard() {
        Label title = new Label("Exam supervision dashboard");
        title.getStyleClass().add("section-title");
        dashboard.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dashboard.getColumns().add(column("Student", row -> row.studentNumber() + " · " + row.studentName()));
        dashboard.getColumns().add(column("State", row -> row.state().name()));
        dashboard.getColumns().add(column("Language", row -> row.language() == null
                ? "Not chosen" : row.language().displayName()));
        dashboard.getColumns().add(column("File", row -> row.fileName() == null ? "—" : row.fileName()));
        dashboard.getColumns().add(column("Revision", row -> Long.toString(row.codeRevision())));
        dashboard.getColumns().add(column("Focus loss", row -> Long.toString(row.focusLossCount())));
        dashboard.getColumns().add(column("Flags", row -> Integer.toString(row.integrityFlagCount())));
        dashboard.setPlaceholder(new Label("Select an exam to view assigned students"));

        codeViewer.setEditable(false);
        codeViewer.setWrapText(false);
        codeViewer.getStyleClass().add("audit-log");
        codeInformation.setWrapText(true);
        Button refreshCode = new Button("Refresh code");
        Button exportSelected = new Button("Export selected student");
        Button exportAll = new Button("Export all student code");
        refreshCode.setOnAction(event -> refreshSelectedCode());
        exportSelected.setOnAction(event -> exportSelected());
        exportAll.setOnAction(event -> exportAll());
        VBox codePane = new VBox(8, codeInformation,
                new HBox(8, refreshCode, exportSelected, exportAll), codeViewer);
        codePane.setPadding(new Insets(10));
        VBox.setVgrow(codeViewer, Priority.ALWAYS);

        integrityReport.setEditable(false);
        integrityReport.setWrapText(true);
        integrityReport.setPromptText("Integrity flags will appear here");
        Tab codeTab = new Tab("Live and submitted code", codePane);
        Tab integrityTab = new Tab("Integrity flags", integrityReport);
        codeTab.setClosable(false);
        integrityTab.setClosable(false);
        TabPane details = new TabPane(codeTab, integrityTab);

        SplitPane split = new SplitPane(dashboard, details);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.48);
        VBox content = new VBox(10, title, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return content;
    }

    private TableColumn<ExamParticipantSnapshot, String> column(
            String heading, java.util.function.Function<ExamParticipantSnapshot, String> value) {
        TableColumn<ExamParticipantSnapshot, String> column = new TableColumn<>(heading);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    public void setStudents(List<StudentSummary> availableStudents) {
        students.setItems(FXCollections.observableArrayList(availableStudents));
    }

    public void refresh() {
        FxResponses.observe(gateway.exams(), values -> {
            Long selectedId = exams.getSelectionModel().getSelectedItem() == null
                    ? null : exams.getSelectionModel().getSelectedItem().id();
            exams.setItems(FXCollections.observableArrayList(values));
            if (selectedId != null) {
                values.stream().filter(exam -> exam.id() == selectedId).findFirst()
                        .ifPresent(exam -> exams.getSelectionModel().select(exam));
            }
        }, feedback);
    }

    public void startPolling() {
        polling.play();
    }

    private void createExam(
            TextField title,
            TextField course,
            DatePicker date,
            TextField time,
            Spinner<Integer> duration,
            TextArea question) {
        try {
            LocalDateTime localStart = LocalDateTime.of(date.getValue(), LocalTime.parse(time.getText().strip()));
            Set<Long> assigned = students.getSelectionModel().getSelectedItems().stream()
                    .map(StudentSummary::id).collect(Collectors.toSet());
            CreateExamRequest request = new CreateExamRequest(title.getText(), course.getText(),
                    localStart.atZone(ZoneId.systemDefault()).toInstant(), duration.getValue(),
                    question.getText(), assigned);
            FxResponses.observe(gateway.createExam(request), created -> {
                title.clear();
                course.clear();
                question.clear();
                refresh();
            }, feedback);
        } catch (DateTimeException | NullPointerException exception) {
            feedback.setText("Enter a valid date and time such as 09:00");
        }
    }

    private void selectExam(ExamSummary selected) {
        participants.clear();
        displayedCode = null;
        codeViewer.clear();
        codeInformation.setText("Select a student to view live or submitted code");
        integrityReport.clear();
        if (selected == null) {
            return;
        }
        gateway.subscribeToExam(selected.id(), update -> Platform.runLater(() -> updateParticipant(update)));
        refreshDashboard();
    }

    private void refreshDashboard() {
        ExamSummary selected = exams.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ExamParticipantSnapshot selectedStudent = dashboard.getSelectionModel().getSelectedItem();
        Long selectedStudentId = selectedStudent == null ? null : selectedStudent.studentId();
        gateway.examDashboard(selected.id()).whenComplete((rows, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                participants.setAll(rows);
                if (selectedStudentId != null) {
                    rows.stream().filter(row -> row.studentId() == selectedStudentId).findFirst()
                            .ifPresent(row -> dashboard.getSelectionModel().select(row));
                }
                refreshSelectedCode();
            }
        }));
        gateway.integrityReport(selected.id()).whenComplete((flags, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                integrityReport.setText(flags.isEmpty() ? "No integrity flags recorded." : flags.stream()
                        .map(flag -> flag.studentName() + " — " + flag.flagType() + "\n" + flag.description())
                        .collect(Collectors.joining("\n\n")));
            }
        }));
    }

    private void refreshSelectedCode() {
        ExamSummary exam = exams.getSelectionModel().getSelectedItem();
        ExamParticipantSnapshot student = dashboard.getSelectionModel().getSelectedItem();
        if (exam == null || student == null) {
            return;
        }
        gateway.examCode(exam.id(), student.studentId()).whenComplete((snapshot, failure) ->
                Platform.runLater(() -> {
                    if (failure != null) {
                        codeInformation.setText(FxResponses.message(failure));
                        return;
                    }
                    displayedCode = snapshot;
                    codeViewer.setText(snapshot.codeText());
                    if (snapshot.language() == null) {
                        codeInformation.setText(snapshot.studentName() + " has not selected a language yet");
                    } else {
                        codeInformation.setText(snapshot.studentName() + " · " + snapshot.language().displayName()
                                + " · " + snapshot.fileName() + " · revision " + snapshot.revision()
                                + (snapshot.submitted() ? " · FINAL SUBMISSION" : " · LIVE DRAFT"));
                    }
                }));
    }

    private void exportSelected() {
        ExamParticipantSnapshot selected = dashboard.getSelectionModel().getSelectedItem();
        if (selected == null || displayedCode == null || displayedCode.studentId() != selected.studentId()) {
            feedback.setText("Select a student with loaded code first");
            return;
        }
        if (displayedCode.language() == null) {
            feedback.setText("That student has not selected a programming language");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export student exam code");
        chooser.setInitialFileName(exportFileName(displayedCode));
        var selectedFile = chooser.showSaveDialog(root.getScene().getWindow());
        if (selectedFile != null) {
            writeCode(displayedCode, selectedFile.toPath());
        }
    }

    private void exportAll() {
        ExamSummary exam = exams.getSelectionModel().getSelectedItem();
        if (exam == null) {
            feedback.setText("Select an exam first");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder for exported exam code");
        var directory = chooser.showDialog(root.getScene().getWindow());
        if (directory == null) {
            return;
        }
        List<CompletableFuture<ExamCodeSnapshot>> requests = participants.stream()
                .map(student -> gateway.examCode(exam.id(), student.studentId()))
                .toList();
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    if (failure != null) {
                        feedback.setText(FxResponses.message(failure));
                        return;
                    }
                    List<ExamCodeSnapshot> snapshots = requests.stream()
                            .map(CompletableFuture::join)
                            .filter(snapshot -> snapshot.language() != null && !snapshot.codeText().isBlank())
                            .toList();
                    int written = 0;
                    for (ExamCodeSnapshot snapshot : snapshots) {
                        Path target = uniquePath(directory.toPath().resolve(exportFileName(snapshot)));
                        if (writeCode(snapshot, target)) {
                            written++;
                        }
                    }
                    feedback.setText("Exported " + written + " student source files to " + directory);
                }));
    }

    private boolean writeCode(ExamCodeSnapshot snapshot, Path target) {
        try {
            Files.writeString(target, snapshot.codeText(), StandardCharsets.UTF_8);
            feedback.setText("Exported " + target.getFileName());
            return true;
        } catch (Exception exception) {
            feedback.setText("Could not export " + target + ": " + exception.getMessage());
            return false;
        }
    }

    private Path uniquePath(Path requested) {
        if (!Files.exists(requested)) {
            return requested;
        }
        String name = requested.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int suffix = 2; suffix < 10_000; suffix++) {
            Path candidate = requested.resolveSibling(base + "_" + suffix + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not create a unique export filename");
    }

    private String exportFileName(ExamCodeSnapshot snapshot) {
        String identity = snapshot.studentNumber() + "_" + snapshot.studentName();
        String safe = identity.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_");
        return safe + snapshot.language().extension();
    }

    private void updateParticipant(ExamParticipantSnapshot update) {
        ExamSummary selected = exams.getSelectionModel().getSelectedItem();
        if (selected == null || selected.id() != update.examSessionId()) {
            return;
        }
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).studentId() == update.studentId()) {
                participants.set(index, update);
                return;
            }
        }
        participants.add(update);
        participants.sort(Comparator.comparing(ExamParticipantSnapshot::studentNumber));
    }

    private void withSelected(java.util.function.Consumer<ExamSummary> operation) {
        ExamSummary selected = exams.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select an exam first");
        } else {
            operation.accept(selected);
        }
    }

    public Parent root() {
        return root;
    }
}
