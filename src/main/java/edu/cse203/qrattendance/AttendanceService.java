package edu.cse203.qrattendance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AttendanceService {
    private final Database database;

    AttendanceService(Database database) {
        this.database = database;
    }

    void ensureStarterAccount() throws SQLException {
        try (Connection connection = database.open();
             PreparedStatement check = connection.prepareStatement("SELECT 1 FROM faculty WHERE username = ?")) {
            check.setString(1, "admin");
            try (ResultSet result = check.executeQuery()) {
                if (result.next()) return;
            }
        }
        PasswordHasher.PasswordData credentials = PasswordHasher.create("admin123");
        try (Connection connection = database.open(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO faculty(faculty_id, username, display_name, password_hash, password_salt, created_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, "admin");
            insert.setString(3, "Faculty Administrator");
            insert.setString(4, credentials.hash());
            insert.setString(5, credentials.salt());
            insert.setString(6, Instant.now().toString());
            insert.executeUpdate();
        }
    }

    boolean authenticate(String username, String password) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT password_hash, password_salt FROM faculty WHERE username = ?")) {
            query.setString(1, username.trim());
            try (ResultSet result = query.executeQuery()) {
                return result.next() && PasswordHasher.verify(password, result.getString(1), result.getString(2));
            }
        }
    }

    void changePassword(String username, String currentPassword, String newPassword) throws SQLException {
        if (newPassword == null || newPassword.length() < 10) {
            throw new IllegalArgumentException("Use a new password with at least 10 characters.");
        }
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT password_hash, password_salt FROM faculty WHERE username = ?")) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next() || !PasswordHasher.verify(currentPassword, result.getString(1), result.getString(2))) {
                    throw new IllegalArgumentException("The current password is not correct.");
                }
            }
        }
        PasswordHasher.PasswordData next = PasswordHasher.create(newPassword);
        try (Connection connection = database.open(); PreparedStatement update = connection.prepareStatement(
                "UPDATE faculty SET password_hash = ?, password_salt = ? WHERE username = ?")) {
            update.setString(1, next.hash());
            update.setString(2, next.salt());
            update.setString(3, username);
            update.executeUpdate();
        }
    }

    List<Student> listStudents() throws SQLException {
        List<Student> rows = new ArrayList<>();
        try (Connection connection = database.open(); Statement query = connection.createStatement();
             ResultSet result = query.executeQuery("SELECT student_id, full_name, department, year_of_study FROM students ORDER BY LOWER(full_name)")) {
            while (result.next()) rows.add(new Student(result.getString(1), result.getString(2), result.getString(3), result.getInt(4)));
        }
        return rows;
    }

    void saveStudent(Student student) throws SQLException {
        validateStudent(student);
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM students WHERE student_id = ?")) {
            query.setString(1, student.studentId());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE students SET full_name = ?, department = ?, year_of_study = ? WHERE student_id = ?")) {
                        update.setString(1, student.fullName());
                        update.setString(2, student.department());
                        update.setInt(3, student.yearOfStudy());
                        update.setString(4, student.studentId());
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO students(student_id, full_name, department, year_of_study, created_at)
                            VALUES(?, ?, ?, ?, ?)
                            """)) {
                        insert.setString(1, student.studentId());
                        insert.setString(2, student.fullName());
                        insert.setString(3, student.department());
                        insert.setInt(4, student.yearOfStudy());
                        insert.setString(5, Instant.now().toString());
                        insert.executeUpdate();
                    }
                }
            }
        }
    }

    void deleteStudent(String studentId) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM students WHERE student_id = ?")) {
            delete.setString(1, studentId);
            delete.executeUpdate();
        }
    }

    List<SessionRecord> listSessions() throws SQLException {
        List<SessionRecord> rows = new ArrayList<>();
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement("""
                SELECT s.session_id, s.course, s.section, s.token, s.starts_at, s.expires_at, s.status, f.display_name
                FROM attendance_sessions s JOIN faculty f ON f.faculty_id = s.faculty_id
                ORDER BY s.starts_at DESC
                """ ); ResultSet result = query.executeQuery()) {
            while (result.next()) rows.add(sessionFrom(result));
        }
        return rows;
    }

    SessionRecord createSession(String course, String section, int durationMinutes, String facultyUsername) throws SQLException {
        course = cleanText(course, "Course name", 120);
        section = cleanText(section, "Section", 60);
        if (durationMinutes < 1 || durationMinutes > 180) throw new IllegalArgumentException("Session duration must be from 1 to 180 minutes.");
        String facultyId;
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT faculty_id FROM faculty WHERE username = ?")) {
            query.setString(1, facultyUsername);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("The signed-in faculty account was not found.");
                facultyId = result.getString(1);
            }
        }
        Instant startsAt = Instant.now();
        Instant expiresAt = startsAt.plusSeconds(durationMinutes * 60L);
        String id = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        try (Connection connection = database.open(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO attendance_sessions(session_id, faculty_id, course, section, token, starts_at, expires_at, status)
                VALUES(?, ?, ?, ?, ?, ?, ?, 'OPEN')
                """)) {
            insert.setString(1, id);
            insert.setString(2, facultyId);
            insert.setString(3, course);
            insert.setString(4, section);
            insert.setString(5, token);
            insert.setString(6, startsAt.toString());
            insert.setString(7, expiresAt.toString());
            insert.executeUpdate();
        }
        return new SessionRecord(id, course, section, token, startsAt, expiresAt, "OPEN", "Faculty Administrator");
    }

    void closeSession(String sessionId) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement update = connection.prepareStatement(
                "UPDATE attendance_sessions SET status = 'CLOSED' WHERE session_id = ?")) {
            update.setString(1, sessionId);
            update.executeUpdate();
        }
    }

    SessionRecord findSessionByToken(String token) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement("""
                SELECT s.session_id, s.course, s.section, s.token, s.starts_at, s.expires_at, s.status, f.display_name
                FROM attendance_sessions s JOIN faculty f ON f.faculty_id = s.faculty_id
                WHERE s.token = ?
                """)) {
            query.setString(1, token);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? sessionFrom(result) : null;
            }
        }
    }

    void markAttendance(String token, String studentId) throws SQLException {
        SessionRecord session = findSessionByToken(token);
        if (session == null) throw new IllegalArgumentException("This attendance link is not valid.");
        if (!session.isOpenNow()) throw new IllegalArgumentException("This attendance session is closed or has expired.");
        String normalizedId = studentId == null ? "" : studentId.trim();
        if (!normalizedId.matches("[A-Za-z0-9._/-]{2,30}")) {
            throw new IllegalArgumentException("Enter a valid student ID.");
        }
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM students WHERE student_id = ?")) {
            query.setString(1, normalizedId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Student ID not found. Ask faculty to add you to the roster.");
            }
        }
        try (Connection connection = database.open(); PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO attendance(attendance_id, session_id, student_id, marked_at, status)
                VALUES(?, ?, ?, ?, 'PRESENT')
                """)) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, session.sessionId());
            insert.setString(3, normalizedId);
            insert.setString(4, Instant.now().toString());
            insert.executeUpdate();
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (ex.getErrorCode() == 1062 || message.contains("unique") || message.contains("duplicate")) {
                throw new IllegalArgumentException("Attendance has already been recorded for this student.");
            }
            throw ex;
        }
    }

    List<AttendanceRecord> listAttendance(String sessionId) throws SQLException {
        List<AttendanceRecord> rows = new ArrayList<>();
        String sql = """
                SELECT a.attendance_id, a.session_id, s.course, s.section, a.student_id, st.full_name,
                       st.department, a.marked_at, a.status
                FROM attendance a
                JOIN attendance_sessions s ON s.session_id = a.session_id
                JOIN students st ON st.student_id = a.student_id
                """ + (sessionId == null || sessionId.isBlank() ? "" : " WHERE a.session_id = ?")
                + " ORDER BY a.marked_at DESC";
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(sql)) {
            if (sessionId != null && !sessionId.isBlank()) query.setString(1, sessionId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) rows.add(new AttendanceRecord(result.getString(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getString(5), result.getString(6), result.getString(7),
                        Instant.parse(result.getString(8)), result.getString(9)));
            }
        }
        return rows;
    }

    int countStudents() throws SQLException { return count("SELECT COUNT(*) FROM students"); }

    int countAttendanceToday() throws SQLException {
        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM attendance WHERE marked_at >= ? AND marked_at < ?")) {
            query.setString(1, start.toString());
            query.setString(2, end.toString());
            try (ResultSet result = query.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    int countActiveSessions() throws SQLException {
        Instant now = Instant.now();
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement("""
                SELECT COUNT(*) FROM attendance_sessions
                WHERE status = 'OPEN' AND starts_at <= ? AND expires_at > ?
                """)) {
            query.setString(1, now.toString());
            query.setString(2, now.toString());
            try (ResultSet result = query.executeQuery()) { return result.next() ? result.getInt(1) : 0; }
        }
    }

    Map<String, Integer> attendanceBySession() throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = database.open(); Statement query = connection.createStatement();
             ResultSet result = query.executeQuery("SELECT session_id, COUNT(*) FROM attendance GROUP BY session_id")) {
            while (result.next()) counts.put(result.getString(1), result.getInt(2));
        }
        return counts;
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = database.open(); Statement query = connection.createStatement(); ResultSet result = query.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static SessionRecord sessionFrom(ResultSet result) throws SQLException {
        return new SessionRecord(result.getString(1), result.getString(2), result.getString(3), result.getString(4),
                Instant.parse(result.getString(5)), Instant.parse(result.getString(6)), result.getString(7), result.getString(8));
    }

    private static void validateStudent(Student student) {
        if (student == null || !student.studentId().trim().matches("[A-Za-z0-9._/-]{2,30}")) {
            throw new IllegalArgumentException("Student ID must be 2–30 letters or digits; . _ / - are also allowed.");
        }
        cleanText(student.fullName(), "Student name", 100);
        cleanText(student.department(), "Department", 80);
        if (student.yearOfStudy() < 1 || student.yearOfStudy() > 8) throw new IllegalArgumentException("Year of study must be from 1 to 8.");
    }

    private static String cleanText(String text, String label, int maxLength) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " is required and must be no longer than " + maxLength + " characters.");
        }
        return value;
    }
}

