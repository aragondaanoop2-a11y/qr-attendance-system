# QR Attendance System

A Java mini project for CSE203. Faculty use a JavaFX desktop app to manage students, open time-limited attendance sessions, display QR codes, and export attendance records. Students scan the QR code with a phone on the same Wi-Fi network and submit their pre-registered student ID.

## Web demo

The static Faculty and Student portal demo is in [`docs/`](docs/). It can be hosted with GitHub Pages. The demo stores data in the current browser only; it does not synchronize attendance between a student's phone and the faculty computer. Use the Java desktop application below for the full local-network attendance workflow. The web demo uses fictitious sample students; do not enter real student information.

## What is included

- Faculty sign-in with a salted PBKDF2 password hash
- Student registration and maintenance
- Session creation with a unique, expiring QR link
- A small embedded HTTP server for mobile check-in
- Validation for registered students, open sessions, expiry, and duplicate submissions
- JDBC persistence using a local SQLite database by default, with optional MySQL support
- Attendance table, workspace counts, and CSV export
- JavaFX desktop interface and QR code PNG export

## Requirements

- JDK 21 or newer
- Apache Maven 3.9 or newer
- A phone and computer connected to the same local Wi-Fi for phone scanning

The first Maven run downloads JavaFX and library dependencies from Maven Central. The app itself runs locally and stores data in `data/attendance.db`.

## Run on Windows

1. Install JDK 21+ and Apache Maven, and make sure `java` and `mvn` work in Command Prompt.
2. Double-click `run.bat`, or open a terminal in this folder and run `mvn javafx:run`.
3. Sign in with the starter account shown on the login screen: `admin` / `admin123`.
4. Open **Students** and register the class roster before starting a session.
5. Open **Sessions**, enter the course and section, choose a duration, and create a session.
6. Show the QR code. Students scan it, enter their registered student ID, and submit.
7. Use **Attendance** to review and export records.

Change the starter password with the button at the top right after the first sign-in.

## Phone scanning setup

The desktop app starts its check-in server on port `8765` (override with `QR_ATTENDANCE_PORT`). It detects a local IPv4 address and puts that address in the session QR code. The phone must be on the same Wi-Fi network, and the computer firewall must allow inbound connections to Java on port 8765. If the detected address is wrong, edit **Phone access URL** on the Sessions page before creating or sharing the QR code. Enter a URL such as `http://192.168.1.20:8765`.

The session QR contains an unguessable session token and expires at the time shown. Only students already present in the roster can check in. A second submission for the same student and session is rejected.

## Data and database

By default, the app creates `data/attendance.db` beside the project. The schema is also documented in `schema.sql`. Back up this file to preserve records.

The default SQLite database needs no server or separate setup. For MySQL, create an empty database and a database user, then set `QR_ATTENDANCE_JDBC_URL` (for example `jdbc:mysql://localhost:3306/qr_attendance`), `QR_ATTENDANCE_DB_USER`, and `QR_ATTENDANCE_DB_PASSWORD` before launching. The app creates the tables automatically; the account needs permission to create tables and indexes. Database tables are initialized automatically on launch.

## Project layout

```text
src/main/java/edu/cse203/qrattendance/
  MainApp.java                 JavaFX screens and navigation
  Database.java                JDBC connection and schema setup
  AttendanceService.java       Authentication, validation, records, reports
  LocalAttendanceServer.java   Phone check-in HTTP endpoints
  QrCodeService.java           QR creation and PNG export
  PasswordHasher.java          PBKDF2 password hashing
  Student.java                 Student model
  SessionRecord.java           Attendance session model
  AttendanceRecord.java        Attendance row model
src/main/resources/edu/cse203/qrattendance/app.css
```

## Evaluation mapping

| Evaluation area | Project evidence |
|---|---|
| Problem identification and design | This README, data model, and faculty/student workflow |
| Java and data structures | Model classes, collections, input validation, grouping and attendance percentage calculation |
| Application and database | JavaFX, QR creation, mobile HTTP check-in, JDBC, SQLite schema |
| Teamwork and Git | Use Git commits/branches to record each member's contribution |
| Documentation and viva | This setup guide and source comments |

This is a classroom demonstration project. The same-network HTTP check-in is intended for a local demo; use HTTPS and institution-managed identity before exposing it outside a trusted campus network.

