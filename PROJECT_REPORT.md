# QR Attendance System — Mini Project Report

**Course:** OOP using Java (CSE203)  
**Project type:** Java mini project

## Abstract

The QR Attendance System records class attendance through a time-limited QR check-in. Faculty maintain the student roster and create sessions in a JavaFX desktop application. Each session gets a unique QR link. A registered student scans it with a phone on the same local network and submits their student ID. The application validates the student and session, prevents a second check-in for the same session, and stores the result through JDBC.

## Problem statement

Manual attendance takes class time, creates extra record keeping, and can lead to incorrect or duplicate entries. The project provides a digital workflow for recording and retrieving attendance.

## Objectives

- Automate classroom attendance using QR codes.
- Provide a JavaFX interface for faculty.
- Maintain students, faculty sessions, and attendance records in a relational database.
- Demonstrate object-oriented Java, collections, validation, exception handling, and JDBC.
- Provide an attendance view and CSV report export.

## Scope and users

Faculty sign in, add students, open or close attendance sessions, and review or export records. Students use a phone browser to submit attendance during a valid session. A student must already be registered by faculty. The included implementation is designed for a classroom demonstration on a trusted local network.

## Architecture

| Layer | Implementation |
|---|---|
| Faculty interface | JavaFX controls and CSS |
| Application logic | Core Java services and model records |
| QR generation | ZXing QR encoder |
| Student check-in | Java `HttpServer` serving a small phone-friendly web form |
| Persistence | JDBC with SQLite by default; MySQL can be configured |
| Reports | Java collections for grouping/summary data and CSV export |

## Main modules

1. **Faculty authentication:** verifies salted PBKDF2 password hashes and permits password changes.
2. **Student management:** adds, edits, lists, and removes roster entries where attendance history permits.
3. **Session management:** creates a random, session-specific QR token and an expiry time; faculty can close a session early.
4. **QR check-in:** phone form submits a student ID to the embedded server.
5. **Attendance validation:** checks token, time window, roster membership, and duplicate check-ins before inserting a record.
6. **Attendance reports:** filters records by session and exports the selected rows as CSV.

## Attendance workflow

1. Faculty sign in and register the class roster.
2. Faculty enter the course and section, then create an attendance session.
3. The app generates a unique QR code that links to the local check-in page.
4. A student scans the code on the same Wi-Fi network and enters their registered student ID.
5. The application confirms that the session is open, the student exists, and the student has not already checked in.
6. A successful check-in is stored with its timestamp and appears in the faculty attendance table.
7. Faculty export a CSV report if needed.

## Database design

| Table | Purpose | Main fields |
|---|---|---|
| `faculty` | Faculty accounts | `faculty_id`, `username`, password hash and salt |
| `students` | Registered student roster | `student_id`, name, department, year |
| `attendance_sessions` | QR session lifecycle | `session_id`, faculty, course, section, token, start, expiry, status |
| `attendance` | Check-ins | `attendance_id`, session, student, timestamp, status |

The `(session_id, student_id)` unique constraint prevents a student from being recorded twice in one session. Foreign keys keep attendance attached to a valid student and session.

## Object-oriented and Java concepts

- Model records represent `Student`, `SessionRecord`, and `AttendanceRecord`.
- Services separate authentication and attendance rules from JavaFX event handlers.
- JDBC uses prepared statements for database reads and writes.
- Java collections hold result rows and group attendance counts.
- Input validation, exception handling, and PBKDF2 password hashing are applied where data enters the system.
- QR generation and web serving are encapsulated in dedicated classes.

## Setup and execution

Install JDK 21+ and Maven 3.9+. On Windows, run `run.bat`, or use `mvn javafx:run`. On first start the application creates its database and a starter faculty account: `admin` / `admin123`. Change the password after signing in. The full setup and optional MySQL configuration are in [README.md](README.md).

## Evaluation alignment

The source deck's evaluation categories map to the system design, Java and collections implementation, JavaFX/JDBC application, Git contribution history, this report, and project presentation. Git history should identify each team member's work.

## Future enhancements

- Add role-based student and administrator accounts.
- Add attendance percentage calculations and low-attendance alerts.
- Add scheduled report exports and more secure campus deployment with HTTPS.
- Add stronger identity validation for larger deployments.

