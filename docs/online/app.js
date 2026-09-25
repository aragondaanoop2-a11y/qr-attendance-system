import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm";

const settings = window.ATTENDLY_CONFIG || {};
const configured = Boolean(settings.supabaseUrl && settings.publishableKey);
const supabase = configured ? createClient(settings.supabaseUrl, settings.publishableKey) : null;
const page = document.body.dataset.page;
const TOKEN_STORE = "attendly.pendingCheckin";
const initialAuthQuery = new URLSearchParams(location.search);
const initialAuthHash = new URLSearchParams(location.hash.replace(/^#/, ""));
const hasAuthCallback = Boolean(initialAuthHash.toString() || initialAuthQuery.has("code") || initialAuthQuery.has("type"));
const isPasswordRecoveryCallback =
  initialAuthQuery.get("type") === "recovery" || initialAuthHash.get("type") === "recovery";

function setNotice(element, message, kind) {
  if (!element) return;
  element.textContent = message;
  element.classList.remove("hidden", "notice-error", "notice-success", "notice-warning");
  if (kind) element.classList.add("notice-" + kind);
}

function hide(element) {
  if (element) element.classList.add("hidden");
}

function friendlyError(error) {
  const message = error && error.message ? error.message : String(error || "Unknown error.");
  if (/invalid login credentials/i.test(message)) return "Email or password is incorrect.";
  if (/email not confirmed/i.test(message)) return "Confirm your email using the link Supabase sent, then sign in.";
  if (/user already registered/i.test(message)) return "An account already uses this email. Sign in instead.";
  if (/duplicate key|qr_profiles_student_id_unique/i.test(message)) return "That student ID is already registered. Sign in to the existing account.";
  return message;
}

function showSetupWarning() {
  const warning = document.getElementById("config-warning") || document.getElementById("page-message");
  setNotice(warning, "Setup required: the project owner must add the Supabase Project URL and publishable key to config.js. Follow the setup guide before signing in.", "warning");
}

async function getSignedInProfile(requiredRole) {
  const result = await supabase.auth.getUser();
  if (result.error) throw result.error;
  if (!result.data.user) return { user: null, profile: null };
  const profileResult = await supabase.from("qr_profiles")
    .select("id, full_name, student_id, role")
    .eq("id", result.data.user.id)
    .maybeSingle();
  if (profileResult.error) throw profileResult.error;
  if (!profileResult.data) throw new Error("Your profile was not found. Ask the project owner to run the Supabase setup.");
  if (requiredRole && profileResult.data.role !== requiredRole) {
    throw new Error("This account is set up as " + profileResult.data.role + ". Open the " + profileResult.data.role + " portal instead.");
  }
  return { user: result.data.user, profile: profileResult.data };
}

function getPendingCheckin() {
  try {
    const current = new URLSearchParams(location.search);
    const sessionId = current.get("session");
    const token = current.get("token");
    if (sessionId && token) {
      const pending = { sessionId: sessionId, token: token };
      localStorage.setItem(TOKEN_STORE, JSON.stringify(pending));
      return pending;
    }
    const saved = JSON.parse(localStorage.getItem(TOKEN_STORE) || "null");
    if (saved && saved.sessionId && saved.token) return saved;
  } catch (_) {}
  return null;
}

function clearPendingCheckin() {
  try { localStorage.removeItem(TOKEN_STORE); } catch (_) {}
}

function studentPortalUrl(pending) {
  const url = new URL("student.html", location.href);
  if (pending) {
    url.searchParams.set("session", pending.sessionId);
    url.searchParams.set("token", pending.token);
  }
  return url.href;
}

document.querySelectorAll("[data-signout]").forEach(function (button) {
  button.addEventListener("click", async function () {
    if (supabase) await supabase.auth.signOut();
    location.href = "index.html";
  });
});

if (!configured && page !== "home") showSetupWarning();
if (page === "auth") initAuth();
if (page === "faculty" && configured) initFaculty();
if (page === "student" && configured) initStudent();

function initAuth() {
  const params = new URLSearchParams(location.search);
  const role = params.get("role") === "faculty" ? "faculty" : "student";
  const message = document.getElementById("auth-message");
  const form = document.getElementById("auth-form");
  const passwordUpdateForm = document.getElementById("password-update-form");
  const fields = document.getElementById("student-fields");
  const toggle = document.getElementById("student-signup-toggle");
  const facultyNote = document.getElementById("faculty-note");
  const forgot = document.getElementById("forgot-password");
  let signingUp = false;

  document.getElementById("auth-eyebrow").textContent = role === "faculty" ? "FACULTY PORTAL" : "STUDENT PORTAL";
  document.getElementById("auth-title").textContent = role === "faculty" ? "Faculty sign in" : "Student sign in";
  document.getElementById("auth-subtitle").textContent = role === "faculty"
    ? "Sign in with the faculty account provided by your administrator."
    : "Sign in to check in to your class or create a student account.";
  if (role === "student") toggle.classList.remove("hidden");
  else facultyNote.classList.remove("hidden");
  forgot.classList.remove("hidden");
  if (role === "student") getPendingCheckin();

  document.getElementById("toggle-signup").addEventListener("click", function () {
    signingUp = !signingUp;
    fields.classList.toggle("hidden", !signingUp);
    forgot.classList.toggle("hidden", signingUp);
    document.getElementById("auth-title").textContent = signingUp ? "Create student account" : "Student sign in";
    document.getElementById("submit-auth").textContent = signingUp ? "Create account" : "Sign in";
    document.getElementById("toggle-signup").textContent = signingUp ? "Sign in instead" : "Create an account";
    document.getElementById("full-name").required = signingUp;
    document.getElementById("student-id").required = signingUp;
    hide(message);
  });

  if (!supabase) return;

  function showPasswordUpdateForm() {
    form.classList.add("hidden");
    passwordUpdateForm.classList.remove("hidden");
    toggle.classList.add("hidden");
    forgot.classList.add("hidden");
    facultyNote.classList.add("hidden");
    setNotice(message, "Choose a new password for your account.", "warning");
    document.getElementById("auth-title").textContent = "Reset your password";
  }

  if (isPasswordRecoveryCallback) showPasswordUpdateForm();

  passwordUpdateForm.addEventListener("submit", async function (event) {
    event.preventDefault();
    const password = document.getElementById("new-password").value;
    const result = await supabase.auth.updateUser({ password: password });
    if (result.error) {
      setNotice(message, friendlyError(result.error), "error");
      return;
    }
    await supabase.auth.signOut();
    passwordUpdateForm.classList.add("hidden");
    form.classList.remove("hidden");
    setNotice(message, "Password changed. Sign in with your new password.", "success");
    document.getElementById("auth-title").textContent = role === "faculty" ? "Faculty sign in" : "Student sign in";
  });

  supabase.auth.onAuthStateChange(function (event) {
    if (event === "PASSWORD_RECOVERY") {
      showPasswordUpdateForm();
    }
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    hide(message);
    const button = document.getElementById("submit-auth");
    button.disabled = true;
    button.textContent = signingUp ? "Creating account…" : "Signing in…";
    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    try {
      let authResult;
      if (signingUp) {
        const fullName = document.getElementById("full-name").value.trim();
        const studentId = document.getElementById("student-id").value.trim().toUpperCase();
        if (!fullName || !studentId) throw new Error("Enter your full name and student ID.");
        authResult = await supabase.auth.signUp({
          email: email,
          password: password,
          options: {
            data: { full_name: fullName, student_id: studentId },
            emailRedirectTo: new URL("auth.html?role=student", location.href).href
          }
        });
        if (authResult.error) throw authResult.error;
        if (!authResult.data.session) {
          setNotice(message, "Account created. Check your email to confirm the address, then return here and sign in.", "success");
          return;
        }
      } else {
        authResult = await supabase.auth.signInWithPassword({ email: email, password: password });
        if (authResult.error) throw authResult.error;
      }
      const profile = await getSignedInProfile();
      if (!profile.profile || profile.profile.role !== role) {
        throw new Error("This account is set up as " + (profile.profile ? profile.profile.role : "unknown") + ". Open the matching portal.");
      }
      const pending = role === "student" ? getPendingCheckin() : null;
      if (role === "student") clearPendingCheckin();
      location.href = role === "faculty" ? "faculty.html" : studentPortalUrl(pending);
    } catch (error) {
      setNotice(message, friendlyError(error), "error");
    } finally {
      button.disabled = false;
      button.textContent = signingUp ? "Create account" : "Sign in";
    }
  });

  forgot.addEventListener("click", async function () {
    hide(message);
    const email = document.getElementById("email").value.trim();
    if (!email) {
      setNotice(message, "Enter your email address first, then choose Forgot password.", "warning");
      return;
    }
    const result = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: new URL("auth.html?role=" + role, location.href).href
    });
    if (result.error) setNotice(message, friendlyError(result.error), "error");
    else setNotice(message, "If an account uses that email, Supabase will send a password reset link.", "success");
  });

  supabase.auth.getSession().then(async function (result) {
    if (hasAuthCallback) {
      if (isPasswordRecoveryCallback && !result.data.session) {
        setNotice(message, "This password-reset link may have expired. Request a new reset email and open its newest link.", "error");
      }
      return;
    }
    if (!result.data.session) return;
    try {
      const profile = await getSignedInProfile();
      if (profile.profile.role === role) {
        const pending = role === "student" ? getPendingCheckin() : null;
        if (role === "student") clearPendingCheckin();
        location.href = role === "faculty" ? "faculty.html" : studentPortalUrl(pending);
      } else {
        setNotice(message, "This account is a " + profile.profile.role + " account. Open the matching portal.", "warning");
      }
    } catch (error) {
      setNotice(message, friendlyError(error), "error");
    }
  });
}

async function initFaculty() {
  const notice = document.getElementById("page-message");
  let profile;
  try {
    const signedIn = await getSignedInProfile("faculty");
    if (!signedIn.user) {
      location.href = "auth.html?role=faculty";
      return;
    }
    profile = signedIn.profile;
  } catch (error) {
    setNotice(notice, friendlyError(error), "error");
    return;
  }

  document.getElementById("signed-in-user").textContent = profile.full_name || "Faculty";
  let sessions = [];
  let records = [];
  let activeSession = null;
  let timer = null;

  function renderActive(session, token) {
    activeSession = session && token ? Object.assign({}, session, { checkin_token: token }) : null;
    const panel = document.getElementById("active-session");
    const qrHolder = document.getElementById("session-qr");
    if (!activeSession || activeSession.status !== "active" || new Date(activeSession.expires_at).getTime() <= Date.now()) {
      panel.classList.add("hidden");
      if (timer) clearInterval(timer);
      return;
    }
    panel.classList.remove("hidden");
    document.getElementById("session-course").textContent = activeSession.course;
    document.getElementById("session-section").textContent = activeSession.section || "No section specified";
    const checkinUrl = new URL("student.html", location.href);
    checkinUrl.searchParams.set("session", activeSession.id);
    checkinUrl.searchParams.set("token", token);
    const link = document.getElementById("session-link");
    link.href = checkinUrl.href;
    link.textContent = checkinUrl.href;
    qrHolder.replaceChildren();
    if (window.QRCode) {
      new window.QRCode(qrHolder, {
        text: checkinUrl.href,
        width: 220,
        height: 220,
        colorDark: "#102a43",
        colorLight: "#ffffff",
        correctLevel: window.QRCode.CorrectLevel.M
      });
    } else {
      qrHolder.textContent = "QR library did not load. Copy the check-in link below instead.";
    }
    updateTimer();
    if (timer) clearInterval(timer);
    timer = setInterval(updateTimer, 1000);
  }

  function updateTimer() {
    if (!activeSession) return;
    const seconds = Math.max(0, Math.floor((new Date(activeSession.expires_at).getTime() - Date.now()) / 1000));
    document.getElementById("session-timer").textContent = String(Math.floor(seconds / 60)).padStart(2, "0") + ":" + String(seconds % 60).padStart(2, "0");
    if (!seconds) {
      renderActive(null, null);
      refreshSessions();
    }
  }

  async function refreshSessions() {
    const result = await supabase.from("qr_attendance_sessions")
      .select("id, course, section, status, created_at, expires_at")
      .order("created_at", { ascending: false })
      .limit(50);
    if (result.error) {
      setNotice(notice, friendlyError(result.error), "error");
      return;
    }
    sessions = result.data || [];
    const stored = sessionStorage.getItem("attendly.facultyQr");
    let remembered = null;
    try { remembered = JSON.parse(stored || "null"); } catch (_) {}
    const current = sessions.find(function (item) {
      return item.status === "active" && new Date(item.expires_at).getTime() > Date.now();
    });
    if (current && remembered && remembered.id === current.id && remembered.token) renderActive(current, remembered.token);
    else if (!current || (activeSession && activeSession.id !== current.id)) renderActive(null, null);

    const list = document.getElementById("session-list");
    list.replaceChildren();
    if (!sessions.length) {
      const empty = document.createElement("p");
      empty.className = "muted-copy";
      empty.textContent = "No sessions yet. Start one to record attendance.";
      list.append(empty);
      return;
    }
    sessions.forEach(function (session) {
      const row = document.createElement("div");
      row.className = "session-history-row";
      const details = document.createElement("div");
      const title = document.createElement("strong");
      title.textContent = session.course;
      const meta = document.createElement("span");
      meta.textContent = (session.section || "No section") + " · " + new Date(session.created_at).toLocaleString();
      details.append(title, meta);
      const badge = document.createElement("span");
      const isOpen = session.status === "active" && new Date(session.expires_at).getTime() > Date.now();
      badge.className = "status-badge " + (isOpen ? "status-open" : "status-closed");
      badge.textContent = isOpen ? "Open" : session.status === "closed" ? "Ended" : "Expired";
      row.append(details, badge);
      list.append(row);
    });
  }

  async function refreshRecords() {
    const result = await supabase.from("qr_faculty_attendance")
      .select("student_id, student_name, course, section, marked_at")
      .order("marked_at", { ascending: false })
      .limit(2000);
    if (result.error) {
      setNotice(notice, friendlyError(result.error), "error");
      return;
    }
    records = result.data || [];
    drawRecords();
  }

  function drawRecords() {
    const query = document.getElementById("record-search").value.trim().toLowerCase();
    const matching = records.filter(function (record) {
      return [record.student_name, record.student_id, record.course, record.section]
        .some(function (value) { return String(value || "").toLowerCase().includes(query); });
    });
    const body = document.getElementById("attendance-rows");
    body.replaceChildren();
    if (!matching.length) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 5;
      cell.textContent = records.length ? "No matching attendance records." : "No attendance has been recorded yet.";
      row.append(cell);
      body.append(row);
    } else {
      matching.forEach(function (record) {
        const row = document.createElement("tr");
        [record.student_name, record.student_id, record.course, record.section || "—", new Date(record.marked_at).toLocaleString()]
          .forEach(function (value) {
            const cell = document.createElement("td");
            cell.textContent = value || "—";
            row.append(cell);
          });
        body.append(row);
      });
    }
    document.getElementById("record-count").textContent = matching.length + " record" + (matching.length === 1 ? "" : "s");
  }

  document.getElementById("session-form").addEventListener("submit", async function (event) {
    event.preventDefault();
    hide(notice);
    const button = document.getElementById("create-session");
    button.disabled = true;
    button.textContent = "Creating session…";
    const result = await supabase.rpc("qr_create_session", {
      p_course: document.getElementById("course").value.trim(),
      p_section: document.getElementById("section").value.trim(),
      p_duration_minutes: Number(document.getElementById("duration").value)
    });
    button.disabled = false;
    button.innerHTML = "Create QR session <span aria-hidden=\"true\">→</span>";
    if (result.error) {
      setNotice(notice, friendlyError(result.error), "error");
      return;
    }
    const created = Array.isArray(result.data) ? result.data[0] : result.data;
    if (!created || !created.new_session_id || !created.checkin_token) {
      setNotice(notice, "Supabase did not return the new session link. Check that setup.sql ran successfully.", "error");
      return;
    }
    const session = {
      id: created.new_session_id,
      course: document.getElementById("course").value.trim(),
      section: document.getElementById("section").value.trim(),
      status: "active",
      created_at: new Date().toISOString(),
      expires_at: created.expires_at
    };
    sessionStorage.setItem("attendly.facultyQr", JSON.stringify({ id: session.id, token: created.checkin_token }));
    renderActive(session, created.checkin_token);
    await refreshSessions();
    setNotice(notice, "Attendance session is live. Students can scan this QR from any network.", "success");
  });

  document.getElementById("close-session").addEventListener("click", async function () {
    if (!activeSession) return;
    const result = await supabase.from("qr_attendance_sessions")
      .update({ status: "closed" })
      .eq("id", activeSession.id);
    if (result.error) {
      setNotice(notice, friendlyError(result.error), "error");
      return;
    }
    sessionStorage.removeItem("attendly.facultyQr");
    renderActive(null, null);
    setNotice(notice, "Session ended. Its QR code can no longer record attendance.", "success");
    await refreshSessions();
  });

  document.getElementById("copy-link").addEventListener("click", async function () {
    try {
      await navigator.clipboard.writeText(document.getElementById("session-link").href);
      setNotice(notice, "Student check-in link copied.", "success");
    } catch (_) {
      setNotice(notice, "Copy is unavailable. Select and copy the student check-in link shown above the buttons.", "warning");
    }
  });

  document.getElementById("record-search").addEventListener("input", drawRecords);
  document.getElementById("refresh-data").addEventListener("click", refreshSessions);
  document.getElementById("refresh-records").addEventListener("click", refreshRecords);
  document.getElementById("export-csv").addEventListener("click", function () {
    const csvCell = function (value) {
      const text = String(value == null ? "" : value);
      const safe = /^[\s]*[=+@-]/.test(text) ? "'" + text : text;
      return "\"" + safe.replaceAll("\"", "\"\"") + "\"";
    };
    const lines = [["Student ID", "Student", "Course", "Section", "Check-in time"]]
      .concat(records.map(function (record) {
        return [record.student_id, record.student_name, record.course, record.section, new Date(record.marked_at).toISOString()];
      }));
    const file = new Blob([lines.map(function (line) { return line.map(csvCell).join(","); }).join("\r\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(file);
    const link = document.createElement("a");
    link.href = url;
    link.download = "attendly-attendance.csv";
    link.click();
    URL.revokeObjectURL(url);
  });

  await Promise.all([refreshSessions(), refreshRecords()]);
}

async function initStudent() {
  const notice = document.getElementById("page-message");
  const params = new URLSearchParams(location.search);
  const sessionId = params.get("session");
  const token = params.get("token");
  const signinPrompt = document.getElementById("student-signin-prompt");
  const loginLink = document.getElementById("student-login-link");
  if (sessionId && token) {
    const target = new URL("auth.html", location.href);
    target.searchParams.set("role", "student");
    target.searchParams.set("session", sessionId);
    target.searchParams.set("token", token);
    loginLink.href = target.href;
  }

  let signedIn;
  try {
    signedIn = await getSignedInProfile();
  } catch (error) {
    setNotice(notice, friendlyError(error), "error");
    return;
  }
  if (!signedIn.user) {
    signinPrompt.classList.remove("hidden");
    return;
  }
  if (signedIn.profile.role !== "student") {
    setNotice(notice, "This account is for faculty. Sign out and use your student account to check in.", "error");
    return;
  }
  document.getElementById("signed-in-user").textContent = signedIn.profile.full_name || "Student";
  if (!sessionId || !token || !/^[0-9a-f-]{36}$/i.test(sessionId) || !/^[0-9a-f]{64}$/i.test(token)) {
    setNotice(notice, "Open the current QR code shown by your faculty member to check in.", "warning");
    return;
  }

  setNotice(notice, "Checking your session…", "warning");
  const result = await supabase.rpc("qr_record_attendance", {
    p_session_id: sessionId,
    p_checkin_token: token
  });
  if (result.error) {
    setNotice(notice, friendlyError(result.error), "error");
    return;
  }
  const receipt = Array.isArray(result.data) ? result.data[0] : result.data;
  if (!receipt) {
    setNotice(notice, "No attendance confirmation was returned. Ask faculty to check the session.", "error");
    return;
  }
  hide(notice);
  document.getElementById("checkin-result").classList.remove("hidden");
  document.getElementById("result-title").textContent = receipt.already_recorded ? "Already checked in" : "Attendance recorded";
  document.getElementById("result-copy").textContent =
    (receipt.already_recorded ? "Your attendance was already recorded for " : "Your attendance is recorded for ") +
    receipt.course + (receipt.section ? " · " + receipt.section : "") +
    " at " + new Date(receipt.marked_at).toLocaleString() + ".";
}
