package edu.university.plis.shared.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.university.plis.shared.dto.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PlisApiClient {
    private static final TypeReference<List<StudentSummary>> STUDENT_LIST = new TypeReference<>() { };
    private static final TypeReference<List<LabSessionSummary>> LAB_LIST = new TypeReference<>() { };
    private static final TypeReference<List<StudentActivitySnapshot>> ACTIVITY_LIST = new TypeReference<>() { };
    private static final TypeReference<List<EventLogView>> EVENT_LIST = new TypeReference<>() { };
    private static final TypeReference<List<ExamSummary>> EXAM_LIST = new TypeReference<>() { };
    private static final TypeReference<List<ExamParticipantSnapshot>> PARTICIPANT_LIST = new TypeReference<>() { };
    private static final TypeReference<List<IntegrityFlagView>> FLAG_LIST = new TypeReference<>() { };
    private static final TypeReference<List<StudentAccountView>> ACCOUNT_LIST = new TypeReference<>() { };
    private static final TypeReference<List<ConnectionRequestView>> CONNECTION_LIST = new TypeReference<>() { };
    private static final TypeReference<List<AuditLogView>> AUDIT_LIST = new TypeReference<>() { };

    private final URI serverBaseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String accessToken;

    public PlisApiClient(String serverBaseUrl) {
        String normalizedUrl = Objects.requireNonNull(serverBaseUrl).replaceAll("/+$", "");
        this.serverBaseUri = URI.create(normalizedUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public CompletableFuture<LoginResponse> login(String username, String password) {
        return login(new LoginRequest(username, password));
    }

    public CompletableFuture<LoginResponse> login(LoginRequest request) {
        return post("/api/auth/login", request, LoginResponse.class, false)
                .thenApply(response -> {
                    accessToken = response.accessToken();
                    return response;
                });
    }

    public CompletableFuture<SetupStatus> setupStatus() {
        return getPublic("/api/setup/status", SetupStatus.class);
    }

    public CompletableFuture<SetupStatus> initializeTeacher(InitialTeacherSetupRequest request) {
        return post("/api/setup/teacher", request, SetupStatus.class, false);
    }

    public CompletableFuture<List<StudentSummary>> listStudents() {
        return get("/api/teacher/students", STUDENT_LIST);
    }

    public CompletableFuture<List<StudentAccountView>> listStudentAccounts() {
        return get("/api/teacher/accounts", ACCOUNT_LIST);
    }

    public CompletableFuture<StudentAccountView> createStudentAccount(CreateStudentAccountRequest request) {
        return post("/api/teacher/accounts", request, StudentAccountView.class, true);
    }

    public CompletableFuture<List<StudentAccountView>> createStudentAccounts(BulkStudentAccountsRequest request) {
        return post("/api/teacher/accounts/bulk", request, ACCOUNT_LIST, true);
    }

    public CompletableFuture<StudentAccountView> resetStudentPassword(
            long studentId, PasswordResetRequest request) {
        return put("/api/teacher/accounts/" + studentId + "/password", request, StudentAccountView.class);
    }

    public CompletableFuture<StudentAccountView> setStudentEnabled(
            long studentId, AccountEnabledRequest request) {
        return put("/api/teacher/accounts/" + studentId + "/enabled", request, StudentAccountView.class);
    }

    public CompletableFuture<Void> terminateStudentConnections(long studentId) {
        return postWithoutResponse("/api/teacher/accounts/" + studentId + "/terminate", Map.of());
    }

    public CompletableFuture<List<ConnectionRequestView>> listConnections() {
        return get("/api/teacher/connections", CONNECTION_LIST);
    }

    public CompletableFuture<ConnectionRequestView> decideConnection(
            long connectionId, ConnectionDecisionRequest request) {
        return put("/api/teacher/connections/" + connectionId, request, ConnectionRequestView.class);
    }

    public CompletableFuture<List<AuditLogView>> getAudit(String username) {
        String query = username == null || username.isBlank() ? "" : "?username=" + encode(username);
        return get("/api/teacher/audit" + query, AUDIT_LIST);
    }

    public CompletableFuture<List<EventLogView>> getAllStudentEvents(long studentId) {
        return get("/api/teacher/students/" + studentId + "/all-events", EVENT_LIST);
    }

    public CompletableFuture<Void> changePassword(PasswordChangeRequest request) {
        return putWithoutResponse("/api/account/password", request);
    }

    public CompletableFuture<Void> heartbeat() {
        return postWithoutResponse("/api/account/heartbeat", Map.of());
    }

    public CompletableFuture<List<LabSessionSummary>> listLabs() {
        return get("/api/labs", LAB_LIST);
    }

    public CompletableFuture<List<LabSessionSummary>> listAvailableLabs() {
        return get("/api/labs/available", LAB_LIST);
    }

    public CompletableFuture<LabSessionSummary> createLab(CreateLabSessionRequest request) {
        return post("/api/labs", request, LabSessionSummary.class, true);
    }

    public CompletableFuture<LabSessionSummary> startLab(long labSessionId) {
        return post("/api/labs/" + labSessionId + "/start", Map.of(), LabSessionSummary.class, true);
    }

    public CompletableFuture<LabSessionSummary> endLab(long labSessionId) {
        return post("/api/labs/" + labSessionId + "/end", Map.of(), LabSessionSummary.class, true);
    }

    public CompletableFuture<LabSessionSummary> updateLabPolicy(
            long labSessionId, UpdateLabPolicyRequest request) {
        return put("/api/labs/" + labSessionId + "/policy", request, LabSessionSummary.class);
    }

    public CompletableFuture<Void> joinLab(long labSessionId, JoinLabRequest request) {
        return postWithoutResponse("/api/labs/" + labSessionId + "/join", request);
    }

    public CompletableFuture<Void> recordLabEvent(long labSessionId, LabEventRequest event) {
        return postWithoutResponse("/api/labs/" + labSessionId + "/events", event);
    }

    public CompletableFuture<List<StudentActivitySnapshot>> getLabActivity(long labSessionId) {
        return get("/api/labs/" + labSessionId + "/activity", ACTIVITY_LIST);
    }

    public CompletableFuture<List<EventLogView>> getStudentLabEvents(long labSessionId, long studentId) {
        return get("/api/labs/" + labSessionId + "/students/" + studentId + "/events", EVENT_LIST);
    }

    public CompletableFuture<LabCodeSnapshot> getStudentLabCode(long labSessionId) {
        return get("/api/labs/" + labSessionId + "/code", LabCodeSnapshot.class);
    }

    public CompletableFuture<LabCodeSnapshot> updateStudentLabCode(
            long labSessionId, LabCodeUpdateRequest request) {
        return put("/api/labs/" + labSessionId + "/code", request, LabCodeSnapshot.class);
    }

    public CompletableFuture<LabCodeSnapshot> getStudentLabCode(long labSessionId, long studentId) {
        return get("/api/labs/" + labSessionId + "/students/" + studentId + "/code", LabCodeSnapshot.class);
    }

    public CompletableFuture<LabCodeSnapshot> updateStudentLabCode(
            long labSessionId, long studentId, LabCodeUpdateRequest request) {
        return put("/api/labs/" + labSessionId + "/students/" + studentId + "/code",
                request, LabCodeSnapshot.class);
    }

    public CompletableFuture<List<ExamSummary>> listExams() {
        return get("/api/exams", EXAM_LIST);
    }

    public CompletableFuture<List<ExamSummary>> listAvailableExams() {
        return get("/api/exams/available", EXAM_LIST);
    }

    public CompletableFuture<ExamSummary> createExam(CreateExamRequest request) {
        return post("/api/exams", request, ExamSummary.class, true);
    }

    public CompletableFuture<ExamSummary> startExam(long examSessionId) {
        return post("/api/exams/" + examSessionId + "/start", Map.of(), ExamSummary.class, true);
    }

    public CompletableFuture<ExamSummary> endExam(long examSessionId) {
        return post("/api/exams/" + examSessionId + "/end", Map.of(), ExamSummary.class, true);
    }

    public CompletableFuture<ExamDetails> getExam(long examSessionId) {
        return get("/api/exams/" + examSessionId, ExamDetails.class);
    }

    public CompletableFuture<Void> recordExamSignal(long examSessionId, ExamSignalRequest signal) {
        return postWithoutResponse("/api/exams/" + examSessionId + "/signals", signal);
    }

    public CompletableFuture<SubmissionReceipt> submitCode(long examSessionId, CodeSubmissionRequest submission) {
        return post("/api/exams/" + examSessionId + "/submissions", submission, SubmissionReceipt.class, true);
    }

    public CompletableFuture<ExamCodeSnapshot> getStudentExamCode(long examSessionId) {
        return get("/api/exams/" + examSessionId + "/code", ExamCodeSnapshot.class);
    }

    public CompletableFuture<ExamCodeSnapshot> updateStudentExamCode(
            long examSessionId, ExamCodeUpdateRequest request) {
        return put("/api/exams/" + examSessionId + "/code", request, ExamCodeSnapshot.class);
    }

    public CompletableFuture<ExamCodeSnapshot> getStudentExamCode(long examSessionId, long studentId) {
        return get("/api/exams/" + examSessionId + "/students/" + studentId + "/code",
                ExamCodeSnapshot.class);
    }

    public CompletableFuture<List<ExamParticipantSnapshot>> getExamDashboard(long examSessionId) {
        return get("/api/exams/" + examSessionId + "/dashboard", PARTICIPANT_LIST);
    }

    public CompletableFuture<List<IntegrityFlagView>> getIntegrityReport(long examSessionId) {
        return get("/api/exams/" + examSessionId + "/integrity", FLAG_LIST);
    }

    public String accessToken() {
        return accessToken;
    }

    public URI webSocketUri() {
        String scheme = "https".equalsIgnoreCase(serverBaseUri.getScheme()) ? "wss" : "ws";
        try {
            return new URI(scheme, serverBaseUri.getUserInfo(), serverBaseUri.getHost(),
                    serverBaseUri.getPort(), "/ws", null, null);
        } catch (Exception exception) {
            throw new ApiClientException("Could not build the WebSocket URL", exception);
        }
    }

    private <T> CompletableFuture<T> get(String path, Class<T> responseType) {
        return send(requestBuilder(path, true).GET().build(), responseType);
    }

    private <T> CompletableFuture<T> getPublic(String path, Class<T> responseType) {
        return send(requestBuilder(path, false).GET().build(), responseType);
    }

    private <T> CompletableFuture<T> get(String path, TypeReference<T> responseType) {
        return send(requestBuilder(path, true).GET().build(), responseType);
    }

    private <T> CompletableFuture<T> post(
            String path, Object requestBody, Class<T> responseType, boolean authenticated) {
        return send(requestBuilder(path, authenticated)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                .build(), responseType);
    }

    private <T> CompletableFuture<T> post(
            String path, Object requestBody, TypeReference<T> responseType, boolean authenticated) {
        return send(requestBuilder(path, authenticated)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                .build(), responseType);
    }

    private CompletableFuture<Void> postWithoutResponse(String path, Object requestBody) {
        return send(requestBuilder(path, true)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                .build(), Void.class);
    }

    private <T> CompletableFuture<T> put(String path, Object requestBody, Class<T> responseType) {
        return send(requestBuilder(path, true)
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                .build(), responseType);
    }

    private CompletableFuture<Void> putWithoutResponse(String path, Object requestBody) {
        return send(requestBuilder(path, true)
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                .build(), Void.class);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder requestBuilder(String path, boolean authenticated) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverBaseUri.resolve(path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (authenticated) {
            String currentToken = accessToken;
            if (currentToken == null || currentToken.isBlank()) {
                throw new ApiClientException(401, "Login is required");
            }
            builder.header("Authorization", "Bearer " + currentToken);
        }
        return builder;
    }

    private <T> CompletableFuture<T> send(HttpRequest request, Class<T> responseType) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> readResponse(response, responseType));
    }

    private <T> CompletableFuture<T> send(HttpRequest request, TypeReference<T> responseType) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> readResponse(response, responseType));
    }

    private <T> T readResponse(HttpResponse<String> response, Class<T> responseType) {
        ensureSuccess(response);
        if (responseType == Void.class || response.body().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException exception) {
            throw new ApiClientException("The server returned an unreadable response", exception);
        }
    }

    private <T> T readResponse(HttpResponse<String> response, TypeReference<T> responseType) {
        ensureSuccess(response);
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonProcessingException exception) {
            throw new ApiClientException("The server returned an unreadable response", exception);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        String message = "Server request failed with status " + response.statusCode();
        try {
            ApiError error = objectMapper.readValue(response.body(), ApiError.class);
            if (error.message() != null && !error.message().isBlank()) {
                message = error.message();
            }
        } catch (JsonProcessingException ignored) {
            // Some infrastructure errors do not use the application's error envelope.
        }
        throw new ApiClientException(response.statusCode(), message);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiClientException("Could not encode the request", exception);
        }
    }
}
