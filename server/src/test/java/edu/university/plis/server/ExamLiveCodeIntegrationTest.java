package edu.university.plis.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.plis.shared.dto.*;
import edu.university.plis.shared.model.ProgrammingLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:exam-live-code;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "plis.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class ExamLiveCodeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void bulkAccountsLivePythonCodeSubmissionAndTeacherViewArePersisted() throws Exception {
        mvc.perform(post("/api/setup/teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new InitialTeacherSetupRequest(
                                "examowner", "Exam Owner", "12345"))))
                .andExpect(status().isOk());
        String teacherToken = login(new LoginRequest("examowner", "12345"), 200)
                .get("accessToken").asText();

        String bulkBody = mvc.perform(post("/api/teacher/accounts/bulk")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new BulkStudentAccountsRequest(List.of(
                                new CreateStudentAccountRequest(
                                        "candidate01", "Candidate One", "E-001", "CSE", 2, "12345"),
                                new CreateStudentAccountRequest(
                                        "candidate02", "Candidate Two", "E-002", "CSE", 2, "12345"))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode accounts = json.readTree(bulkBody);
        assertThat(accounts).hasSize(2);
        long studentId = accounts.get(0).get("profileId").asLong();

        login(new LoginRequest("candidate01", "12345", "exam-device-1", "Exam PC 1"), 423);
        JsonNode connections = json.readTree(mvc.perform(get("/api/teacher/connections")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long connectionId = connections.get(0).get("id").asLong();
        mvc.perform(put("/api/teacher/connections/{id}", connectionId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new ConnectionDecisionRequest(true))))
                .andExpect(status().isOk());
        String studentToken = login(new LoginRequest(
                "candidate01", "12345", "exam-device-1", "Exam PC 1"), 200)
                .get("accessToken").asText();

        String createdExam = mvc.perform(post("/api/exams")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CreateExamRequest(
                                "Algorithms Final", "CSE-220", Instant.now(), 120,
                                "Solve the problem", Set.of(studentId)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long examId = json.readTree(createdExam).get("id").asLong();
        mvc.perform(post("/api/exams/{id}/start", examId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk());

        String pythonCode = "print(sum(map(int, input().split())))\n";
        mvc.perform(put("/api/exams/{id}/code", examId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new ExamCodeUpdateRequest(
                                ProgrammingLanguage.PYTHON, pythonCode, 0, Instant.now()))))
                .andExpect(status().isOk());

        JsonNode liveCode = teacherCode(teacherToken, examId, studentId);
        assertThat(liveCode.get("language").asText()).isEqualTo("PYTHON");
        assertThat(liveCode.get("fileName").asText()).isEqualTo("solution.py");
        assertThat(liveCode.get("codeText").asText()).isEqualTo(pythonCode);
        assertThat(liveCode.get("submitted").asBoolean()).isFalse();

        mvc.perform(post("/api/exams/{id}/submissions", examId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CodeSubmissionRequest(
                                pythonCode, Instant.now(), ProgrammingLanguage.PYTHON))))
                .andExpect(status().isCreated());

        JsonNode submittedCode = teacherCode(teacherToken, examId, studentId);
        assertThat(submittedCode.get("submitted").asBoolean()).isTrue();
        assertThat(submittedCode.get("codeText").asText()).isEqualTo(pythonCode);
        JsonNode dashboard = json.readTree(mvc.perform(get("/api/exams/{id}/dashboard", examId)
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(dashboard.get(0).get("language").asText()).isEqualTo("PYTHON");
        assertThat(dashboard.get(0).get("fileName").asText()).isEqualTo("solution.py");
    }

    private JsonNode teacherCode(String token, long examId, long studentId) throws Exception {
        return json.readTree(mvc.perform(get("/api/exams/{examId}/students/{studentId}/code",
                                examId, studentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode login(LoginRequest request, int expectedStatus) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
