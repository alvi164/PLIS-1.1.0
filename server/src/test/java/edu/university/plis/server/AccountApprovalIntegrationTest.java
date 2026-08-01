package edu.university.plis.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.plis.shared.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-approval;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "plis.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class AccountApprovalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void teacherCreatesApprovesAndTerminatesStudentDevice() throws Exception {
        mvc.perform(post("/api/setup/teacher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new InitialTeacherSetupRequest(
                                "owner", "Owner Teacher", "12345"))))
                .andExpect(status().isOk());

        String teacherToken = login(new LoginRequest("owner", "12345"), 200).get("accessToken").asText();
        mvc.perform(post("/api/teacher/accounts")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CreateStudentAccountRequest(
                                "student12", "Student Twelve", "CSE-012", "CSE", 1, "12345"))))
                .andExpect(status().isOk());

        login(new LoginRequest("student12", "12345", "device-12", "Lab PC 12"), 423);

        String connectionsJson = mvc.perform(get("/api/teacher/connections")
                        .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long connectionId = json.readTree(connectionsJson).get(0).get("id").asLong();
        mvc.perform(put("/api/teacher/connections/{id}", connectionId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new ConnectionDecisionRequest(true))))
                .andExpect(status().isOk());

        String studentToken = login(new LoginRequest(
                "student12", "12345", "device-12", "Lab PC 12"), 200).get("accessToken").asText();
        mvc.perform(post("/api/account/heartbeat").header("Authorization", bearer(studentToken)))
                .andExpect(status().isNoContent());

        mvc.perform(put("/api/teacher/connections/{id}", connectionId)
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new ConnectionDecisionRequest(false))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/account/heartbeat").header("Authorization", bearer(studentToken)))
                .andExpect(status().isUnauthorized());
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
