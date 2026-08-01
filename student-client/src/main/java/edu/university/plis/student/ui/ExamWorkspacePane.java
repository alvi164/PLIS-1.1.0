package edu.university.plis.student.ui;

import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.ExamSessionStatus;
import edu.university.plis.shared.model.ProgrammingLanguage;
import edu.university.plis.shared.model.StudentEventType;
import edu.university.plis.student.client.InMemoryJavaCompiler;
import edu.university.plis.student.client.StudentGateway;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

public final class ExamWorkspacePane {
    private final StudentGateway gateway;
    private final Stage stage;
    private final InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();
    private final BorderPane root = new BorderPane();
    private final ListView<ExamSummary> exams = new ListView<>();
    private final Label examTitle = new Label("No exam open");
    private final Label countdown = new Label();
    private final Label fileName = new Label("Choose a language to create the source file");
    private final TextArea question = new TextArea();
    private final TextArea codeEditor = new TextArea();
    private final TextArea output = new TextArea();
    private final ComboBox<ProgrammingLanguage> languageChoice = new ComboBox<>();
    private final Button compile = new Button("Compile check");
    private final Button run = new Button("Run check");
    private final Button submit = new Button("Submit final code");
    private final Label feedback = new Label();
    private final PauseTransition autosave = new PauseTransition(Duration.millis(800));
    private final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
    private ExamDetails activeExam;
    private Instant editingStartedAt;
    private long codeRevision;
    private boolean examOpen;
    private boolean submitted;
    private boolean applyingServerCode;
    private boolean saveInFlight;
    private boolean savePending;
    private boolean submissionInFlight;

    public ExamWorkspacePane(StudentGateway gateway, Stage stage) {
        this.gateway = gateway;
        this.stage = stage;
        root.setPadding(new Insets(18));
        root.setLeft(buildExamList());
        root.setCenter(buildExamWorkspace());
        setWorkspaceEnabled(false);
        clock.setCycleCount(Timeline.INDEFINITE);
        autosave.setOnFinished(event -> synchronizeCode());
        codeEditor.textProperty().addListener((observable, previous, current) -> {
            if (examOpen && !submitted && !applyingServerCode && languageChoice.getValue() != null) {
                fileName.setText(languageChoice.getValue().defaultFileName()
                        + " · unsaved changes");
                autosave.playFromStart();
            }
        });
        languageChoice.valueProperty().addListener((observable, previous, selected) -> {
            if (examOpen && !submitted && !applyingServerCode && selected != null && codeRevision == 0) {
                beginLanguage(selected);
            }
        });
        stage.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (examOpen && !submitted && !isFocused) {
                gateway.recordExamSignal(StudentEventType.FOCUS_LOST, "Exam window lost focus");
            }
        });
        stage.fullScreenProperty().addListener((observable, wasFullScreen, isFullScreen) -> {
            if (examOpen && !submitted && wasFullScreen && !isFullScreen) {
                gateway.recordExamSignal(StudentEventType.FOCUS_LOST, "Full-screen exam mode was exited");
            }
        });
    }

    private Parent buildExamList() {
        Label title = new Label("Assigned exams");
        title.getStyleClass().add("section-title");
        exams.setPrefWidth(315);
        Button open = new Button("Enter active exam");
        open.getStyleClass().add("primary-button");
        Button refresh = new Button("Refresh");
        open.setOnAction(event -> openSelectedExam());
        refresh.setOnAction(event -> refresh());
        feedback.getStyleClass().add("error-label");
        feedback.setWrapText(true);
        VBox sidebar = new VBox(10, title, exams, new HBox(8, open, refresh), feedback);
        sidebar.setPadding(new Insets(0, 18, 0, 0));
        return sidebar;
    }

    private Parent buildExamWorkspace() {
        examTitle.getStyleClass().add("section-title");
        countdown.getStyleClass().add("countdown");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(10, examTitle, spacer, countdown);

        languageChoice.setItems(FXCollections.observableArrayList(ProgrammingLanguage.values()));
        languageChoice.setPromptText("Choose programming language");
        languageChoice.setPrefWidth(210);
        fileName.getStyleClass().add("muted");
        HBox languageBar = new HBox(10, new Label("Language"), languageChoice,
                new Separator(javafx.geometry.Orientation.VERTICAL), fileName);

        question.setEditable(false);
        question.setWrapText(true);
        question.setPrefRowCount(8);
        codeEditor.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");
        output.setEditable(false);
        output.setPrefRowCount(5);
        output.setStyle("-fx-font-family: 'Consolas';");
        compile.setOnAction(event -> compile(false));
        run.setOnAction(event -> compile(true));
        submit.getStyleClass().add("danger-button");
        submit.setOnAction(event -> submit());
        HBox actions = new HBox(9, compile, run, submit);

        SplitPane split = new SplitPane(question, codeEditor);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.34);
        VBox workspace = new VBox(10, heading, languageBar, actions, split, new Label("Output"), output);
        VBox.setVgrow(split, Priority.ALWAYS);
        return workspace;
    }

    public void refresh() {
        FxResponses.observe(gateway.availableExams(), available ->
                exams.setItems(FXCollections.observableArrayList(available)), feedback);
    }

    private void openSelectedExam() {
        ExamSummary selected = exams.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select an exam first");
            return;
        }
        if (selected.status() != ExamSessionStatus.ACTIVE) {
            feedback.setText("The teacher has not started this exam yet");
            return;
        }
        CompletableFuture<OpenedExam> operation = gateway.openExam(selected.id())
                .thenCompose(details -> gateway.examCode(selected.id())
                        .thenApply(code -> new OpenedExam(details, code)));
        FxResponses.observe(operation, opened -> activateExam(opened.details(), opened.code()), feedback);
    }

    private void activateExam(ExamDetails details, ExamCodeSnapshot code) {
        setWorkspaceEnabled(true);
        activeExam = details;
        submitted = code.submitted();
        examOpen = !submitted;
        submissionInFlight = false;
        gateway.setActiveExam(submitted ? null : details.id());
        gateway.recordExamSignal(StudentEventType.JOINED, "Entered the exam workspace");
        examTitle.setText(details.examTitle() + " · " + details.courseCode());
        question.setText(details.questionText());
        output.clear();
        codeRevision = code.revision();
        editingStartedAt = code.editingStartedAt();
        applyingServerCode = true;
        languageChoice.setValue(code.language());
        codeEditor.setText(code.codeText());
        applyingServerCode = false;
        if (code.language() == null) {
            languageChoice.setDisable(false);
            codeEditor.setDisable(true);
            submit.setDisable(true);
            fileName.setText("Choose a language before coding");
            output.setText("Select your programming language. PLIS will set the correct file extension.");
        } else {
            languageChoice.setDisable(true);
            codeEditor.setDisable(submitted);
            codeEditor.setEditable(!submitted);
            submit.setDisable(submitted);
            fileName.setText(code.fileName() + " · saved revision " + code.revision());
        }
        updateCompilerButtons();
        if (submitted) {
            countdown.setText("Submitted " + code.submittedAt().truncatedTo(ChronoUnit.SECONDS));
            output.setText("This submission is saved and available to the teacher.");
        } else {
            stage.setFullScreenExitHint("Leaving full screen is recorded as an integrity signal");
            stage.setFullScreen(true);
            clock.play();
            updateCountdown();
        }
    }

    private void beginLanguage(ProgrammingLanguage language) {
        editingStartedAt = Instant.now();
        applyingServerCode = true;
        codeEditor.setText(language.starterCode());
        applyingServerCode = false;
        languageChoice.setDisable(true);
        codeEditor.setDisable(false);
        codeEditor.setEditable(true);
        submit.setDisable(false);
        fileName.setText(language.defaultFileName() + " · preparing first revision");
        updateCompilerButtons();
        autosave.playFromStart();
        codeEditor.requestFocus();
    }

    private void synchronizeCode() {
        if (!examOpen || submitted || submissionInFlight || activeExam == null
                || languageChoice.getValue() == null) {
            return;
        }
        if (saveInFlight) {
            savePending = true;
            return;
        }
        saveInFlight = true;
        String codeToSave = codeEditor.getText();
        long baseRevision = codeRevision;
        fileName.setText(languageChoice.getValue().defaultFileName() + " · saving…");
        gateway.updateExamCode(activeExam.id(), new ExamCodeUpdateRequest(
                        languageChoice.getValue(), codeToSave, baseRevision, editingStartedAt))
                .whenComplete((saved, failure) -> Platform.runLater(() -> {
                    saveInFlight = false;
                    if (failure != null) {
                        if (!submissionInFlight && !submitted) {
                            feedback.setText(FxResponses.message(failure));
                        }
                    } else {
                        codeRevision = saved.revision();
                        fileName.setText(saved.fileName() + " · saved revision " + saved.revision()
                                + " · visible to teacher");
                    }
                    if (!submissionInFlight && !submitted
                            && (savePending || !codeToSave.equals(codeEditor.getText()))) {
                        savePending = false;
                        autosave.playFromStart();
                    }
                }));
    }

    private void compile(boolean runAfterCompilation) {
        if (!examOpen || submitted || languageChoice.getValue() == null) {
            return;
        }
        if (languageChoice.getValue() != ProgrammingLanguage.JAVA) {
            output.setText("Safe compile checking is currently available for Java only. "
                    + "Your " + languageChoice.getValue().displayName() + " source is still saved live.");
            return;
        }
        synchronizeCode();
        output.setText("Checking Java source…");
        CompletableFuture.supplyAsync(() -> compiler.check(codeEditor.getText()))
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    if (failure != null) {
                        output.setText(FxResponses.message(failure));
                    } else {
                        output.setText(result.output());
                        if (result.successful() && runAfterCompilation) {
                            output.appendText(System.lineSeparator()
                                    + "Run check finished; untrusted code was not executed.");
                        }
                    }
                }));
    }

    private void submit() {
        if (activeExam == null || submitted || submissionInFlight || languageChoice.getValue() == null) {
            feedback.setText("Choose a language and enter code before submitting");
            return;
        }
        if (codeEditor.getText().isBlank()) {
            feedback.setText("Code cannot be empty");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Submission is final. Editing will be locked after the server accepts it.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.setHeaderText("Submit " + languageChoice.getValue().defaultFileName() + "?");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        autosave.stop();
        submissionInFlight = true;
        submit.setDisable(true);
        var operation = gateway.submitCode(activeExam.id(), new CodeSubmissionRequest(
                codeEditor.getText(), editingStartedAt, languageChoice.getValue()));
        operation.whenComplete((receipt, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                submissionInFlight = false;
                submit.setDisable(false);
            }
        }));
        FxResponses.observe(operation, receipt -> {
            submitted = true;
            examOpen = false;
            submissionInFlight = false;
            codeEditor.setEditable(false);
            submit.setDisable(true);
            compile.setDisable(true);
            run.setDisable(true);
            clock.stop();
            countdown.setText("Submitted " + receipt.submittedAt().truncatedTo(ChronoUnit.SECONDS));
            fileName.setText(languageChoice.getValue().defaultFileName() + " · final submission saved");
            output.setText("Submission accepted and visible to the teacher. Receipt #"
                    + receipt.submissionId());
            gateway.setActiveExam(null);
            stage.setFullScreen(false);
        }, feedback);
    }

    private void updateCompilerButtons() {
        boolean java = examOpen && !submitted && languageChoice.getValue() == ProgrammingLanguage.JAVA;
        compile.setDisable(!java);
        run.setDisable(!java);
    }

    private void updateCountdown() {
        if (activeExam == null || activeExam.actualStartTime() == null || submitted) {
            return;
        }
        Instant deadline = activeExam.actualStartTime().plusSeconds(activeExam.durationMinutes() * 60L);
        long remaining = java.time.Duration.between(Instant.now(), deadline).toSeconds();
        if (remaining <= 0) {
            countdown.setText("Time expired");
            synchronizeCode();
            examOpen = false;
            codeEditor.setEditable(false);
            submit.setDisable(true);
            updateCompilerButtons();
            clock.stop();
            gateway.setActiveExam(null);
        } else {
            countdown.setText(String.format("%02d:%02d:%02d", remaining / 3600,
                    (remaining % 3600) / 60, remaining % 60));
        }
    }

    private void setWorkspaceEnabled(boolean enabled) {
        question.setDisable(!enabled);
        codeEditor.setDisable(!enabled);
        codeEditor.setEditable(enabled);
        languageChoice.setDisable(!enabled);
        submit.setDisable(!enabled);
        compile.setDisable(!enabled);
        run.setDisable(!enabled);
    }

    public Parent root() {
        return root;
    }

    private record OpenedExam(ExamDetails details, ExamCodeSnapshot code) { }
}
