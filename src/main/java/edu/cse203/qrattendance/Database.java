package edu.cse203.qrattendance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

final class Database {
    private final String url;
    private final String user;
    private final String password;

    Database() {
        String configuredUrl = System.getenv("QR_ATTENDANCE_JDBC_URL");
        this.url = configuredUrl == null || configuredUrl.isBlank()
                ? "jdbc:sqlite:data/attendance.db" : configuredUrl.trim();
        this.user = System.getenv("QR_ATTENDANCE_DB_USER");
        this.password = System.getenv("QR_ATTENDANCE_DB_PASSWORD");
    }

    void initialize() throws SQLException {
        if (!url.startsWith("jdbc:sqlite:") && !url.startsWith("jdbc:mysql:")) {
            throw new SQLException("Use the default SQLite database or configure a MySQL JDBC URL.");
        }
        createSqliteDirectory();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS faculty (
                        faculty_id VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(80) NOT NULL UNIQUE,
                        display_name VARCHAR(120) NOT NULL,
                        password_hash VARCHAR(128) NOT NULL,
                        password_salt VARCHAR(64) NOT NULL,
                        created_at VARCHAR(40) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS students (
                        student_id VARCHAR(30) PRIMARY KEY,
                        full_name VARCHAR(100) NOT NULL,
                        department VARCHAR(80) NOT NULL,
                        year_of_study INTEGER NOT NULL,
                        created_at VARCHAR(40) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS attendance_sessions (
                        session_id VARCHAR(36) PRIMARY KEY,
                        faculty_id VARCHAR(36) NOT NULL REFERENCES faculty(faculty_id),
                        course VARCHAR(120) NOT NULL,
                        section VARCHAR(60) NOT NULL,
                        token VARCHAR(36) NOT NULL UNIQUE,
                        starts_at VARCHAR(40) NOT NULL,
                        expires_at VARCHAR(40) NOT NULL,
                        status VARCHAR(12) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS attendance (
                        attendance_id VARCHAR(36) PRIMARY KEY,
                        session_id VARCHAR(36) NOT NULL REFERENCES attendance_sessions(session_id),
                        student_id VARCHAR(30) NOT NULL REFERENCES students(student_id),
                        marked_at VARCHAR(40) NOT NULL,
                        status VARCHAR(12) NOT NULL,
                        UNIQUE (session_id, student_id)
                    )
                    """);
            createIndex(connection, "idx_sessions_started", "attendance_sessions", "starts_at");
            createIndex(connection, "idx_attendance_session", "attendance", "session_id, marked_at");
        }
    }

    private void createIndex(Connection connection, String name, String table, String columns) throws SQLException {
        String ifNotExists = url.startsWith("jdbc:sqlite:") ? "IF NOT EXISTS " : "";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX " + ifNotExists + name + " ON " + table + "(" + columns + ")");
        } catch (SQLException ex) {
            if (url.startsWith("jdbc:mysql:") && ex.getErrorCode() == 1061) return;
            throw ex;
        }
    }

    Connection open() throws SQLException {
        Connection connection;
        if (user == null || user.isBlank()) {
            connection = DriverManager.getConnection(url);
        } else {
            connection = DriverManager.getConnection(url, user, password == null ? "" : password);
        }
        if (url.startsWith("jdbc:sqlite:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    private void createSqliteDirectory() throws SQLException {
        if (!url.startsWith("jdbc:sqlite:") || url.equals("jdbc:sqlite::memory:")) return;
        String file = url.substring("jdbc:sqlite:".length());
        if (file.startsWith("file:")) return;
        try {
            Path path = Path.of(file).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (Exception ex) {
            throw new SQLException("Could not prepare the local database folder.", ex);
        }
    }
}

