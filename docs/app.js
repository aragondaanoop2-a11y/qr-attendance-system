(() => {
  const RECORDS_KEY = 'attendly.demo.records.v1';
  const SESSION_KEY = 'attendly.demo.session.v1';
  const demoRecords = [
    {studentId:'STU-2401',name:'Student 001',course:'CSE 203 · Data Structures',date:'Today',time:'09:02 AM',status:'Present'},
    {studentId:'STU-2402',name:'Student 002',course:'CSE 203 · Data Structures',date:'Today',time:'09:04 AM',status:'Present'},
    {studentId:'STU-2403',name:'Student 003',course:'CSE 203 · Data Structures',date:'Today',time:'09:13 AM',status:'Late'},
    {studentId:'STU-2404',name:'Student 004',course:'CSE 315 · Database Systems',date:'Today',time:'11:31 AM',status:'Present'},
    {studentId:'STU-2405',name:'Student 005',course:'MAT 221 · Discrete Mathematics',date:'Today',time:'02:05 PM',status:'Late'}
  ];
  const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) || fallback; } catch { return fallback; } };
  const save = (key, value) => localStorage.setItem(key, JSON.stringify(value));
  const pad = n => String(n).padStart(2,'0');
  const formatTime = date => `${pad((date.getHours()+11)%12+1)}:${pad(date.getMinutes())} ${date.getHours()>=12?'PM':'AM'}`;
  const formatDate = date => date.toLocaleDateString(undefined,{month:'short',day:'numeric',year:'numeric'});
  const initials = name => name.split(/\s+/).slice(0,2).map(part=>part[0]||'').join('').toUpperCase();
  const esc = value => String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const rowsWithDefaults = () => {
    const saved = read(RECORDS_KEY, null);
    return Array.isArray(saved) ? saved : demoRecords;
  };

  if (document.body.dataset.page === 'faculty') initFaculty();
  if (document.body.dataset.page === 'student') initStudent();

  function initFaculty() {
    const today = document.getElementById('today-label');
    if (today) today.textContent = new Date().toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'});
    const form = document.getElementById('session-form');
    const sessionBox = document.getElementById('active-session');
    let activeSession = read(SESSION_KEY, null);
    let tick;

    function showSession(session) {
      activeSession = session;
      if (!session) {
        sessionBox.classList.add('hidden');
        return;
      }
      const left = Math.max(0, Math.floor((session.expiresAt-Date.now())/1000));
      if (left <= 0) {
        save(SESSION_KEY, null);
        activeSession = null;
        sessionBox.classList.add('hidden');
        return;
      }
      const link = new URL('student.html', location.href);
      link.searchParams.set('session', session.code);
      link.searchParams.set('course', session.course);
      link.searchParams.set('expires', String(session.expiresAt));
      document.getElementById('session-class').textContent = session.course;
      document.getElementById('session-code').textContent = session.code;
      document.getElementById('student-link').textContent = link.href;
      document.getElementById('session-qr').src = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=8&data=${encodeURIComponent(link.href)}`;
      document.getElementById('session-timer').textContent = `${pad(Math.floor(left/60))}:${pad(left%60)}`;
      sessionBox.classList.remove('hidden');
    }

    form.addEventListener('submit', event => {
      event.preventDefault();
      const course = document.getElementById('class-select').value;
      const session = {course,code:String(Math.floor(100000+Math.random()*900000)),expiresAt:Date.now()+10*60*1000};
      save(SESSION_KEY,session);
      showSession(session);
      if (tick) clearInterval(tick);
      tick = setInterval(()=>showSession(activeSession),1000);
    });
    document.getElementById('end-session').addEventListener('click',()=>{save(SESSION_KEY,null);showSession(null);if(tick)clearInterval(tick);});
    document.getElementById('copy-link').addEventListener('click',async event=>{
      const link = new URL('student.html',location.href);
      link.searchParams.set('session',activeSession?.code||'');
      link.searchParams.set('course',activeSession?.course||'');
      link.searchParams.set('expires',String(activeSession?.expiresAt||''));
      try { await navigator.clipboard.writeText(link.href); event.currentTarget.textContent='Link copied'; setTimeout(()=>event.currentTarget.textContent='Copy check-in link',1800); }
      catch { event.currentTarget.textContent='Copy from address above'; }
    });

    function drawRows() {
      const query = document.getElementById('record-search').value.trim().toLowerCase();
      const records = rowsWithDefaults().filter(row => `${row.name} ${row.studentId} ${row.course} ${row.status}`.toLowerCase().includes(query));
      const tbody = document.getElementById('attendance-rows');
      tbody.innerHTML = records.map(row=>`<tr><td><span class="student-cell"><span class="student-avatar">${esc(initials(row.name))}</span>${esc(row.name)}</span></td><td>${esc(row.course)}</td><td>${esc(row.date)}</td><td>${esc(row.time)}</td><td><span class="status-pill ${row.status==='Late'?'status-late':'status-present'}">${esc(row.status)}</span></td></tr>`).join('');
      document.getElementById('record-count').textContent=`${records.length} record${records.length===1?'':'s'}`;
      document.getElementById('empty-records').classList.toggle('hidden',records.length>0);
      const stored = read(RECORDS_KEY, []);
      if (stored.length) {
        const newCheckIns = Math.max(0, stored.length - demoRecords.length);
        document.getElementById('stat-present').textContent = String(Math.min(24,18+newCheckIns));
        document.getElementById('stat-students').textContent = '24';
      }
    }
    document.getElementById('record-search').addEventListener('input',drawRows);
    document.getElementById('export-csv').addEventListener('click',()=>{
      const rows = rowsWithDefaults();
      const csvCell = value => {
        const text = String(value);
        const safe = /^[\s]*[=+@-]/.test(text) ? `'${text}` : text;
        return `"${safe.replaceAll('"','""')}"`;
      };
      const csv = [['Student ID','Student','Class','Date','Check-in','Status'],...rows.map(r=>[r.studentId,r.name,r.course,r.date,r.time,r.status])].map(line=>line.map(csvCell).join(',')).join('\r\n');
      const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));
      const a=document.createElement('a');a.href=url;a.download='attendly-attendance.csv';a.click();URL.revokeObjectURL(url);
    });
    window.addEventListener('storage',event=>{if(event.key===RECORDS_KEY)drawRows();if(event.key===SESSION_KEY)showSession(read(SESSION_KEY,null));});
    drawRows();
    if(activeSession){showSession(activeSession);tick=setInterval(()=>showSession(activeSession),1000);}
  }

  function initStudent() {
    const codeInput=document.getElementById('checkin-code');
    const params=new URLSearchParams(location.search);
    const incoming=params.get('session');
    const incomingCourse=params.get('course');
    const incomingExpiry=Number(params.get('expires'));
    const qrSession=incoming && incomingCourse && Number.isFinite(incomingExpiry) ? {code:incoming,course:incomingCourse,expiresAt:incomingExpiry} : null;
    const session=read(SESSION_KEY,null);
    if(incoming) codeInput.value=incoming;
    const visibleSession = qrSession || (session && session.expiresAt>Date.now() ? session : null);
    if(visibleSession && visibleSession.expiresAt>Date.now()) {
      document.getElementById('session-banner-title').textContent=visibleSession.course;
      document.getElementById('session-banner-subtitle').textContent=`Session is live · code ${visibleSession.code}`;
      document.getElementById('session-banner').classList.add('session-banner-live');
    } else if(incoming) {
      document.getElementById('session-banner-title').textContent='QR code opened';
      document.getElementById('session-banner-subtitle').textContent='Enter your student details and confirm attendance.';
    }
    document.getElementById('checkin-form').addEventListener('submit',event=>{
      event.preventDefault();
      const studentId=document.getElementById('student-id').value.trim().toUpperCase();
      const name=document.getElementById('student-name').value.trim();
      const code=codeInput.value.trim();
      if(!/^\d{6}$/.test(code)) { alert('Please enter the 6-digit session code shown by your faculty member.'); return; }
      const localSession=read(SESSION_KEY,null);
      const current=localSession && localSession.code===code ? localSession : (qrSession && qrSession.code===code ? qrSession : null);
      if(!current || current.expiresAt<=Date.now()) { alert('That session code is not active. Ask your faculty member to start a new session and scan its QR.'); return; }
      const records=rowsWithDefaults();
      if(records.some(row=>row.studentId.toLowerCase()===studentId.toLowerCase() && row.course===current.course)) { alert('This student ID has already checked in for this class session.'); return; }
      const now=new Date();
      const item={studentId,name,course:current.course,date:formatDate(now),time:formatTime(now),status:'Present'};
      const saved=read(RECORDS_KEY,null);
      save(RECORDS_KEY,[...(Array.isArray(saved)?saved:demoRecords),item]);
      document.getElementById('receipt-student').textContent=`${name} · ${studentId}`;
      document.getElementById('receipt-class').textContent=current.course;
      document.getElementById('receipt-time').textContent=`${formatDate(now)} at ${formatTime(now)}`;
      document.getElementById('success-copy').textContent='Your demo attendance record has been saved in this browser.';
      document.getElementById('checkin-form-view').classList.add('hidden');
      document.getElementById('checkin-success').classList.remove('hidden');
    });
    document.getElementById('another-checkin').addEventListener('click',()=>{
      document.getElementById('checkin-success').classList.add('hidden');
      document.getElementById('checkin-form-view').classList.remove('hidden');
      document.getElementById('checkin-form').reset();
      if(incoming) codeInput.value=incoming;
    });
  }
})();

