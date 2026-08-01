package edu.university.plis.server.service;

import edu.university.plis.server.domain.*;
import edu.university.plis.shared.dto.*;

public final class ViewMapper {
    private ViewMapper() {
    }

    public static StudentSummary toStudentSummary(StudentProfile student) {
        return new StudentSummary(student.getId(), student.getStudentNumber(), student.getUser().getDisplayName(),
                student.getProgram(), student.getSemester());
    }

    public static LabSessionSummary toLabSummary(LabSession session) {
        return new LabSessionSummary(session.getId(), session.getCourseCode(), session.getSection(),
                session.getTeacher().getUser().getDisplayName(), session.getStartTime(), session.getEndTime(),
                session.getStatus(), session.getAssignedStudents().size(), session.getMaxParticipants(),
                session.getJoinedStudents().size(), session.getConnectionPolicy());
    }

    public static ExamSummary toExamSummary(ExamSession session) {
        return new ExamSummary(session.getId(), session.getExamTitle(), session.getCourseCode(),
                session.getScheduledStartTime(), session.getDurationMinutes(), session.getStatus(),
                session.getAssignedStudents().size());
    }

    public static ExamDetails toExamDetails(ExamSession session) {
        return new ExamDetails(session.getId(), session.getExamTitle(), session.getCourseCode(),
                session.getScheduledStartTime(), session.getActualStartTime(), session.getEndedAt(),
                session.getDurationMinutes(), session.getQuestionText(), session.getStatus());
    }

    public static EventLogView toEventView(EventLog event) {
        return new EventLogView(event.getId(), event.getSessionType(), event.getSessionId(),
                event.getStudent().getId(), event.getStudent().getUser().getDisplayName(),
                event.getEventType(), event.getPayload(), event.getTimestamp());
    }

    public static IntegrityFlagView toIntegrityFlagView(IntegrityFlag flag) {
        return new IntegrityFlagView(flag.getId(), flag.getExamSession().getId(), flag.getStudent().getId(),
                flag.getStudent().getUser().getDisplayName(), flag.getFlagType(), flag.getDescription(),
                flag.getTimestamp());
    }
}
