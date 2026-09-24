package edu.cse203.qrattendance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

final class LocalAttendanceServer implements AutoCloseable {
    static final int DEFAULT_PORT = 8765;
    private final AttendanceService attendanceService;
    private HttpServer server;

    LocalAttendanceServer(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    int start() throws IOException {
        int port = DEFAULT_PORT;
        String configured = System.getenv("QR_ATTENDANCE_PORT");
        if (configured != null && !configured.isBlank()) {
            try {
                port = Integer.parseInt(configured.trim());
            } catch (NumberFormatException ex) {
                throw new IOException("QR_ATTENDANCE_PORT must be a valid port number.", ex);
            }
        }
        if (port < 1 || port > 65_535) throw new IOException("QR_ATTENDANCE_PORT must be between 1 and 65535.");
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "qr-attendance-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/health".equals(path)) {
                send(exchange, 200, "text/plain; charset=utf-8", "QR Attendance System is ready.");
                return;
            }
            if (!path.startsWith("/s/")) {
                send(exchange, 404, "text/html; charset=utf-8", page("QR Attendance", "Open a session QR code to check in."));
                return;
            }
            String token = path.substring(3);
            if (!token.matches("[0-9a-fA-F-]{36}")) {
                send(exchange, 404, "text/html; charset=utf-8", page("Link not found", "This attendance link is not valid."));
                return;
            }
            SessionRecord session = attendanceService.findSessionByToken(token);
            if (session == null) {
                send(exchange, 404, "text/html; charset=utf-8", page("Link not found", "This attendance link is not valid."));
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!session.isOpenNow()) {
                    send(exchange, 410, "text/html; charset=utf-8", page("Session ended", "This attendance session is closed or has expired."));
                    return;
                }
                String content = """
                        <main class="sheet">
                          <div class="mark">QR<span>ATTENDANCE</span></div>
                          <p class="eyebrow">STUDENT CHECK-IN</p>
                          <h1>%s</h1>
                          <p class="sub">Section %s · open until %s</p>
                          <form method="post" action="/s/%s">
                            <label for="studentId">Student ID</label>
                            <input id="studentId" name="studentId" autocomplete="off" maxlength="30" required placeholder="e.g. CSE203-014">
                            <button type="submit">Mark attendance</button>
                          </form>
                          <p class="note">Use the student ID registered with your faculty.</p>
                        </main>
                        """.formatted(escape(session.course()), escape(session.section()),
                        escape(session.expiresAt().toString().replace('T', ' ').substring(0, 16) + " UTC"), escape(token));
                send(exchange, 200, "text/html; charset=utf-8", page("Student check-in", content));
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readNBytes(4097);
                if (body.length > 4096) {
                    send(exchange, 413, "text/html; charset=utf-8", page("Request too large", "Please submit a shorter student ID."));
                    return;
                }
                String raw = new String(body, StandardCharsets.UTF_8);
                String studentId = formValue(raw, "studentId");
                try {
                    attendanceService.markAttendance(token, studentId);
                    String content = """
                            <main class="sheet success">
                              <div class="check">✓</div>
                              <p class="eyebrow">ATTENDANCE RECORDED</p>
                              <h1>You're checked in</h1>
                              <p class="sub">%s · Section %s</p>
                              <p class="note">You can close this page now.</p>
                            </main>
                            """.formatted(escape(session.course()), escape(session.section()));
                    send(exchange, 200, "text/html; charset=utf-8", page("Attendance recorded", content));
                } catch (IllegalArgumentException ex) {
                    String content = """
                            <main class="sheet">
                              <div class="mark">QR<span>ATTENDANCE</span></div>
                              <p class="eyebrow">CHECK-IN NOT COMPLETED</p>
                              <h1>Try again</h1>
                              <p class="error">%s</p>
                              <a class="back" href="/s/%s">Return to check-in</a>
                            </main>
                            """.formatted(escape(ex.getMessage()), escape(token));
                    send(exchange, 200, "text/html; charset=utf-8", page("Check-in not completed", content));
                }
                return;
            }
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed.");
        } catch (Exception ex) {
            send(exchange, 500, "text/html; charset=utf-8", page("Service error", "The check-in service could not complete this request."));
        } finally {
            exchange.close();
        }
    }

    private static String page(String title, String content) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="theme-color" content="#101f33"><title>%s</title>
                <style>
                *{box-sizing:border-box}body{margin:0;min-height:100vh;padding:24px;background:#eef2f5;color:#15253a;
                font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;display:grid;place-items:center}
                .sheet{width:min(100%%,440px);padding:30px;background:white;border:1px solid #e1e7ec;border-radius:20px;
                box-shadow:0 18px 55px #192b4014}.mark{font-size:15px;font-weight:800;letter-spacing:.12em;color:#16a384}
                .mark span{margin-left:8px;font-size:10px;letter-spacing:.17em;color:#647387}.eyebrow{margin:36px 0 10px;
                color:#16866e;font-size:11px;font-weight:800;letter-spacing:.16em}h1{margin:0;font-size:28px;line-height:1.18}
                .sub,.note{color:#667589;line-height:1.55}.sub{margin:12px 0 28px}.note{font-size:13px;margin:18px 0 0}
                label{display:block;font-size:13px;font-weight:700;margin:0 0 8px}input{width:100%;height:50px;border:1px solid #d3dce4;
                border-radius:10px;padding:0 14px;font:inherit;font-size:16px;outline:none}input:focus{border-color:#169b7c;box-shadow:0 0 0 3px #169b7c20}
                button,.back{display:block;width:100%%;margin-top:14px;border:0;border-radius:10px;padding:15px;background:#147c68;color:white;
                text-align:center;font:inherit;font-weight:700;text-decoration:none;cursor:pointer}.error{padding:12px 14px;border-radius:10px;
                background:#fff1ee;color:#a54334;line-height:1.5}.check{display:grid;place-items:center;width:54px;height:54px;border-radius:50%;
                background:#e2f6ef;color:#16866e;font-size:28px;font-weight:700}.success .eyebrow{margin-top:26px}
                </style></head><body>%s</body></html>
                """.formatted(escape(title), content);
    }

    private static String formValue(String body, String key) {
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) continue;
            String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (key.equals(name)) return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
        }
        return "";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        if (server != null) server.stop(1);
    }
}

