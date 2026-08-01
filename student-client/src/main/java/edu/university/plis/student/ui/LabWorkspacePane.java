package edu.university.plis.student.ui;

import edu.university.plis.shared.dto.LabCodeSnapshot;
import edu.university.plis.shared.dto.LabSessionSummary;
import edu.university.plis.shared.model.CodeAuthorType;
import edu.university.plis.shared.model.NetworkConnectionType;
import edu.university.plis.shared.model.StudentEventType;
import edu.university.plis.student.client.InMemoryJavaCompiler;
import edu.university.plis.student.client.StudentGateway;
import edu.university.plis.student.client.VisibleActivityMonitor;
import edu.university.plis.student.model.CompilationResult;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

import java.util.concurrent.CompletableFuture;

public final class LabWorkspacePane implements AutoCloseable {
    private static final String STARTER_CODE = """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, lab!");
                }
            }
            """;

    private final StudentGateway gateway;
    private final InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();
    private final BorderPane root = new BorderPane();
    private final ListView<LabSessionSummary> sessions = new ListView<>();
    private final CodeArea codeEditor = new CodeArea();
    private final TextArea output = new TextArea();
    private final Label feedback = new Label();
    private final Label synchronization = new Label("Join a class to enable server-backed live code");
    private final PauseTransition autosave = new PauseTransition(Duration.millis(800));
    private final Timeline codePolling = new Timeline(new KeyFrame(Duration.seconds(1), event -> pollCode()));
    private final Timeline networkPolling = new Timeline(new KeyFrame(Duration.seconds(5), event -> pollNetwork()));
    private Long joinedLabId;
    private long codeRevision;
    private boolean applyingServerCode;
    private boolean saveInFlight;
    private boolean savePending;
    private NetworkConnectionType lastConnection = NetworkConnectionType.UNKNOWN;
    private VisibleActivityMonitor activityMonitor;

    public LabWorkspacePane(StudentGateway gateway) {
        this.gateway = gateway;
        codeEditor.replaceText(STARTER_CODE);
        root.setPadding(new Insets(18));
        root.setLeft(buildSessions());
        root.setCenter(buildEditor());
        codeEditor.setDisable(true);
        autosave.setOnFinished(event -> synchronizeCode());
        codePolling.setCycleCount(Timeline.INDEFINITE);
        networkPolling.setCycleCount(Timeline.INDEFINITE);
        codeEditor.textProperty().addListener((observable, previous, current) -> {
            if (joinedLabId != null && !applyingServerCode) {
                clearTeacherHighlights();
                synchronization.setText("Student edit pending…");
                autosave.playFromStart();
            }
        });
    }

    private Parent buildSessions() {
        Label title = new Label("Available labs");
        title.getStyleClass().add("section-title");
        sessions.setPrefWidth(330);
        Button refresh = new Button("Refresh");
        Button join = new Button("Join selected lab");
        join.getStyleClass().add("primary-button");
        join.setOnAction(event -> joinSelected());
        refresh.setOnAction(event -> refresh());
        feedback.getStyleClass().add("error-label");
        feedback.setWrapText(true);
        Label disclosure = new Label("While joined, PLIS stores code revisions, compile/run events, "
                + "network type, and detected browser/AI application activity. It does not read passwords "
                + "or encrypted browser search contents.");
        disclosure.setWrapText(true);
        disclosure.getStyleClass().add("monitoring-disclosure");
        VBox sidebar = new VBox(10, title, sessions, new HBox(8, join, refresh), disclosure, feedback);
        sidebar.setPadding(new Insets(0, 18, 0, 0));
        return sidebar;
    }

    private Parent buildEditor() {
        Label title = new Label("Java workspace");
        title.getStyleClass().add("section-title");
        codeEditor.getStyleClass().add("code-editor");
        codeEditor.setWrapText(false);
        VirtualizedScrollPane<CodeArea> editorScroll = new VirtualizedScrollPane<>(codeEditor);
        synchronization.getStyleClass().add("muted");
        output.setEditable(false);
        output.setPrefRowCount(7);
        output.getStyleClass().add("code-output");
        Button save = new Button("Save now");
        Button compile = new Button("Compile check");
        Button run = new Button("Run check");
        save.setOnAction(event -> {
            synchronizeCode();
            sendEvent(StudentEventType.FILE_SAVE, "Main.java");
        });
        compile.setOnAction(event -> compile(false));
        run.setOnAction(event -> compile(true));
        HBox actions = new HBox(9, save, compile, run);
        VBox editor = new VBox(8, title, synchronization, actions, editorScroll, new Label("Output"), output);
        VBox.setVgrow(editorScroll, Priority.ALWAYS);
        return editor;
    }

    public void refresh() {
        FxResponses.observe(gateway.availableLabs(), labs ->
                sessions.setItems(FXCollections.observableArrayList(labs)), feedback);
    }

    private void joinSelected() {
        LabSessionSummary selected = sessions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            feedback.setText("Select a lab session first");
            return;
        }
        lastConnection = gateway.currentConnectionType();
        FxResponses.observe(gateway.joinLab(selected.id()), ignored -> {
            joinedLabId = selected.id();
            codeEditor.setDisable(false);
            output.setText("Joined " + selected.courseCode() + " / " + selected.section()
                    + " using " + lastConnection + ". Seat " + (selected.joinedStudentCount() + 1)
                    + " of " + selected.maxParticipants() + ".");
            startMonitoring();
            loadInitialCode();
        }, feedback);
    }

    private void loadInitialCode() {
        Long labId = joinedLabId;
        if (labId == null) {
            return;
        }
        gateway.labCode(labId).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                feedback.setText(FxResponses.message(failure));
                return;
            }
            boolean emptyDocument = snapshot.revision() == 0 && snapshot.codeText().isBlank();
            applySnapshot(snapshot, emptyDocument ? STARTER_CODE : snapshot.codeText(), false);
            if (emptyDocument) {
                synchronizeCode();
            }
        }));
    }

    private void synchronizeCode() {
        Long labId = joinedLabId;
        if (labId == null) {
            return;
        }
        if (saveInFlight) {
            savePending = true;
            return;
        }
        saveInFlight = true;
        String codeToSave = codeEditor.getText();
        long baseRevision = codeRevision;
        synchronization.setText("Saving code revision…");
        gateway.updateLabCode(labId, codeToSave, baseRevision)
                .whenComplete((saved, failure) -> Platform.runLater(() -> {
                    saveInFlight = false;
                    if (failure != null) {
                        synchronization.setText(FxResponses.message(failure));
                        if (isClassAccessClosed(failure)) {
                            stopJoinedLab("The class ended or access was withdrawn");
                        } else {
                            pollCode();
                        }
                    } else {
                        codeRevision = saved.revision();
                        synchronization.setText("Saved revision " + codeRevision + " · visible to teacher");
                    }
                    if (savePending || !codeToSave.equals(codeEditor.getText())) {
                        savePending = false;
                        autosave.playFromStart();
                    }
                }));
    }

    private void pollCode() {
        Long labId = joinedLabId;
        if (labId == null) {
            return;
        }
        gateway.labCode(labId).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                if (isClassAccessClosed(failure)) {
                    stopJoinedLab("The class ended or access was withdrawn");
                }
                return;
            }
            if (snapshot.revision() <= codeRevision) {
                return;
            }
            if (snapshot.lastAuthorType() == CodeAuthorType.TEACHER) {
                applySnapshot(snapshot, snapshot.codeText(), true);
                synchronization.setText("Teacher-changed text is red · revision " + snapshot.revision()
                        + " by " + snapshot.lastAuthorName());
            } else if (!saveInFlight) {
                applySnapshot(snapshot, snapshot.codeText(), false);
                synchronization.setText("Loaded newer student revision " + snapshot.revision());
            }
        }));
    }

    private void applySnapshot(LabCodeSnapshot snapshot, String text, boolean highlightTeacherChange) {
        String previous = codeEditor.getText();
        applyingServerCode = true;
        codeEditor.replaceText(text);
        clearTeacherHighlights();
        if (highlightTeacherChange) {
            int[] range = changedRange(previous, text);
            if (range[1] > range[0]) {
                codeEditor.setStyleClass(range[0], range[1], "teacher-code-range");
            }
        }
        applyingServerCode = false;
        codeRevision = snapshot.revision();
    }

    private int[] changedRange(String previous, String current) {
        int prefix = 0;
        int maximumPrefix = Math.min(previous.length(), current.length());
        while (prefix < maximumPrefix && previous.charAt(prefix) == current.charAt(prefix)) {
            prefix++;
        }
        int previousSuffix = previous.length();
        int currentSuffix = current.length();
        while (previousSuffix > prefix && currentSuffix > prefix
                && previous.charAt(previousSuffix - 1) == current.charAt(currentSuffix - 1)) {
            previousSuffix--;
            currentSuffix--;
        }
        return new int[]{prefix, currentSuffix};
    }

    private void clearTeacherHighlights() {
        if (codeEditor.getLength() > 0) {
            codeEditor.clearStyle(0, codeEditor.getLength());
        }
    }

    private void startMonitoring() {
        codePolling.play();
        networkPolling.play();
        if (activityMonitor != null) {
            activityMonitor.close();
        }
        activityMonitor = new VisibleActivityMonitor(this::sendEvent);
        activityMonitor.start();
        synchronization.setText("Live code and disclosed classroom activity monitoring are active");
    }

    private void pollNetwork() {
        NetworkConnectionType current = gateway.currentConnectionType();
        if (current != lastConnection) {
            lastConnection = current;
            sendEvent(StudentEventType.NETWORK_CHANGED, current.name());
        }
    }

    private void compile(boolean runAfterCompilation) {
        if (joinedLabId == null) {
            feedback.setText("Join an active lab session first");
            return;
        }
        synchronizeCode();
        sendEvent(StudentEventType.COMPILE_START, null);
        output.setText("Checking Java source…");
        CompletableFuture.supplyAsync(() -> compiler.check(codeEditor.getText()))
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    if (failure != null) {
                        output.setText(FxResponses.message(failure));
                        sendEvent(StudentEventType.COMPILE_ERROR, output.getText());
                    } else {
                        finishCompilation(result, runAfterCompilation);
                    }
                }));
    }

    private void finishCompilation(CompilationResult result, boolean runAfterCompilation) {
        output.setText(result.output());
        sendEvent(result.successful() ? StudentEventType.COMPILE_SUCCESS : StudentEventType.COMPILE_ERROR,
                result.successful() ? null : result.output());
        if (result.successful() && runAfterCompilation) {
            sendEvent(StudentEventType.RUN_START, "Safe compile-only run check");
            output.appendText(System.lineSeparator() + "Run check finished; untrusted code was not executed.");
            sendEvent(StudentEventType.RUN_END, null);
        }
    }

    private void sendEvent(StudentEventType eventType, String detail) {
        Long labId = joinedLabId;
        if (labId == null) {
            return;
        }
        gateway.recordLabEvent(labId, eventType, detail).whenComplete((ignored, failure) -> {
            if (failure != null) {
                Platform.runLater(() -> {
                    if (isClassAccessClosed(failure)) {
                        stopJoinedLab("The class ended or access was withdrawn");
                    } else {
                        feedback.setText(FxResponses.message(failure));
                    }
                });
            }
        });
    }

    private boolean isClassAccessClosed(Throwable failure) {
        int statusCode = FxResponses.statusCode(failure);
        return statusCode == 403 || statusCode == 409;
    }

    private void stopJoinedLab(String message) {
        joinedLabId = null;
        autosave.stop();
        codePolling.stop();
        networkPolling.stop();
        codeEditor.setDisable(true);
        synchronization.setText(message + ". Classroom monitoring is stopped.");
        if (activityMonitor != null) {
            activityMonitor.close();
            activityMonitor = null;
        }
        refresh();
    }

    @Override
    public void close() {
        codePolling.stop();
        networkPolling.stop();
        if (activityMonitor != null) {
            activityMonitor.close();
        }
    }

    public Parent root() {
        return root;
    }
}
