package edu.university.plis.student.client;

import edu.university.plis.shared.client.ApiClientException;
import edu.university.plis.shared.client.PlisApiClient;
import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.NetworkConnectionType;
import edu.university.plis.shared.model.StudentEventType;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class StudentGateway implements AutoCloseable {
    private static final String DEVICE_ID_KEY = "device-id";

    private final PlisApiClient apiClient;
    private final NetworkConnectionDetector connectionDetector;
    private final AtomicBoolean disconnectedDuringExam = new AtomicBoolean();
    private final AtomicBoolean backgroundLoginRunning = new AtomicBoolean();
    private final ScheduledExecutorService connectionExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "plis-student-connection");
        thread.setDaemon(true);
        return thread;
    });
    private final String deviceId;
    private final String deviceName;
    private volatile Long activeExamId;
    private volatile String rememberedUsername;
    private volatile String rememberedPassword;
    private volatile boolean authenticated;
    private volatile Consumer<String> loginStatusListener = ignored -> { };

    public StudentGateway(String serverUrl) {
        this.apiClient = new PlisApiClient(serverUrl);
        this.connectionDetector = new NetworkConnectionDetector(serverUrl);
        Preferences preferences = Preferences.userNodeForPackage(StudentGateway.class);
        String existingDeviceId = preferences.get(DEVICE_ID_KEY, null);
        String resolvedDeviceId = existingDeviceId == null ? UUID.randomUUID().toString() : existingDeviceId;
        if (existingDeviceId == null) {
            preferences.put(DEVICE_ID_KEY, resolvedDeviceId);
        }
        this.deviceId = resolvedDeviceId;
        this.deviceName = computerName();
        connectionExecutor.scheduleWithFixedDelay(this::maintainConnection, 10, 10, TimeUnit.SECONDS);
    }

    public void setLoginStatusListener(Consumer<String> listener) {
        loginStatusListener = listener == null ? ignored -> { } : listener;
    }

    public CompletableFuture<LoginResponse> login(String username, String password) {
        rememberedUsername = username;
        rememberedPassword = password;
        CompletableFuture<LoginResponse> result = new CompletableFuture<>();
        attemptLogin(result);
        return result;
    }

    private void attemptLogin(CompletableFuture<LoginResponse> result) {
        if (result.isDone()) {
            return;
        }
        apiClient.login(new LoginRequest(rememberedUsername, rememberedPassword, deviceId, deviceName))
                .whenComplete((login, failure) -> {
                    if (failure == null) {
                        authenticated = true;
                        loginStatusListener.accept("Teacher approved this computer. Connected.");
                        result.complete(login);
                    } else if (statusCode(failure) == 423) {
                        authenticated = false;
                        loginStatusListener.accept("Access request sent. Waiting for teacher approval…");
                        connectionExecutor.schedule(() -> attemptLogin(result), 3, TimeUnit.SECONDS);
                    } else {
                        result.completeExceptionally(unwrap(failure));
                    }
                });
    }

    private void maintainConnection() {
        if (rememberedUsername == null || rememberedPassword == null) {
            return;
        }
        if (authenticated) {
            apiClient.heartbeat().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    authenticated = false;
                    loginStatusListener.accept("Connection lost or terminated. Requesting teacher access…");
                }
            });
            return;
        }
        if (!backgroundLoginRunning.compareAndSet(false, true)) {
            return;
        }
        apiClient.login(new LoginRequest(rememberedUsername, rememberedPassword, deviceId, deviceName))
                .whenComplete((login, failure) -> {
                    authenticated = failure == null;
                    if (authenticated) {
                        loginStatusListener.accept("Connection restored.");
                    }
                    backgroundLoginRunning.set(false);
                });
    }

    public CompletableFuture<Void> changePassword(String currentPassword, String newPassword) {
        return monitor(apiClient.changePassword(new PasswordChangeRequest(currentPassword, newPassword)))
                .thenRun(() -> rememberedPassword = newPassword);
    }

    public CompletableFuture<List<LabSessionSummary>> availableLabs() {
        return monitor(apiClient.listAvailableLabs());
    }

    public CompletableFuture<Void> joinLab(long labSessionId) {
        return monitor(apiClient.joinLab(labSessionId, new JoinLabRequest(currentConnectionType())));
    }

    public NetworkConnectionType currentConnectionType() {
        return connectionDetector.detect();
    }

    public CompletableFuture<LabCodeSnapshot> labCode(long labSessionId) {
        return monitor(apiClient.getStudentLabCode(labSessionId));
    }

    public CompletableFuture<LabCodeSnapshot> updateLabCode(
            long labSessionId, String codeText, long baseRevision) {
        return monitor(apiClient.updateStudentLabCode(
                labSessionId, new LabCodeUpdateRequest(codeText, baseRevision)));
    }

    public CompletableFuture<Void> recordLabEvent(long labSessionId, StudentEventType type, String detail) {
        return monitor(apiClient.recordLabEvent(labSessionId, new LabEventRequest(type, detail)));
    }

    public CompletableFuture<List<ExamSummary>> availableExams() {
        return monitor(apiClient.listAvailableExams());
    }

    public CompletableFuture<ExamDetails> openExam(long examSessionId) {
        return monitor(apiClient.getExam(examSessionId));
    }

    public CompletableFuture<ExamCodeSnapshot> examCode(long examSessionId) {
        return monitor(apiClient.getStudentExamCode(examSessionId));
    }

    public CompletableFuture<ExamCodeSnapshot> updateExamCode(
            long examSessionId, ExamCodeUpdateRequest request) {
        return monitor(apiClient.updateStudentExamCode(examSessionId, request));
    }

    public void setActiveExam(Long examSessionId) {
        this.activeExamId = examSessionId;
        if (examSessionId == null) {
            disconnectedDuringExam.set(false);
        }
    }

    public CompletableFuture<Void> recordExamSignal(StudentEventType type, String detail) {
        Long examId = activeExamId;
        if (examId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return monitor(apiClient.recordExamSignal(examId, new ExamSignalRequest(type, detail)));
    }

    public CompletableFuture<SubmissionReceipt> submitCode(long examSessionId, CodeSubmissionRequest request) {
        return monitor(apiClient.submitCode(examSessionId, request));
    }

    public String deviceId() { return deviceId; }
    public String deviceName() { return deviceName; }

    private <T> CompletableFuture<T> monitor(CompletableFuture<T> request) {
        request.whenComplete((result, failure) -> {
            Long examId = activeExamId;
            if (examId == null) {
                return;
            }
            if (failure != null) {
                disconnectedDuringExam.set(true);
            } else if (disconnectedDuringExam.compareAndSet(true, false)) {
                apiClient.recordExamSignal(examId, new ExamSignalRequest(
                        StudentEventType.DISCONNECTED, "Client reconnected after a server communication failure"));
            }
        });
        return request;
    }

    private int statusCode(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof ApiClientException apiFailure ? apiFailure.statusCode() : -1;
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String computerName() {
        String environmentName = System.getenv("COMPUTERNAME");
        if (environmentName != null && !environmentName.isBlank()) {
            return environmentName;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "Student computer";
        }
    }

    @Override
    public void close() {
        connectionExecutor.shutdownNow();
    }
}
