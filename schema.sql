-- The application creates this schema automatically on first launch.
-- Kept here for review and for database coursework documentation.
CREATE TABLE faculty (
    faculty_id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    created_at VARCHAR(40) NOT NULL
);

CREATE TABLE students (
    student_id VARCHAR(30) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    department VARCHAR(80) NOT NULL,
    year_of_study INTEGER NOT NULL,
    created_at VARCHAR(40) NOT NULL
);

CREATE TABLE attendance_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    faculty_id VARCHAR(36) NOT NULL REFERENCES faculty(faculty_id),
    course VARCHAR(120) NOT NULL,
    section VARCHAR(60) NOT NULL,
    token VARCHAR(36) NOT NULL UNIQUE,
    starts_at VARCHAR(40) NOT NULL,
    expires_at VARCHAR(40) NOT NULL,
    status VARCHAR(12) NOT NULL
);

CREATE TABLE attendance (
    attendance_id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL REFERENCES attendance_sessions(session_id),
    student_id VARCHAR(30) NOT NULL REFERENCES students(student_id),
    marked_at VARCHAR(40) NOT NULL,
    status VARCHAR(12) NOT NULL,
    UNIQUE (session_id, student_id)
);

CREATE INDEX idx_sessions_started ON attendance_sessions(starts_at);
CREATE INDEX idx_attendance_session ON attendance(session_id, marked_at);

