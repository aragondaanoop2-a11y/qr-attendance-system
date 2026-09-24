package edu.cse203.qrattendance;

import java.time.Instant;

public record AttendanceRecord(
        String attendanceId,
        String sessionId,
        String course,
        String section,
        String studentId,
        String studentName,
        String department,
        Instant markedAt,
        String status) {
}

