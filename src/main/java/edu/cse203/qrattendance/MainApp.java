package edu.cse203.qrattendance;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.net.SocketException;

public final class MainApp extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a").withZone(ZoneId.systemDefault());

    private final Database database = new Database();
    private final AttendanceService attendanceService = new AttendanceService(database);
    private final QrCodeService qrCodeService = new QrCodeService();
    private Stage stage;
    private LocalAttendanceServer attendanceServer;
    private Timeline refreshTimer;
    private int serverPort = LocalAttendanceServer.DEFAULT_PORT;
    private String currentUsername;
    private BorderPane shell;
    private VBox contentArea;
    private Label pageTitle;
    private Label pageSubtitle;
    private Label serverStatus;
    private String currentPage = "Overview";
    private TableView<Student> studentTable;
    private TextField studentIdField;
    private TextField studentNameField;
    private TextField departmentField;
    private ComboBox<Integer> yearField;
    private TableView<SessionRecord> sessionTable;
    private TextField courseField;
    private TextField sectionField;
    private ComboBox<Integer> durationField;
    private TextField phoneUrlField;
    private ImageView qrImage;
    private Label qrInfo;
    private SessionRecord selectedSession;
    private TableView<AttendanceRecord> attendanceTable;
    private ComboBox<SessionChoice> attendanceFilter;
    private Label overviewStudentsValue;
    private Label overviewTodayValue;
    private Label overviewActiveValue;

    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;
        stage.setTitle("QR Attendance System · CSE203");
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        database.initialize();
        attendanceService.ensureStarterAccount();
        attendanceServer = new LocalAttendanceServer(attendanceService);
        serverPort = attendanceServer.start();
        serverStatus = new Label("Check-in server · port " + serverPort);
        stage.setOnCloseRequest(event -> {
            if (refreshTimer != null) refreshTimer.stop();
            if (attendanceServer != null) attendanceServer.close();
        });
        showLogin();
    }

    private void showLogin() {
        Label brand = new Label("QR / ATTENDANCE");
        brand.getStyleClass().add("brand-mark");
        Label eyebrow = new Label("CSE203 · JAVA MINI PROJECT");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Attendance,\nwithout the paper.");
        title.getStyleClass().add("login-title");
        Label description = new Label("Manage class rosters, open QR check-in sessions, and keep attendance records in one place.");
        description.getStyleClass().add("login-description");
        VBox story = new VBox(24, brand, eyebrow, title, description, serverStatus);
        story.getStyleClass().add("login-story");
        story.setPadding(new Insets(52));
        story.setAlignment(Pos.CENTER_LEFT);
        story.setPrefWidth(560);

        Label formEyebrow = new Label("FACULTY PORTAL");
        formEyebrow.getStyleClass().add("eyebrow");
        Label formTitle = new Label("Sign in");
        formTitle.getStyleClass().add("section-title");
        TextField username = new TextField("admin");
        username.setPromptText("Username");
        username.getStyleClass().add("large-input");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.getStyleClass().add("large-input");
        Label hint = new Label("Starter account: admin  /  admin123");
        hint.getStyleClass().add("muted");
        Label error = new Label();
        error.getStyleClass().add("error-text");
        Button signIn = new Button("Sign in to dashboard");
        signIn.getStyleClass().addAll("button", "primary", "wide");
        Runnable attempt = () -> {
            try {
                if (attendanceService.authenticate(username.getText(), password.getText())) {
                    currentUsername = username.getText().trim();
                    showApplication();
                } else {
                    error.setText("The username or password is incorrect.");
                }
            } catch (Exception ex) {
                error.setText("Could not connect to the attendance database: " + safeMessage(ex));
            }
        };
        signIn.setOnAction(event -> attempt.run());
        password.setOnAction(event -> attempt.run());
        VBox form = new VBox(14, formEyebrow, formTitle, fieldLabel("Username", username), fieldLabel("Password", password), hint, error, signIn);
        form.getStyleClass().add("login-card");
        form.setMaxWidth(420);
        form.setPadding(new Insets(46));
        StackPane right = new StackPane(form);
        right.setPadding(new Insets(36));
        right.setPrefWidth(600);
        HBox layout = new HBox(story, right);
        layout.getStyleClass().add("login-page");
        HBox.setHgrow(right, Priority.ALWAYS);
        Scene scene = new Scene(layout, 1180, 760);
        installCss(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        username.requestFocus();
    }

    private void showApplication() {
        shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setLeft(buildSidebar());
        contentArea = new VBox(18);
        contentArea.getStyleClass().add("page-content");
        contentArea.setPadding(new Insets(30, 34, 32, 34));
        pageTitle = new Label();
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle = new Label();
        pageSubtitle.getStyleClass().add("page-subtitle");
        Button account = new Button("Change password");
        account.getStyleClass().addAll("button", "quiet");
        account.setOnAction(event -> changePasswordDialog());
        VBox heading = new VBox(5, pageTitle, pageSubtitle);
        HBox top = new HBox(heading, spacer(), account);
        top.setAlignment(Pos.CENTER_LEFT);
        shell.setTop(top);
        BorderPane.setMargin(top, new Insets(28, 34, 0, 34));
        shell.setCenter(contentArea);
        Scene scene = new Scene(shell, 1280, 820);
        installCss(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        showPage("Overview");
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(6), event -> refreshVisiblePage()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private Node buildSidebar() {
        Label logo = new Label("QR");
        logo.getStyleClass().add("logo-tile");
        Label brand = new Label("Attendance\nSystem");
        brand.getStyleClass().add("sidebar-brand");
        HBox mark = new HBox(12, logo, brand);
        mark.setAlignment(Pos.CENTER_LEFT);
        Label workspace = new Label("WORKSPACE");
        workspace.getStyleClass().add("sidebar-label");
        VBox nav = new VBox(7);
        for (String page : List.of("Overview", "Students", "Sessions", "Attendance")) {
            Button button = new Button(page);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> showPage(page));
            button.setUserData(page);
            nav.getChildren().add(button);
        }
        Label signedIn = new Label(currentUsername);
        signedIn.getStyleClass().add("sidebar-user");
        Label role = new Label("Faculty account");
        role.getStyleClass().add("sidebar-role");
        VBox user = new VBox(3, signedIn, role);
        HBox userCard = new HBox(user);
        userCard.getStyleClass().add("sidebar-user-card");
        VBox sidebar = new VBox(27, mark, workspace, nav, spacer(), userCard);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(26, 18, 20, 18));
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        VBox.setVgrow(nav, Priority.NEVER);
        return sidebar;
    }

    private void showPage(String page) {
        currentPage = page;
        for (Node node : ((VBox) shell.getLeft()).getChildren()) {
            if (node instanceof VBox group) {
                for (Node item : group.getChildren()) {
                    if (item instanceof Button button && button.getUserData() instanceof String name) {
                        button.getStyleClass().remove("selected");
                        if (name.equals(page)) button.getStyleClass().add("selected");
                    }
                }
            }
        }
        contentArea.getChildren().clear();
        switch (page) {
            case "Students" -> showStudentsPage();
            case "Sessions" -> showSessionsPage();
            case "Attendance" -> showAttendancePage();
            default -> showOverviewPage();
        }
    }

    private void showOverviewPage() {
        setHeading("Overview", "Live view of your attendance workspace.");
        overviewStudentsValue = new Label("—");
        Label totalValue = overviewStudentsValue;
        Label totalNote = new Label("Registered in the roster");
        overviewTodayValue = new Label("—");
        Label todayValue = overviewTodayValue;
        Label todayNote = new Label("Across all sessions today");
        overviewActiveValue = new Label("—");
        Label activeValue = overviewActiveValue;
        Label activeNote = new Label("Currently accepting check-ins");
        HBox stats = new HBox(14,
                statCard("STUDENTS", totalValue, totalNote, "01"),
                statCard("MARKED TODAY", todayValue, todayNote, "02"),
                statCard("OPEN SESSIONS", activeValue, activeNote, "03"));
        stats.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        Label section = new Label("Getting started");
        section.getStyleClass().add("section-title");
        Label explain = new Label("Set up the roster, create a session, and let students check in from their phones.");
        explain.getStyleClass().add("muted");
        VBox intro = new VBox(8, section, explain);
        Button addStudents = new Button("Manage students  →");
        addStudents.getStyleClass().addAll("button", "quiet");
        addStudents.setOnAction(event -> showPage("Students"));
        Button startSession = new Button("Create attendance session  →");
        startSession.getStyleClass().addAll("button", "primary");
        startSession.setOnAction(event -> showPage("Sessions"));
        VBox steps = new VBox(
                step("1", "Add your students", "Register each student's ID, name, department, and year."),
                step("2", "Create a QR session", "Choose the class and how long the session stays open."),
                step("3", "Review the attendance", "Track check-ins as they arrive and export a CSV report."));
        VBox workflow = panel(new HBox(intro, spacer(), addStudents), steps);
        workflow.setSpacing(20);
        HBox shortcuts = new HBox(12, startSession);
        Label note = new Label("The phone check-in page is served on this computer's local network. Students must be on the same Wi-Fi.");
        note.getStyleClass().add("callout");
        contentArea.getChildren().addAll(stats, workflow, shortcuts, note);
        try {
            totalValue.setText(String.valueOf(attendanceService.countStudents()));
            todayValue.setText(String.valueOf(attendanceService.countAttendanceToday()));
            activeValue.setText(String.valueOf(attendanceService.countActiveSessions()));
        } catch (Exception ex) {
            showError("Could not load workspace summary", ex);
        }
    }

    private Node step(String number, String title, String description) {
        Label index = new Label(number);
        index.getStyleClass().add("step-number");
        Label name = new Label(title);
        name.getStyleClass().add("step-title");
        Label detail = new Label(description);
        detail.getStyleClass().add("muted");
        VBox text = new VBox(5, name, detail);
        HBox row = new HBox(15, index, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node statCard(String title, Label value, Label note, String number) {
        Label heading = new Label(title);
        heading.getStyleClass().add("stat-label");
        Label accent = new Label(number);
        accent.getStyleClass().add("stat-index");
        HBox top = new HBox(heading, spacer(), accent);
        value.getStyleClass().add("stat-value");
        note.getStyleClass().add("stat-note");
        VBox card = new VBox(15, top, value, note);
        card.getStyleClass().add("panel");
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(20));
        card.setMinWidth(180);
        return card;
    }

    private void showStudentsPage() {
        setHeading("Students", "Maintain the roster used to validate QR check-ins.");
        studentTable = new TableView<>();
        studentTable.setPlaceholder(new Label("No students yet. Add the class roster to begin."));
        studentTable.getColumns().setAll(
                column("Student ID", Student::studentId, 160),
                column("Full name", Student::fullName, 245),
                column("Department", Student::department, 200),
                column("Year", student -> "Year " + student.yearOfStudy(), 100));
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        studentTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) populateStudentForm(selected);
        });
        Label formTitle = new Label("Add a student");
        formTitle.getStyleClass().add("section-title");
        studentIdField = new TextField();
        studentIdField.setPromptText("e.g. CSE203-014");
        studentNameField = new TextField();
        studentNameField.setPromptText("Student full name");
        departmentField = new TextField();
        departmentField.setPromptText("Department");
        yearField = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 7, 8));
        yearField.setValue(1);
        yearField.setMaxWidth(Double.MAX_VALUE);
        Button save = new Button("Save student");
        save.getStyleClass().addAll("button", "primary", "wide");
        save.setOnAction(event -> saveStudent());
        Button clear = new Button("Clear form");
        clear.getStyleClass().addAll("button", "quiet", "wide");
        clear.setOnAction(event -> clearStudentForm());
        Button delete = new Button("Remove selected");
        delete.getStyleClass().addAll("button", "danger", "wide");
        delete.setOnAction(event -> deleteSelectedStudent());
        VBox form = new VBox(13, formTitle, fieldLabel("Student ID", studentIdField), fieldLabel("Full name", studentNameField),
                fieldLabel("Department", departmentField), fieldLabel("Year of study", yearField), save, clear, delete);
        form.getStyleClass().add("panel");
        form.setPadding(new Insets(20));
        form.setPrefWidth(325);
        HBox body = new HBox(16, form, studentTable);
        HBox.setHgrow(studentTable, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        contentArea.getChildren().add(body);
        refreshStudents();
    }

    private void showSessionsPage() {
        setHeading("Sessions", "Create a QR code students can use to mark attendance.");
        courseField = new TextField();
        courseField.setPromptText("Course name");
        sectionField = new TextField();
        sectionField.setPromptText("Section or class group");
        durationField = new ComboBox<>(FXCollections.observableArrayList(5, 10, 15, 30, 45, 60, 90, 120, 180));
        durationField.setValue(15);
        durationField.setMaxWidth(Double.MAX_VALUE);
        phoneUrlField = new TextField(detectedBaseUrl());
        phoneUrlField.setPromptText("http://192.168.1.20:8765");
        phoneUrlField.textProperty().addListener((observable, oldValue, newValue) -> updateQrDisplay());
        Button create = new Button("Create session & QR");
        create.getStyleClass().addAll("button", "primary", "wide");
        create.setOnAction(event -> createSession());
        VBox form = new VBox(13,
                sectionTitle("New session"),
                fieldLabel("Course", courseField),
                fieldLabel("Section", sectionField),
                fieldLabel("QR stays open (minutes)", durationField),
                fieldLabel("Phone access URL", phoneUrlField),
                create,
                new Label("Use the computer's Wi-Fi address. Students scan from the same network.")
        );
        form.getChildren().getLast().getStyleClass().add("small-note");
        form.getStyleClass().add("panel");
        form.setPadding(new Insets(20));
        form.setPrefWidth(375);

        qrImage = new ImageView();
        qrImage.setFitWidth(224);
        qrImage.setFitHeight(224);
        qrImage.setPreserveRatio(true);
        StackPane qrFrame = new StackPane(qrImage);
        qrFrame.getStyleClass().add("qr-frame");
        qrFrame.setMinSize(248, 248);
        qrFrame.setMaxSize(248, 248);
        qrInfo = new Label("Create a session to display its QR code.");
        qrInfo.getStyleClass().add("muted");
        qrInfo.setWrapText(true);
        qrInfo.setMaxWidth(280);
        Button copy = new Button("Copy check-in link");
        copy.getStyleClass().addAll("button", "quiet");
        copy.setOnAction(event -> copySessionLink());
        Button saveQr = new Button("Save QR image");
        saveQr.getStyleClass().addAll("button", "quiet");
        saveQr.setOnAction(event -> saveQrImage());
        VBox qrPanel = new VBox(13, sectionTitle("Student check-in"), qrFrame, qrInfo, new HBox(8, copy, saveQr));
        qrPanel.getStyleClass().add("panel");
        qrPanel.setPadding(new Insets(20));
        qrPanel.setAlignment(Pos.TOP_CENTER);
        qrPanel.setMinWidth(330);

        sessionTable = new TableView<>();
        sessionTable.setPlaceholder(new Label("No sessions yet. Create your first session above."));
        sessionTable.getColumns().setAll(
                column("Course", SessionRecord::course, 190),
                column("Section", SessionRecord::section, 110),
                column("Started", record -> TIME.format(record.startsAt()), 180),
                column("Expires", record -> TIME.format(record.expiresAt()), 180),
                column("Status", SessionRecord::displayStatus, 105));
        sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectedSession = selected;
                updateQrDisplay();
            }
        });
        Button close = new Button("Close selected session");
        close.getStyleClass().addAll("button", "danger");
        close.setOnAction(event -> closeSelectedSession());
        Label recordsTitle = sectionTitle("Recent sessions");
        HBox tableHeader = new HBox(recordsTitle, spacer(), close);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        VBox list = new VBox(12, tableHeader, sessionTable);
        list.getStyleClass().add("panel");
        list.setPadding(new Insets(18));
        VBox.setVgrow(sessionTable, Priority.ALWAYS);
        VBox.setVgrow(list, Priority.ALWAYS);
        HBox top = new HBox(16, form, qrPanel);
        contentArea.getChildren().addAll(top, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        refreshSessions();
    }

    private void showAttendancePage() {
        setHeading("Attendance", "Review check-ins and export a class report.");
        attendanceTable = new TableView<>();
        attendanceTable.setPlaceholder(new Label("No attendance records for this selection."));
        attendanceTable.getColumns().setAll(
                column("Student ID", AttendanceRecord::studentId, 135),
                column("Student", AttendanceRecord::studentName, 205),
                column("Department", AttendanceRecord::department, 165),
                column("Course", AttendanceRecord::course, 180),
                column("Section", AttendanceRecord::section, 100),
                column("Marked at", record -> TIME.format(record.markedAt()), 175),
                column("Status", AttendanceRecord::status, 95));
        attendanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        attendanceFilter = new ComboBox<>();
        attendanceFilter.valueProperty().addListener((observable, oldValue, selected) -> refreshAttendance());
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().addAll("button", "quiet");
        refresh.setOnAction(event -> refreshAttendance());
        Button export = new Button("Export CSV");
        export.getStyleClass().addAll("button", "primary");
        export.setOnAction(event -> exportAttendance());
        Label filterLabel = new Label("Session");
        filterLabel.getStyleClass().add("field-label");
        HBox controls = new HBox(10, filterLabel, attendanceFilter, spacer(), refresh, export);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox tablePanel = new VBox(14, controls, attendanceTable);
        tablePanel.getStyleClass().add("panel");
        tablePanel.setPadding(new Insets(20));
        VBox.setVgrow(attendanceTable, Priority.ALWAYS);
        VBox.setVgrow(tablePanel, Priority.ALWAYS);
        contentArea.getChildren().add(tablePanel);
        refreshSessionFilter();
        refreshAttendance();
    }

    private void setHeading(String title, String subtitle) {
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
    }

    private void saveStudent() {
        try {
            Student student = new Student(studentIdField.getText().trim(), studentNameField.getText().trim(),
                    departmentField.getText().trim(), yearField.getValue() == null ? 0 : yearField.getValue());
            attendanceService.saveStudent(student);
            refreshStudents();
            clearStudentForm();
            showInfo("Student saved", "The student roster has been updated.");
        } catch (Exception ex) {
            showError("Could not save student", ex);
        }
    }

    private void deleteSelectedStudent() {
        Student selected = studentTable == null ? null : studentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Select a student", "Choose a row from the roster first.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove " + selected.fullName() + " from the roster? Students with attendance history cannot be removed.", ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle("Remove student");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            attendanceService.deleteStudent(selected.studentId());
            refreshStudents();
            clearStudentForm();
        } catch (Exception ex) {
            showError("Could not remove student", new IllegalStateException("This student has attendance history and must remain in the roster.", ex));
        }
    }

    private void populateStudentForm(Student student) {
        studentIdField.setText(student.studentId());
        studentIdField.setDisable(true);
        studentNameField.setText(student.fullName());
        departmentField.setText(student.department());
        yearField.setValue(student.yearOfStudy());
    }

    private void clearStudentForm() {
        studentIdField.clear();
        studentIdField.setDisable(false);
        studentNameField.clear();
        departmentField.clear();
        yearField.setValue(1);
        studentTable.getSelectionModel().clearSelection();
    }

    private void refreshStudents() {
        if (studentTable == null) return;
        try {
            studentTable.setItems(FXCollections.observableArrayList(attendanceService.listStudents()));
        } catch (Exception ex) {
            showError("Could not load students", ex);
        }
    }

    private void createSession() {
        try {
            String base = normalizeBaseUrl(phoneUrlField.getText());
            if (!base.matches("https?://[A-Za-z0-9._:-]+")) throw new IllegalArgumentException("Enter a valid phone access URL, such as http://192.168.1.20:8765.");
            phoneUrlField.setText(base);
            selectedSession = attendanceService.createSession(courseField.getText(), sectionField.getText(),
                    durationField.getValue() == null ? 15 : durationField.getValue(), currentUsername);
            refreshSessions();
            sessionTable.getSelectionModel().select(0);
            updateQrDisplay();
        } catch (Exception ex) {
            showError("Could not create session", ex);
        }
    }

    private void closeSelectedSession() {
        SessionRecord selected = sessionTable == null ? null : sessionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Select a session", "Choose a session from the list first.");
            return;
        }
        try {
            attendanceService.closeSession(selected.sessionId());
            refreshSessions();
            selectedSession = selected;
            updateQrDisplay();
        } catch (Exception ex) {
            showError("Could not close session", ex);
        }
    }

    private void refreshSessions() {
        if (sessionTable == null) return;
        try {
            List<SessionRecord> rows = attendanceService.listSessions();
            sessionTable.setItems(FXCollections.observableArrayList(rows));
            if (selectedSession != null) {
                rows.stream().filter(row -> row.sessionId().equals(selectedSession.sessionId())).findFirst().ifPresent(row -> {
                    selectedSession = row;
                    sessionTable.getSelectionModel().select(row);
                });
            }
        } catch (Exception ex) {
            showError("Could not load sessions", ex);
        }
    }

    private void updateQrDisplay() {
        if (qrImage == null || selectedSession == null) return;
        try {
            String base = normalizeBaseUrl(phoneUrlField.getText());
            String link = base + "/s/" + selectedSession.token();
            qrImage.setImage(new Image(new ByteArrayInputStream(qrCodeService.createPng(link, 320))));
            qrInfo.setText(selectedSession.course() + " · Section " + selectedSession.section() + "\n"
                    + selectedSession.displayStatus() + " · closes " + TIME.format(selectedSession.expiresAt()) + "\n" + link);
        } catch (Exception ex) {
            qrImage.setImage(null);
            qrInfo.setText("Could not create QR code: " + safeMessage(ex));
        }
    }

    private String selectedLink() {
        if (selectedSession == null) return null;
        return normalizeBaseUrl(phoneUrlField.getText()) + "/s/" + selectedSession.token();
    }

    private void copySessionLink() {
        String link = selectedLink();
        if (link == null) {
            showInfo("No session selected", "Create or select a session first.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(link);
        Clipboard.getSystemClipboard().setContent(content);
        showInfo("Check-in link copied", "The phone check-in URL is on your clipboard.");
    }

    private void saveQrImage() {
        String link = selectedLink();
        if (link == null) {
            showInfo("No session selected", "Create or select a session first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save session QR code");
        chooser.setInitialFileName("attendance-" + safeFileName(selectedSession.course()) + ".png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        var file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            Files.write(file.toPath(), qrCodeService.createPng(link, 640));
        } catch (Exception ex) {
            showError("Could not save QR image", ex);
        }
    }

    private void refreshSessionFilter() {
        if (attendanceFilter == null) return;
        try {
            List<SessionChoice> choices = new ArrayList<>();
            choices.add(new SessionChoice(null, "All sessions"));
            for (SessionRecord row : attendanceService.listSessions()) {
                choices.add(new SessionChoice(row.sessionId(), row.course() + " · " + row.section() + " · " + TIME.format(row.startsAt())));
            }
            SessionChoice old = attendanceFilter.getValue();
            attendanceFilter.setItems(FXCollections.observableArrayList(choices));
            attendanceFilter.setValue(old == null ? choices.getFirst() : choices.stream()
                    .filter(choice -> choice.sessionId() == null ? old.sessionId() == null : choice.sessionId().equals(old.sessionId()))
                    .findFirst().orElse(choices.getFirst()));
        } catch (Exception ex) {
            showError("Could not load session filter", ex);
        }
    }

    private void refreshAttendance() {
        if (attendanceTable == null) return;
        try {
            SessionChoice choice = attendanceFilter == null ? null : attendanceFilter.getValue();
            attendanceTable.setItems(FXCollections.observableArrayList(attendanceService.listAttendance(choice == null ? null : choice.sessionId())));
        } catch (Exception ex) {
            showError("Could not load attendance", ex);
        }
    }

    private void exportAttendance() {
        if (attendanceTable == null || attendanceTable.getItems().isEmpty()) {
            showInfo("Nothing to export", "There are no attendance rows for this selection.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export attendance CSV");
        chooser.setInitialFileName("attendance-report.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        var file = chooser.showSaveDialog(stage);
        if (file == null) return;
        StringBuilder csv = new StringBuilder("Student ID,Student,Department,Course,Section,Marked at,Status\r\n");
        for (AttendanceRecord record : attendanceTable.getItems()) {
            csv.append(csv(record.studentId())).append(',').append(csv(record.studentName())).append(',')
                    .append(csv(record.department())).append(',').append(csv(record.course())).append(',')
                    .append(csv(record.section())).append(',').append(csv(TIME.format(record.markedAt()))).append(',')
                    .append(csv(record.status())).append("\r\n");
        }
        try {
            Files.writeString(file.toPath(), "\uFEFF" + csv, StandardCharsets.UTF_8);
            showInfo("Report exported", "Saved " + attendanceTable.getItems().size() + " rows to " + file.getName() + ".");
        } catch (Exception ex) {
            showError("Could not export report", ex);
        }
    }

    private void refreshVisiblePage() {
        if (stage == null || shell == null) return;
        switch (currentPage) {
            case "Overview" -> refreshOverviewStats();
            case "Students" -> refreshStudents();
            case "Sessions" -> refreshSessions();
            case "Attendance" -> {
                refreshSessionFilter();
                refreshAttendance();
            }
        }
    }

    private void refreshOverviewStats() {
        if (overviewStudentsValue == null) return;
        try {
            overviewStudentsValue.setText(String.valueOf(attendanceService.countStudents()));
            overviewTodayValue.setText(String.valueOf(attendanceService.countAttendanceToday()));
            overviewActiveValue.setText(String.valueOf(attendanceService.countActiveSessions()));
        } catch (Exception ex) {
            showError("Could not refresh workspace summary", ex);
        }
    }

    private void changePasswordDialog() {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Change password");
        dialog.setHeaderText("Update your faculty account password");
        ButtonType update = new ButtonType("Update password", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(update, ButtonType.CANCEL);
        PasswordField current = new PasswordField();
        PasswordField next = new PasswordField();
        PasswordField confirm = new PasswordField();
        current.setPromptText("Current password");
        next.setPromptText("New password (10+ characters)");
        confirm.setPromptText("Confirm new password");
        VBox content = new VBox(10, fieldLabel("Current password", current), fieldLabel("New password", next), fieldLabel("Confirm password", confirm));
        content.setPadding(new Insets(10, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == update ? List.of(current.getText(), next.getText(), confirm.getText()) : null);
        Optional<List<String>> result = dialog.showAndWait();
        result.ifPresent(values -> {
            try {
                if (!values.get(1).equals(values.get(2))) throw new IllegalArgumentException("The new passwords do not match.");
                attendanceService.changePassword(currentUsername, values.get(0), values.get(1));
                showInfo("Password changed", "Your faculty account password has been updated.");
            } catch (Exception ex) {
                showError("Could not change password", ex);
            }
        });
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private VBox fieldLabel(String label, Node input) {
        Label caption = new Label(label);
        caption.getStyleClass().add("field-label");
        VBox box = new VBox(7, caption, input);
        if (input instanceof TextField field) field.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox panel(Node... children) {
        VBox box = new VBox(14, children);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(20));
        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    private String detectedBaseUrl() {
        try {
            for (NetworkInterface network : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                for (var address : java.util.Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress() && !ipv4.isLinkLocalAddress()) {
                        return "http://" + ipv4.getHostAddress() + ":" + serverPort;
                    }
                }
            }
        } catch (SocketException ignored) { }
        return "http://127.0.0.1:" + serverPort;
    }

    private static String normalizeBaseUrl(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String safeMessage(Throwable ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String safeFileName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private void showError(String title, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (stage != null) alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(safeMessage(ex));
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (stage != null) alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void installCss(Scene scene) {
        var stylesheet = MainApp.class.getResource("app.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
    }

    private record SessionChoice(String sessionId, String label) {
        @Override public String toString() { return label; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

