package edu.university.plis.teacher.client;

import edu.university.plis.shared.client.PlisApiClient;
import edu.university.plis.shared.client.RealtimeUpdatesClient;
import edu.university.plis.shared.dto.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class TeacherGateway implements AutoCloseable {
    private final PlisApiClient apiClient;
    private final Set<Long> subscribedLabs = new HashSet<>();
    private final Set<Long> subscribedExams = new HashSet<>();
    private volatile RealtimeUpdatesClient realtimeClient;
    private volatile boolean realtimeConnected;

    public TeacherGateway(String serverUrl) {
        this.apiClient = new PlisApiClient(serverUrl);
    }

    public CompletableFuture<LoginResponse> login(String username, String password) {
        return apiClient.login(username, password).thenCompose(login -> {
            realtimeClient = new RealtimeUpdatesClient(apiClient.webSocketUri(), apiClient.accessToken());
            return realtimeClient.connect()
                    .handle((ignored, failure) -> {
                        realtimeConnected = failure == null;
                        return login;
                    });
        });
    }

    public CompletableFuture<List<StudentSummary>> students() {
        return apiClient.listStudents();
    }

    public CompletableFuture<List<StudentAccountView>> studentAccounts() {
        return apiClient.listStudentAccounts();
    }

    public CompletableFuture<StudentAccountView> createStudentAccount(CreateStudentAccountRequest request) {
        return apiClient.createStudentAccount(request);
    }

    public CompletableFuture<List<StudentAccountView>> createStudentAccounts(
            BulkStudentAccountsRequest request) {
        return apiClient.createStudentAccounts(request);
    }

    public CompletableFuture<StudentAccountView> resetStudentPassword(
            long studentId, PasswordResetRequest request) {
        return apiClient.resetStudentPassword(studentId, request);
    }

    public CompletableFuture<StudentAccountView> setStudentEnabled(long studentId, boolean enabled) {
        return apiClient.setStudentEnabled(studentId, new AccountEnabledRequest(enabled));
    }

    public CompletableFuture<Void> terminateStudent(long studentId) {
        return apiClient.terminateStudentConnections(studentId);
    }

    public CompletableFuture<List<ConnectionRequestView>> connections() {
        return apiClient.listConnections();
    }

    public CompletableFuture<ConnectionRequestView> decideConnection(long connectionId, boolean approved) {
        return apiClient.decideConnection(connectionId, new ConnectionDecisionRequest(approved));
    }

    public CompletableFuture<List<AuditLogView>> audit(String username) {
        return apiClient.getAudit(username);
    }

    public CompletableFuture<List<EventLogView>> allStudentEvents(long studentId) {
        return apiClient.getAllStudentEvents(studentId);
    }

    public CompletableFuture<Void> changePassword(String currentPassword, String newPassword) {
        return apiClient.changePassword(new PasswordChangeRequest(currentPassword, newPassword));
    }

    public CompletableFuture<List<LabSessionSummary>> labs() {
        return apiClient.listLabs();
    }

    public CompletableFuture<LabSessionSummary> createLab(CreateLabSessionRequest request) {
        return apiClient.createLab(request);
    }

    public CompletableFuture<LabSessionSummary> startLab(long id) {
        return apiClient.startLab(id);
    }

    public CompletableFuture<LabSessionSummary> endLab(long id) {
        return apiClient.endLab(id);
    }

    public CompletableFuture<LabSessionSummary> updateLabPolicy(long id, UpdateLabPolicyRequest request) {
        return apiClient.updateLabPolicy(id, request);
    }

    public CompletableFuture<List<StudentActivitySnapshot>> labActivity(long id) {
        return apiClient.getLabActivity(id);
    }

    public CompletableFuture<List<EventLogView>> labHistory(long labId, long studentId) {
        return apiClient.getStudentLabEvents(labId, studentId);
    }

    public CompletableFuture<LabCodeSnapshot> labCode(long labId, long studentId) {
        return apiClient.getStudentLabCode(labId, studentId);
    }

    public CompletableFuture<LabCodeSnapshot> updateLabCode(
            long labId, long studentId, LabCodeUpdateRequest request) {
        return apiClient.updateStudentLabCode(labId, studentId, request);
    }

    public synchronized void subscribeToLab(long id, Consumer<StudentActivitySnapshot> listener) {
        if (realtimeConnected && subscribedLabs.add(id)) {
            realtimeClient.subscribeToLab(id, listener);
        }
    }

    public CompletableFuture<List<ExamSummary>> exams() {
        return apiClient.listExams();
    }

    public CompletableFuture<ExamSummary> createExam(CreateExamRequest request) {
        return apiClient.createExam(request);
    }

    public CompletableFuture<ExamSummary> startExam(long id) {
        return apiClient.startExam(id);
    }

    public CompletableFuture<ExamSummary> endExam(long id) {
        return apiClient.endExam(id);
    }

    public CompletableFuture<List<ExamParticipantSnapshot>> examDashboard(long id) {
        return apiClient.getExamDashboard(id);
    }

    public CompletableFuture<List<IntegrityFlagView>> integrityReport(long id) {
        return apiClient.getIntegrityReport(id);
    }

    public CompletableFuture<ExamCodeSnapshot> examCode(long examId, long studentId) {
        return apiClient.getStudentExamCode(examId, studentId);
    }

    public synchronized void subscribeToExam(long id, Consumer<ExamParticipantSnapshot> listener) {
        if (realtimeConnected && subscribedExams.add(id)) {
            realtimeClient.subscribeToExam(id, listener);
        }
    }

    public boolean isRealtimeConnected() {
        return realtimeConnected;
    }

    @Override
    public void close() {
        RealtimeUpdatesClient current = realtimeClient;
        if (current != null) {
            current.close();
        }
    }
}
