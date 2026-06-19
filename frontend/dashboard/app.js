
  const API_BASE = window.location.protocol === 'file:' ? 'http://localhost:8080' : '';

  function escapeHTML(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Intercept all fetch responses to auto-logout on 401/403 (unauthorized/forbidden)
  const originalFetch = window.fetch;
  window.fetch = async function(...args) {
    try {
      const res = await originalFetch(...args);
      const url = args[0] ? args[0].toString() : '';
      if ((res.status === 401 || res.status === 403) && !url.includes('/api/auth/')) {
        logout();
      }
      return res;
    } catch (err) {
      throw err;
    }
  };

  function getToken() {
    return localStorage.getItem("cp_token");
  }

  function getHeaders(extraHeaders = {}) {
    const token = getToken();
    const projectId = localStorage.getItem("cp_project_id");
    return {
      "Content-Type": "application/json",
      ...(token ? { "Authorization": `Bearer ${token}` } : {}),
      ...(projectId ? { "X-Project-Id": projectId } : {}),
      ...extraHeaders
    };
  }

  function checkAuth() {
    const token = getToken();
    const name = localStorage.getItem("cp_name");
    const email = localStorage.getItem("cp_email");
    if (!token) {
      document.getElementById("authOverlay").style.display = "flex";
    } else {
      document.getElementById("authOverlay").style.display = "none";
      document.getElementById("userDisplay").textContent = `${name} (${email})`;
      document.getElementById("mUserMail").textContent = email;
      clog('consoleBox', `[INFO] Session active for ${email}`, 'info');
      // Load projects list
      loadProjectsList();
      // Load initial page content
      loadDashboardPools();
      loadLatestLogs();
    }
  }

  function toggleAuthMode(toLogin) {
    document.getElementById("loginForm").style.display = toLogin ? "block" : "none";
    document.getElementById("registerForm").style.display = toLogin ? "none" : "block";
    document.getElementById("authError").style.display = "none";
  }

  async function performAuth(mode) {
    const errorDiv = document.getElementById("authError");
    errorDiv.style.display = "none";
    
    let payload = {};
    let url = `${API_BASE}/api/auth/login`;

    if (mode === 'login') {
      payload = {
        email: document.getElementById("authEmail").value,
        password: document.getElementById("authPassword").value
      };
    } else {
      url = `${API_BASE}/api/auth/register`;
      payload = {
        name: document.regName ? document.getElementById("regName").value : "Developer",
        email: document.getElementById("regEmail").value,
        password: document.getElementById("regPassword").value
      };
    }

    try {
      const res = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) {
        errorDiv.style.display = "block";
        errorDiv.textContent = data.error || "Authentication failed";
        return;
      }
      localStorage.setItem("cp_token", data.token);
      localStorage.setItem("cp_name", data.name);
      localStorage.setItem("cp_email", data.email);
      checkAuth();
    } catch(e) {
      errorDiv.style.display = "block";
      errorDiv.textContent = "Server connection error: " + e.message;
    }
  }

  function logout() {
    localStorage.removeItem("cp_token");
    localStorage.removeItem("cp_name");
    localStorage.removeItem("cp_email");
    checkAuth();
  }

  /* ── NAVIGATION ── */
  function goTo(id, el) {
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    el.classList.add('active');
    document.getElementById('page-' + id).classList.add('active');

    if (id === 'dashboard') {
      loadDashboardPools();
      loadLatestLogs();
    } else if (id === 'filepool') {
      loadFilesList();
    } else if (id === 'apikeys') {
      loadKeysList();
    } else if (id === 'provisioner') {
      loadProvisionedTables();
    } else if (id === 'topology') {
      loadSecretsList();
      loadSnapshotsList();
    } else if (id === 'analytics') {
      loadApiKeyAnalytics();
    } else if (id === 'compute') {
      loadComputeData();
    } else if (id === 'emails') {
      loadActiveEmailTab();
    }
  }

  /* ── DASHBOARD POOLS & FILES ── */
  async function loadDashboardPools() {
    try {
      const res = await fetch(`${API_BASE}/api/files/buckets`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const buckets = await res.json();
      if (!Array.isArray(buckets)) {
        throw new Error("Buckets response is not an array");
      }
      
      let gdriveLinked = false;
      try {
        const gdRes = await fetch(`${API_BASE}/api/storage/google/status`, { headers: getHeaders() });
        if (gdRes.ok) {
          const gdData = await gdRes.json();
          gdriveLinked = gdData.linked;
        }
      } catch (gde) {
        console.error("GDrive status error", gde);
      }

      // Fetch Quota
      let quotaLimit = 5368709120; // 5GB default
      let quotaUsage = 0;
      try {
        const quotaRes = await fetch(`${API_BASE}/api/files/quota`, { headers: getHeaders() });
        if (quotaRes.ok) {
          const qData = await quotaRes.json();
          quotaLimit = qData.limit;
          quotaUsage = qData.usage;
        }
      } catch (qe) {
        console.error("Quota fetch error", qe);
      }
      
      const poolSelect = document.getElementById("poolSelect");
      poolSelect.innerHTML = "";
      
      const poolsContainer = document.getElementById("dashboardPools");
      poolsContainer.innerHTML = "";

      buckets.forEach((bucket, idx) => {
        // Option
        const opt = document.createElement("option");
        opt.value = bucket.name;
        opt.textContent = bucket.name;
        poolSelect.appendChild(opt);

        // Display usage inside pool card
        let usageStr = "";
        let pct = 0;
        if (!gdriveLinked) {
          const usageMb = (quotaUsage / (1024 * 1024)).toFixed(2);
          const limitGb = (quotaLimit / (1024 * 1024 * 1024)).toFixed(1);
          pct = Math.min(100, ((quotaUsage / quotaLimit) * 100)).toFixed(1);
          usageStr = `${usageMb} MB / ${limitGb} GB (${pct}%)`;
        } else {
          usageStr = `LOCAL OVERFLOW ONLY`;
          pct = 0;
        }

        // Card
        const card = document.createElement("div");
        card.className = `pool-card ${idx === 0 ? 'active' : ''}`;
        card.innerHTML = `
          <div class="tag">JPA_POOL</div>
          <div class="name">${bucket.name}</div>
          <div class="drives">LOCAL STORAGE SUBPOOL</div>
          <div class="bar-wrap"><div class="bar" style="width:${pct}%"></div></div>
          <div class="sizes"><span>${usageStr}</span><span>CREATED AT ${new Date(bucket.createdAt).toLocaleDateString()}</span></div>
        `;
        card.onclick = () => selectPool(card, bucket.name);
        poolsContainer.appendChild(card);
      });

      // GDrive Card
      const gDriveCard = document.createElement("div");
      if (gdriveLinked) {
        const usageGb = (quotaUsage / (1024 * 1024 * 1024)).toFixed(2);
        const limitGb = (quotaLimit / (1024 * 1024 * 1024)).toFixed(1);
        const pct = Math.min(100, ((quotaUsage / quotaLimit) * 100)).toFixed(1);
        
        gDriveCard.className = "pool-card";
        gDriveCard.innerHTML = `
          <div class="tag">ACTIVE</div>
          <div class="name">GOOGLE DRIVE</div>
          <div class="drives" style="margin-top:6px">Route storage directly to your GDrive (CONNECTED).</div>
          <div class="bar-wrap"><div class="bar" style="width:${pct}%"></div></div>
          <div class="sizes"><span>${usageGb} GB / ${limitGb} GB (${pct}%)</span><span>CONNECTED</span></div>
        `;
      } else {
        gDriveCard.className = "pool-card";
        gDriveCard.style.borderStyle = "dashed";
        gDriveCard.innerHTML = `
          <div class="tag" style="border-color:#555;color:#555">LINK</div>
          <div class="name">LINK GOOGLE DRIVE</div>
          <div class="drives" style="margin-top:6px">Route storage directly to your GDrive.</div>
          <div style="margin-top:12px"><button class="btn btn-outline" style="padding:3px 8px;font-size:9px" onclick="initiateGDriveLink()">LINK ACCOUNT</button></div>
        `;
      }
      poolsContainer.appendChild(gDriveCard);
    } catch(e) {
      clog('consoleBox', `[ERROR] Loading pools: ${e.message}`, 'err');
    }
  }

  async function initiateGDriveLink() {
    try {
      const res = await fetch(`${API_BASE}/api/storage/google/auth-url`, { headers: getHeaders() });
      const data = await res.json();
      if (data.url) {
        window.location.href = data.url;
      } else {
        alert("Failed to generate authorization URL");
      }
    } catch(e) {
      alert("Error linking Google Drive: " + e.message);
    }
  }

  async function loadLatestLogs() {
    try {
      const res = await fetch(`${API_BASE}/api/files/logs`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      if (!res.ok) return;
      const logs = await res.json();
      if (!Array.isArray(logs)) return;
      
      const box = document.getElementById("consoleBox");
      box.innerHTML = "";
      logs.forEach(log => {
        const timeStr = new Date(log.createdAt).toLocaleTimeString();
        const line = document.createElement("div");
        line.className = "log-line";
        line.innerHTML = `<span class="log-ts">${timeStr}</span><span class="log-ok">[OK] ${log.details}</span>`;
        box.appendChild(line);
      });
      box.scrollTop = box.scrollHeight;
    } catch(e) {}
  }

  function selectPool(el, name) {
    document.querySelectorAll('.pool-card').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    document.getElementById('poolSelect').value = name;
    document.getElementById('activePanelPool').textContent = name;
  }

  let selectedFile = null;
  function handleFile(e) {
    selectedFile = e.target.files[0];
    if (!selectedFile) return;
    document.getElementById('fileId').value = selectedFile.name;
    clog('consoleBox', `[INFO] File selected: ${selectedFile.name} (${(selectedFile.size/1024).toFixed(1)} KB)`, 'info');
  }

  async function uploadFile() {
    if (!selectedFile) {
      alert("Please select a file first using browse dropzone!");
      return;
    }
    const badge = document.getElementById('statusBadge');
    badge.className = 'panel-badge warn'; badge.textContent = 'PUSHING...';
    document.getElementById('mStatus').textContent = '...';

    const formData = new FormData();
    formData.append("file", selectedFile);
    formData.append("bucket", document.getElementById("poolSelect").value);

    const token = getToken();
    const projectId = localStorage.getItem("cp_project_id");
    const headers = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    if (projectId) headers["X-Project-Id"] = projectId;

    const t = Date.now();
    try {
      const res = await fetch(`${API_BASE}/api/files/upload`, {
        method: "POST",
        headers: headers,
        body: formData
      });
      const data = await res.json();
      const ms = Date.now() - t;
      badge.className = 'panel-badge'; badge.textContent = 'ACTIVE';
      
      if (!res.ok) {
        document.getElementById('mStatus').textContent = 'ERROR';
        clog('consoleBox', `[ERR] Upload failed: ${data.error || 'Server error'}`, 'err');
        return;
      }
      document.getElementById('mStatus').textContent = 'OK';
      document.getElementById('mLatency').textContent = `${ms}ms`;
      clog('consoleBox', `[OK] File '${data.originalName}' saved. ID: ${data.id}`, 'ok');
      
      selectedFile = null;
      document.getElementById('fileId').value = "";
      loadLatestLogs();
    } catch (e) {
      badge.className = 'panel-badge'; badge.textContent = 'ERROR';
      clog('consoleBox', `[ERR] Network error: ${e.message}`, 'err');
    }
  }

  /* ── FILE POOL LISTING ── */
  async function loadFilesList() {
    try {
      const res = await fetch(`${API_BASE}/api/files`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const files = await res.json();
      if (!Array.isArray(files)) return;
      const tbody = document.getElementById("fileTable");
      tbody.innerHTML = "";

      files.forEach(f => {
        const sizeMb = (f.size / (1024 * 1024)).toFixed(2);
        const row = document.createElement("tr");
        row.innerHTML = `
          <td>${escapeHTML(f.originalName)}</td>
          <td>${escapeHTML(f.bucket.name)}</td>
          <td>${sizeMb} MB</td>
          <td><span class="chip active">${escapeHTML(f.extension || 'FILE')}</span></td>
          <td>${escapeHTML(new Date(f.createdAt).toLocaleString())}</td>
          <td><span class="chip">${f.public ? 'PUBLIC' : 'PRIVATE'}</span></td>
          <td>
            <button class="btn btn-outline btn-dl" style="padding:2px 8px;font-size:9px">DOWNLOAD</button>
            <button class="btn btn-outline btn-sh" style="padding:2px 8px;font-size:9px;margin-left:4px">SHARE</button>
          </td>
        `;
        row.querySelector('.btn-dl').onclick = () => downloadFile(f.id);
        row.querySelector('.btn-sh').onclick = () => shareFilePrompt(f.id, f.originalName);
        tbody.appendChild(row);
      });
    } catch(e) {
      console.error(e);
    }
  }

  async function downloadFile(id) {
    const token = getToken();
    try {
      const res = await fetch(`${API_BASE}/api/files/download/${id}`, {
        headers: token ? { "Authorization": `Bearer ${token}` } : {}
      });
      if (!res.ok) {
        alert("Failed to download file or unauthorized");
        return;
      }
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "file";
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch(e) {
      alert("Download error: " + e.message);
    }
  }

  /* ── DATABASE QUERY EXECUTION HUB ── */
  let selectedDBEngine = "H2";

  function selectActiveDB(dbName) {
    selectedDBEngine = dbName;
    document.getElementById("dbSelectionScreen").style.display = "none";
    document.getElementById("dbQueryWorkspace").style.display = "flex";
    
    document.getElementById("dbWorkspaceTitle").textContent = `DATABASE QUERY INTERFACE — ${dbName}`;
    document.getElementById("dbWorkspaceSub").textContent = `Execute queries directly against the connected ${dbName} instance.`;
    document.getElementById("dbBadgeStatus").textContent = `${dbName}_CONNECTED`;

    const editor = document.getElementById("sqlEditor");
    const label = document.getElementById("queryEditorLabel");
    
    // Clear previous headers/rows
    document.getElementById("queryHeaders").innerHTML = "";
    document.getElementById("queryRows").innerHTML = "";
    document.getElementById("dbLog").innerHTML = "";

    if (dbName === 'GRAPHQL') {
      label.textContent = "GRAPHQL QUERY EDITOR";
      editor.value = `query {\n  healthCheck\n}`;
    } else if (dbName === 'REDIS') {
      label.textContent = "REDIS COMMAND CONSOLE";
      editor.value = `PING`;
    } else {
      label.textContent = `SQL QUERY EDITOR (${dbName})`;
      editor.value = `SELECT id, email, name, role, active, created_at FROM users;`;
    }

    loadConnectionConfig(dbName);
  }

  function backToDBSelection() {
    document.getElementById("dbQueryWorkspace").style.display = "none";
    document.getElementById("dbSelectionScreen").style.display = "flex";
  }

  async function runActiveQuery() {
    const editorVal = document.getElementById("sqlEditor").value;
    const badge = document.getElementById('dbBadge');
    badge.className = 'panel-badge warn'; 
    badge.textContent = 'RUNNING...';
    document.getElementById('dbLog').innerHTML = `<div class="log-line"><span class="log-info">[INFO] Connecting and executing statement on ${selectedDBEngine}...</span></div>`;

    if (selectedDBEngine === 'GRAPHQL') {
      try {
        const res = await fetch(`${API_BASE}/graphql`, {
          method: "POST",
          headers: getHeaders({ "Content-Type": "application/json" }),
          body: JSON.stringify({ query: editorVal })
        });
        const result = await res.json();
        badge.className = 'panel-badge'; 
        badge.textContent = 'DONE';

        // Render GraphQL JSON response in results table
        const headersContainer = document.getElementById("queryHeaders");
        headersContainer.innerHTML = "<tr><th>GRAPHQL_RESPONSE_DATA</th></tr>";

        const rowsContainer = document.getElementById("queryRows");
        rowsContainer.innerHTML = `<tr><td style="font-family:var(--font); font-size:10px; white-space:pre-wrap; line-height:1.5;">${JSON.stringify(result.data || result, null, 2)}</td></tr>`;

        if (result.errors) {
          clog('dbLog', "GraphQL errors: " + JSON.stringify(result.errors), 'err');
        } else {
          clog('dbLog', "GraphQL query executed successfully.", 'ok');
        }
      } catch(e) {
        badge.className = 'panel-badge'; 
        badge.textContent = 'ERROR';
        clog('dbLog', "GraphQL Query failed: " + e.message, 'err');
      }
    } else if (selectedDBEngine === 'REDIS') {
      try {
        const res = await fetch(`${API_BASE}/api/console/redis/execute`, {
          method: "POST",
          headers: getHeaders(),
          body: JSON.stringify({ command: editorVal })
        });
        const result = await res.json();
        badge.className = 'panel-badge'; 
        badge.textContent = 'DONE';

        // Load headers
        const headersContainer = document.getElementById("queryHeaders");
        headersContainer.innerHTML = "";
        const rowTr = document.createElement("tr");
        result.columns.forEach(col => {
          const th = document.createElement("th");
          th.textContent = col;
          rowTr.appendChild(th);
        });
        headersContainer.appendChild(rowTr);

        // Load rows
        const rowsContainer = document.getElementById("queryRows");
        rowsContainer.innerHTML = "";
        result.rows.forEach(row => {
          const tr = document.createElement("tr");
          result.columns.forEach(col => {
            const td = document.createElement("td");
            td.textContent = row[col] !== undefined ? row[col] : "";
            tr.appendChild(td);
          });
          rowsContainer.appendChild(tr);
        });

        clog('dbLog', `[REDIS] ` + result.message, result.success ? 'ok' : 'err');
      } catch(e) {
        badge.className = 'panel-badge'; 
        badge.textContent = 'ERROR';
        clog('dbLog', `[REDIS] Execution failed: ` + e.message, 'err');
      }
    } else {
      // Relational SQL Engine Execution (H2, Postgres)
      try {
        const res = await fetch(`${API_BASE}/api/console/execute`, {
          method: "POST",
          headers: getHeaders(),
          body: JSON.stringify({ sql: editorVal })
        });
        const result = await res.json();
        badge.className = 'panel-badge'; 
        badge.textContent = 'DONE';

        // Load headers
        const headersContainer = document.getElementById("queryHeaders");
        headersContainer.innerHTML = "";
        const rowTr = document.createElement("tr");
        result.columns.forEach(col => {
          const th = document.createElement("th");
          th.textContent = col;
          rowTr.appendChild(th);
        });
        headersContainer.appendChild(rowTr);

        // Load rows
        const rowsContainer = document.getElementById("queryRows");
        rowsContainer.innerHTML = "";
        result.rows.forEach(row => {
          const tr = document.createElement("tr");
          result.columns.forEach(col => {
            const td = document.createElement("td");
            td.textContent = row[col] !== undefined ? row[col] : "";
            tr.appendChild(td);
          });
          rowsContainer.appendChild(tr);
        });

        clog('dbLog', `[${selectedDBEngine}] ` + result.message, result.success ? 'ok' : 'err');
      } catch(e) {
        badge.className = 'panel-badge'; 
        badge.textContent = 'ERROR';
        clog('dbLog', `[${selectedDBEngine}] Execution failed: ` + e.message, 'err');
      }
    }
  }

  /* ── VECTOR SEARCH ── */
  async function runVector() {
    const q = document.getElementById("vectorQuery").value;
    const box = document.getElementById("vecLog");
    box.innerHTML = `<div class="log-line"><span class="log-info">[INFO] Match terms in file index...</span></div>`;

    try {
      const res = await fetch(`${API_BASE}/api/vector/search?q=${encodeURIComponent(q)}`, {
        headers: getHeaders()
      });
      const results = await res.json();
      
      const resultsDiv = document.getElementById("vectorResults");
      resultsDiv.innerHTML = "";

      results.forEach(r => {
        const card = document.createElement("div");
        card.className = "result-card";
        card.innerHTML = `
          <div style="display:flex;align-items:center;margin-bottom:3px">
            <div class="rc-title">${r.name}</div>
            <div class="rc-score">${r.score} RELEVANCE</div>
          </div>
          <div class="rc-meta">
            <span>Pool: ${r.pool}</span>
            <span>Size: ${(r.size/(1024*1024)).toFixed(2)} MB</span>
            <span>Type: ${r.type}</span>
          </div>
        `;
        resultsDiv.appendChild(card);
      });

      clog('vecLog', `[OK] Search returned ${results.length} documents matching term criteria.`, 'ok');
    } catch(e) {
      clog('vecLog', `[ERR] Vector search query failed: ${e.message}`, 'err');
    }
  }

  /* ── API KEY GENERATION ── */
  async function loadKeysList() {
    try {
      const res = await fetch(`${API_BASE}/api/keys`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const keys = await res.json();
      if (!Array.isArray(keys)) return;
      const listDiv = document.getElementById("keysList");
      listDiv.innerHTML = "";

      keys.forEach(k => {
        const row = document.createElement("div");
        row.className = "key-row";
        row.style.display = "flex";
        row.style.justifyContent = "space-between";
        row.style.alignItems = "center";
        row.innerHTML = `
          <div style="display:flex;align-items:center;gap:16px;flex:1;min-width:0;">
            <div style="width:120px;flex-shrink:0;">
              <div class="key-name" style="font-weight:700;">${k.name}</div>
              <div style="font-size:8px;color:var(--muted);margin-top:2px">EXPIRES: ${k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'NEVER'}</div>
            </div>
            <div class="key-val" style="font-family:'JetBrains Mono';font-size:9px;color:var(--muted);flex:1;word-break:break-all;overflow:hidden;text-overflow:ellipsis;">${k.keyHash}</div>
            <div class="chip active" style="font-size:8px;flex-shrink:0;margin-left:8px;">${k.active ? 'ACTIVE' : 'REVOKED'}</div>
          </div>
          <button class="btn" onclick="deleteKey('${k.id}')" style="background:#ef5350;color:#fff;border:none;padding:4px 8px;font-size:9px;font-weight:700;cursor:pointer;margin-left:12px;flex-shrink:0;height:24px;line-height:1;">DELETE</button>
        `;
        listDiv.appendChild(row);
      });
    } catch(e) {
      console.error(e);
    }
  }

  async function deleteKey(keyId) {
    if (!confirm("Are you sure you want to delete this API key? This cannot be undone.")) return;
    try {
      const res = await fetch(`${API_BASE}/api/keys/${keyId}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (res.ok) {
        loadKeysList();
      } else {
        const err = await res.json();
        alert("Failed to delete key: " + (err.error || res.statusText));
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function generateKey() {
    const name = document.getElementById("newKeyName").value || "service-key";
    try {
      const res = await fetch(`${API_BASE}/api/keys/generate`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ name, description: "Console generated key", daysToLive: 30 })
      });
      const data = await res.json();
      const box = document.getElementById("newKeyBox");
      box.style.display = "block";
      box.innerHTML = `
        <div style="font-size:9px;color:var(--muted);margin-bottom:4px;letter-spacing:.08em">NEW API KEY — COPY NOW, SHOWN ONCE</div>
        <div style="font-weight:700;font-size:11px;background:#f9f9f9;padding:6px;border:1px solid #000">${data.apiKey}</div>
      `;
      loadKeysList();
    } catch(e) {
      alert("Key generation error: " + e.message);
    }
  }

  async function loadApiKeyAnalytics() {
    try {
      // 1. Fetch summary from the general request analytics API
      const summaryRes = await fetch(`${API_BASE}/api/analytics/summary`, { headers: getHeaders() });
      if (!summaryRes.ok) return;
      const summary = await summaryRes.json();

      // Render stats card metrics
      document.getElementById("analyticsTotalRequests").innerText = summary.totalRequests || 0;
      document.getElementById("analyticsAverageLatency").innerText = Math.round(summary.averageLatencyMs || 0) + " ms";
      document.getElementById("analyticsSuccessRate").innerText = Math.round(summary.successRate || 0) + "%";
      document.getElementById("analyticsErrorRate").innerText = summary.errorCount || 0;

      // 2. Fetch logs from the general request analytics API
      const logsRes = await fetch(`${API_BASE}/api/analytics/logs`, { headers: getHeaders() });
      if (!logsRes.ok) return;
      const logs = await logsRes.json();

      // Fill logs stream
      const logsStream = document.getElementById("analyticsLogsStream");
      logsStream.innerHTML = "";
      if (!logs || logs.length === 0) {
        logsStream.innerHTML = `<div style="color:var(--muted);padding:10px;text-align:center">No request logs recorded yet.</div>`;
      } else {
        logs.forEach(l => {
          const timeStr = new Date(l.timestamp).toLocaleTimeString();
          const row = document.createElement("div");
          row.style.borderBottom = "1px solid var(--border2)";
          row.style.padding = "6px 8px";
          row.style.display = "flex";
          row.style.justifyContent = "space-between";
          row.style.alignItems = "center";
          
          let statusColor = "#66bb6a"; // green for 2xx/3xx
          if (l.statusCode >= 400 && l.statusCode < 500) statusColor = "#ffa726"; // orange
          if (l.statusCode >= 500) statusColor = "#ef5350"; // red

          row.innerHTML = `
            <div style="display:flex;align-items:center;gap:8px;min-width:0;flex:1;">
              <span style="font-weight:700;color:${l.requestMethod === 'GET' ? '#42a5f5' : '#ab47bc'};width:40px;flex-shrink:0;">${l.requestMethod}</span>
              <span style="color:var(--text);word-break:break-all;text-overflow:ellipsis;overflow:hidden;white-space:nowrap;" title="${escapeHTML(l.requestPath)}">${escapeHTML(l.requestPath)}</span>
            </div>
            <div style="display:flex;align-items:center;gap:12px;flex-shrink:0;margin-left:8px;">
              <span style="color:var(--muted);font-size:8px;">${l.durationMs}ms</span>
              <span style="color:var(--muted);font-size:8px;">${escapeHTML(l.ipAddress || 'local')}</span>
              <span style="background:${statusColor};color:#fff;padding:1px 4px;font-size:8px;font-weight:700;">${l.statusCode}</span>
              <span style="color:var(--label);font-size:8px;">${timeStr}</span>
            </div>
          `;
          logsStream.appendChild(row);
        });
      }

      // 3. Render Status Code Distribution in "Requests by Status Code" panel
      const byKeyList = document.getElementById("analyticsByKeyList");
      byKeyList.innerHTML = "";
      if (!summary.statusDistribution || Object.keys(summary.statusDistribution).length === 0) {
        byKeyList.innerHTML = `<div style="color:var(--muted);padding:10px;text-align:center">No metrics available.</div>`;
      } else {
        Object.entries(summary.statusDistribution).forEach(([status, count]) => {
          const row = document.createElement("div");
          row.style.display = "flex";
          row.style.justifyContent = "space-between";
          row.style.padding = "8px 12px";
          row.style.borderBottom = "1px solid var(--border2)";
          row.innerHTML = `
            <div style="font-weight:700;">${status}</div>
            <div style="font-family:'JetBrains Mono';font-weight:700;">${count} hits</div>
          `;
          byKeyList.appendChild(row);
        });
      }

      // 4. Render Requests by Path in "Requests by Path" panel
      const byEndList = document.getElementById("analyticsByEndpointList");
      byEndList.innerHTML = "";
      if (!summary.topPaths || summary.topPaths.length === 0) {
        byEndList.innerHTML = `<div style="color:var(--muted);padding:10px;text-align:center">No metrics available.</div>`;
      } else {
        summary.topPaths.forEach(tp => {
          const row = document.createElement("div");
          row.style.display = "flex";
          row.style.justifyContent = "space-between";
          row.style.padding = "8px 12px";
          row.style.borderBottom = "1px solid var(--border2)";
          row.innerHTML = `
            <div style="font-family:'JetBrains Mono';color:var(--muted);word-break:break-all;max-width:70%;" title="${escapeHTML(tp.path)}">${escapeHTML(tp.path)}</div>
            <div style="font-weight:700;flex-shrink:0;">${tp.count} hits</div>
          `;
          byEndList.appendChild(row);
        });
      }

    } catch(e) {
      console.error("Failed to load request analytics:", e);
    }
  }

  /* ── SHARED LOG UTIL ── */
  function clog(boxId, msg, type) {
    const box = document.getElementById(boxId); if (!box) return;
    const now = new Date();
    const ts = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`;
    const line = document.createElement('div'); line.className = 'log-line';
    
    const tsSpan = document.createElement('span');
    tsSpan.className = 'log-ts';
    tsSpan.textContent = ts;
    
    const msgSpan = document.createElement('span');
    msgSpan.className = 'log-' + type;
    msgSpan.textContent = msg;
    
    line.appendChild(tsSpan);
    line.appendChild(msgSpan);
    box.appendChild(line); box.scrollTop = box.scrollHeight;
  }

  /* ── S3 FILE SHARING ── */
  let currentSharingFileId = null;

  function shareFilePrompt(fileId, filename) {
    currentSharingFileId = fileId;
    document.getElementById("shareModalTitle").textContent = `SHARE FILE: ${filename}`;
    document.getElementById("shareEmail").value = "";
    document.getElementById("shareExpiry").value = "";
    document.getElementById("shareResultBox").style.display = "none";
    document.getElementById("shareModal").style.display = "flex";
  }

  function closeShareModal() {
    document.getElementById("shareModal").style.display = "none";
  }

  async function submitShare() {
    const email = document.getElementById("shareEmail").value.trim();
    const expiryStr = document.getElementById("shareExpiry").value.trim();
    const expiry = expiryStr ? parseInt(expiryStr) : null;

    if (expiryStr && isNaN(expiry)) {
      alert("Expiry time must be a number of hours");
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/files/${currentSharingFileId}/share`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ sharedWithEmail: email, expiryHours: expiry })
      });
      const data = await res.json();
      if (!res.ok) {
        alert("Sharing failed: " + (data.error || "Server error"));
        return;
      }

      // Build absolute download URL
      const shareUrl = `${window.location.origin}${API_BASE}/api/files/shared/${data.token}`;
      document.getElementById("shareLinkVal").value = shareUrl;
      document.getElementById("shareResultBox").style.display = "flex";
    } catch(e) {
      alert("Sharing error: " + e.message);
    }
  }

  function copyShareLink() {
    const copyText = document.getElementById("shareLinkVal");
    copyText.select();
    copyText.setSelectionRange(0, 99999);
    navigator.clipboard.writeText(copyText.value);
    alert("Share link copied to clipboard!");
  }

  /* ── DATABASE PROVISIONER ── */
  let currentViewingTableId = null;
  let currentViewingTableFields = [];
  let currentProvMode = "visual";

  function setProvMode(mode) {
    currentProvMode = mode;
    const tabVisual = document.getElementById("tabVisualMode");
    const tabJson = document.getElementById("tabJsonMode");
    const formVisual = document.getElementById("visualProvForm");
    const formJson = document.getElementById("jsonProvForm");

    if (mode === 'visual') {
      tabVisual.classList.add("active");
      tabJson.classList.remove("active");
      formVisual.style.display = "flex";
      formJson.style.display = "none";
    } else {
      tabVisual.classList.remove("active");
      tabJson.classList.add("active");
      formVisual.style.display = "none";
      formJson.style.display = "flex";
    }
  }

  function addSchemaFieldRow(name = '', type = 'VARCHAR', required = false) {
    const container = document.getElementById("schemaFieldsContainer");
    const row = document.createElement("div");
    row.style.display = "flex";
    row.style.gap = "6px";
    row.style.alignItems = "center";
    row.innerHTML = `
      <input type="text" placeholder="field_name" value="${name}" class="field-name-input" style="flex:1;" />
      <select class="field-type-select" style="width:100px;">
        <option value="VARCHAR" ${type === 'VARCHAR' ? 'selected' : ''}>VARCHAR</option>
        <option value="INTEGER" ${type === 'INTEGER' ? 'selected' : ''}>INTEGER</option>
        <option value="BOOLEAN" ${type === 'BOOLEAN' ? 'selected' : ''}>BOOLEAN</option>
        <option value="DOUBLE" ${type === 'DOUBLE' ? 'selected' : ''}>DOUBLE</option>
        <option value="TEXT" ${type === 'TEXT' ? 'selected' : ''}>TEXT</option>
      </select>
      <label style="display:flex; align-items:center; gap:2px; font-size:9px; cursor:pointer;">
        <input type="checkbox" class="field-required-checkbox" ${required ? 'checked' : ''} /> REQ
      </label>
      <button type="button" class="btn btn-outline" style="padding:4px 8px; font-weight:bold;" onclick="this.parentElement.remove()">×</button>
    `;
    container.appendChild(row);
  }

  async function provisionTable() {
    let payload = {};

    if (currentProvMode === 'json') {
      const jsonText = document.getElementById("jsonSchemaEditor").value.trim();
      try {
        payload = JSON.parse(jsonText);
      } catch (e) {
        alert("Invalid JSON format in Schema Editor: " + e.message);
        return;
      }

      if (!payload.name) {
        alert("JSON Schema must specify table 'name'.");
        return;
      }
      if (!payload.fields || !Array.isArray(payload.fields) || payload.fields.length === 0) {
        alert("JSON Schema must contain at least one column in 'fields'.");
        return;
      }
    } else {
      // Visual mode
      const name = document.getElementById("provTableName").value.trim();
      const displayName = document.getElementById("provTableDisplay").value.trim();
      const description = document.getElementById("provTableDesc").value.trim();

      if (!name) {
        alert("Table physical name is required.");
        return;
      }

      const fieldRows = document.querySelectorAll("#schemaFieldsContainer > div");
      const fields = [];
      let hasError = false;

      fieldRows.forEach(row => {
        const fieldName = row.querySelector(".field-name-input").value.trim();
        const fieldType = row.querySelector(".field-type-select").value;
        const isRequired = row.querySelector(".field-required-checkbox").checked;

        if (!fieldName) {
          alert("All fields must have a name.");
          hasError = true;
          return;
        }
        fields.push({ fieldName, fieldType, isRequired });
      });

      if (hasError) return;
      if (fields.length === 0) {
        alert("At least one field schema must be provided.");
        return;
      }

      payload = { name, displayName, description, fields };
    }

    try {
      const res = await fetch(`${API_BASE}/api/v1/db/tables`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) {
        alert("Provisioning failed: " + (data.error || "Server error"));
        return;
      }

      alert(`Table '${data.displayName}' provisioned successfully.`);
      
      // Clear visual inputs
      document.getElementById("provTableName").value = "";
      document.getElementById("provTableDisplay").value = "";
      document.getElementById("provTableDesc").value = "";
      document.getElementById("schemaFieldsContainer").innerHTML = "";

      loadProvisionedTables();
    } catch(e) {
      alert("Provisioning error: " + e.message);
    }
  }

  async function loadProvisionedTables() {
    try {
      const res = await fetch(`${API_BASE}/api/v1/db/tables`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const tables = await res.json();
      if (!Array.isArray(tables)) return;

      document.getElementById("provTablesCount").textContent = `${tables.length} TABLES`;
      const container = document.getElementById("activeTablesList");
      container.innerHTML = "";

      tables.forEach(t => {
        const item = document.createElement("div");
        item.style.border = "1px solid var(--border2)";
        item.style.padding = "10px 12px";
        item.style.display = "flex";
        item.style.flexDirection = "column";
        item.style.gap = "6px";
        item.innerHTML = `
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <div style="font-weight:600; font-size:11px;">${escapeHTML(t.displayName)} <span style="font-weight:normal; font-size:9px; color:var(--muted)">(${escapeHTML(t.name)})</span></div>
            <div style="display:flex; gap:6px;">
              <button class="btn btn-view" style="padding:2px 8px; font-size:8px">VIEW RECORDS</button>
              <button class="btn btn-outline btn-del" style="padding:2px 8px; font-size:8px">DELETE</button>
            </div>
          </div>
          <div style="font-size:9px; color:var(--muted); line-height:1.4;">${escapeHTML(t.description) || 'No description.'}</div>
        `;
        item.querySelector('.btn-view').onclick = () => viewTableRecords(t.id, t.displayName);
        item.querySelector('.btn-del').onclick = () => deleteTable(t.id, t.displayName);
        container.appendChild(item);
      });
    } catch(e) {
      console.error("Error loading provisioned tables", e);
    }
  }

  async function deleteTable(tableId, displayName) {
    if (!confirm(`Are you sure you want to drop/delete table '${displayName}'? All records will be permanently lost.`)) {
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/v1/db/tables/${tableId}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (!res.ok) {
        const data = await res.json();
        alert("Failed to delete table: " + (data.error || "Server error"));
        return;
      }

      alert("Table deleted successfully.");
      if (currentViewingTableId === tableId) {
        closeRecordViewer();
      }
      loadProvisionedTables();
    } catch(e) {
      alert("Error deleting table: " + e.message);
    }
  }

  async function viewTableRecords(tableId, displayName) {
    currentViewingTableId = tableId;
    document.getElementById("selectedTableTitle").textContent = `TABLE RECORDS: ${displayName}`;
    hideAddRecordForm();

    try {
      // Get fields
      const fRes = await fetch(`${API_BASE}/api/v1/db/tables/${tableId}/fields`, { headers: getHeaders() });
      const fields = await fRes.json();
      currentViewingTableFields = fields;

      // Get records
      const rRes = await fetch(`${API_BASE}/api/v1/db/tables/${tableId}/records`, { headers: getHeaders() });
      const records = await rRes.json();

      // Render Headers
      const headersContainer = document.getElementById("recordTableHeaders");
      headersContainer.innerHTML = "";
      const headerTr = document.createElement("tr");
      
      const idTh = document.createElement("th");
      idTh.textContent = "ID";
      headerTr.appendChild(idTh);

      fields.forEach(field => {
        const th = document.createElement("th");
        th.textContent = `${field.fieldName.toUpperCase()} (${field.fieldType})`;
        headerTr.appendChild(th);
      });

      const actTh = document.createElement("th");
      actTh.textContent = "ACTIONS";
      headerTr.appendChild(actTh);
      headersContainer.appendChild(headerTr);

      // Render Body
      const bodyContainer = document.getElementById("recordTableBody");
      bodyContainer.innerHTML = "";

      if (records.length === 0) {
        const tr = document.createElement("tr");
        tr.innerHTML = `<td colspan="${fields.length + 2}" style="text-align:center; color:var(--muted); padding:16px;">No records found.</td>`;
        bodyContainer.appendChild(tr);
      } else {
        records.forEach(row => {
          const tr = document.createElement("tr");
          
          const idTd = document.createElement("td");
          idTd.textContent = row.ID || row.id || "";
          tr.appendChild(idTd);

          fields.forEach(field => {
            const td = document.createElement("td");
            const val = row[field.fieldName] !== undefined ? row[field.fieldName] : row[field.fieldName.toUpperCase()];
            td.textContent = val !== undefined && val !== null ? val : "";
            tr.appendChild(td);
          });

          const actTd = document.createElement("td");
          const recordId = row.ID || row.id;
          actTd.innerHTML = `<button class="btn btn-outline" style="padding:2px 6px; font-size:8px;" onclick="deleteRecord('${recordId}')">DELETE</button>`;
          tr.appendChild(actTd);
          bodyContainer.appendChild(tr);
        });
      }

      document.getElementById("tableRecordViewerPanel").style.display = "flex";
      document.getElementById("tableRecordViewerPanel").scrollIntoView({ behavior: 'smooth' });
    } catch(e) {
      alert("Error loading table details: " + e.message);
    }
  }

  function closeRecordViewer() {
    document.getElementById("tableRecordViewerPanel").style.display = "none";
    currentViewingTableId = null;
    currentViewingTableFields = [];
  }

  function showAddRecordForm() {
    const container = document.getElementById("dynamicRecordFields");
    container.innerHTML = "";

    currentViewingTableFields.forEach(field => {
      const fieldDiv = document.createElement("div");
      fieldDiv.className = "field";
      
      const label = document.createElement("div");
      label.className = "field-label";
      label.textContent = `${field.fieldName.toUpperCase()} (${field.fieldType})${field.required ? ' *' : ''}`;
      fieldDiv.appendChild(label);

      if (field.fieldType === 'BOOLEAN') {
        const select = document.createElement("select");
        select.className = "record-input";
        select.dataset.field = field.fieldName;
        select.innerHTML = `
          <option value="true">TRUE / YES</option>
          <option value="false" selected>FALSE / NO</option>
        `;
        fieldDiv.appendChild(select);
      } else if (field.fieldType === 'TEXT') {
        const textarea = document.createElement("textarea");
        textarea.className = "record-input";
        textarea.dataset.field = field.fieldName;
        textarea.rows = 2;
        textarea.placeholder = `Enter text content...`;
        fieldDiv.appendChild(textarea);
      } else {
        const input = document.createElement("input");
        input.type = "text";
        input.className = "record-input";
        input.dataset.field = field.fieldName;
        input.placeholder = field.fieldType === 'INTEGER' ? 'e.g. 123' : field.fieldType === 'DOUBLE' ? 'e.g. 99.9' : 'Enter value...';
        fieldDiv.appendChild(input);
      }

      container.appendChild(fieldDiv);
    });

    document.getElementById("addRecordFormWrap").style.display = "flex";
  }

  function hideAddRecordForm() {
    document.getElementById("addRecordFormWrap").style.display = "none";
  }

  async function submitNewRecord() {
    const inputs = document.querySelectorAll("#dynamicRecordFields .record-input");
    const payload = {};

    inputs.forEach(input => {
      const field = input.dataset.field;
      let val = input.value;
      payload[field] = val;
    });

    try {
      const res = await fetch(`${API_BASE}/api/v1/db/tables/${currentViewingTableId}/records`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) {
        alert("Failed to save record: " + (data.error || "Server error"));
        return;
      }

      hideAddRecordForm();
      const displayTitle = document.getElementById("selectedTableTitle").textContent.replace("TABLE RECORDS: ", "");
      viewTableRecords(currentViewingTableId, displayTitle);
    } catch(e) {
      alert("Error saving record: " + e.message);
    }
  }

  async function deleteRecord(recordId) {
    if (!confirm("Are you sure you want to delete this record?")) {
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/v1/db/tables/${currentViewingTableId}/records/${recordId}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (!res.ok) {
        const data = await res.json();
        alert("Failed to delete record: " + (data.error || "Server error"));
        return;
      }

      const displayTitle = document.getElementById("selectedTableTitle").textContent.replace("TABLE RECORDS: ", "");
      viewTableRecords(currentViewingTableId, displayTitle);
    } catch(e) {
      alert("Error deleting record: " + e.message);
    }
  }

  /* ── PROJECT SWITCHER & CONFIGS ── */
  async function loadProjectsList() {
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects`, { headers: getHeaders() });
      if (!res.ok) return;
      const projects = await res.json();
      if (!Array.isArray(projects)) return;

      const selector = document.getElementById("projectSelector");
      selector.innerHTML = "";
      projects.forEach(p => {
        const opt = document.createElement("option");
        opt.value = p.id;
        opt.textContent = p.name;
        selector.appendChild(opt);
      });

      let activeId = localStorage.getItem("cp_project_id");
      if (!activeId && projects.length > 0) {
        activeId = projects[0].id;
        localStorage.setItem("cp_project_id", activeId);
      }
      if (activeId) {
        selector.value = activeId;
      }
    } catch(e) {
      console.error("Error loading projects: ", e);
    }
  }

  function switchProject(projectId) {
    localStorage.setItem("cp_project_id", projectId);
    
    // Reload active tab state
    const activeNav = document.querySelector(".nav-item.active");
    if (activeNav) {
      const pageId = activeNav.dataset.page;
      goTo(pageId, activeNav);
    }
  }

  function openNewProjectPrompt() {
    document.getElementById("newProjName").value = "";
    document.getElementById("newProjDesc").value = "";
    document.getElementById("projectModal").style.display = "flex";
  }

  function closeProjectModal() {
    document.getElementById("projectModal").style.display = "none";
  }

  async function submitNewProject() {
    const name = document.getElementById("newProjName").value.trim();
    const description = document.getElementById("newProjDesc").value.trim();
    if (!name) {
      alert("Project name is required");
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ name, description })
      });
      const data = await res.json();
      if (!res.ok) {
        alert("Failed to create project: " + (data.error || "Server error"));
        return;
      }
      closeProjectModal();
      await loadProjectsList();
      switchProject(data.id);
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  /* ── SECRETS VAULT ── */
  async function loadSecretsList() {
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/secrets`, { headers: getHeaders() });
      if (!res.ok) return;
      const secrets = await res.json();
      const tbody = document.getElementById("secretsTableBody");
      tbody.innerHTML = "";

      if (secrets.length === 0) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:var(--muted)">No secrets set for this project.</td></tr>`;
      } else {
        secrets.forEach(s => {
          const row = document.createElement("tr");
          row.innerHTML = `
            <td style="font-weight:600">${s.secretKey}</td>
            <td style="font-family:var(--font); color:var(--muted)">•••••••• (Base64 Scoped)</td>
            <td>
              <button class="btn btn-outline" style="padding:2px 8px; font-size:8px" onclick="deleteSecret('${s.id}')">DELETE</button>
            </td>
          `;
          tbody.appendChild(row);
        });
      }
    } catch(e) {
      console.error(e);
    }
  }

  async function saveSecret() {
    const key = document.getElementById("secretKeyInput").value.trim();
    const value = document.getElementById("secretValueInput").value;
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;
    if (!key || !value) {
      alert("Key and value are both required");
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/secrets`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ key, value })
      });
      if (!res.ok) {
        const data = await res.json();
        alert("Failed to save secret: " + (data.error || "Server error"));
        return;
      }
      document.getElementById("secretKeyInput").value = "";
      document.getElementById("secretValueInput").value = "";
      loadSecretsList();
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function deleteSecret(secretId) {
    if (!confirm("Are you sure you want to delete this secret variable?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/secrets/${secretId}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (!res.ok) {
        alert("Delete failed");
        return;
      }
      loadSecretsList();
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  /* ── SNAPSHOTS TIMELINE ── */
  async function loadSnapshotsList() {
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/snapshots`, { headers: getHeaders() });
      if (!res.ok) return;
      const snapshots = await res.json();
      const tbody = document.getElementById("snapshotsTableBody");
      tbody.innerHTML = "";

      if (snapshots.length === 0) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align:center; color:var(--muted)">No configuration snapshots taken.</td></tr>`;
      } else {
        snapshots.forEach(s => {
          const row = document.createElement("tr");
          row.innerHTML = `
            <td style="font-weight:600">${s.name}</td>
            <td>${new Date(s.createdAt).toLocaleString()}</td>
            <td>
              <button class="btn" style="padding:2px 8px; font-size:8px" onclick="restoreSnapshot('${s.id}')">RESTORE</button>
              <button class="btn btn-outline" style="padding:2px 8px; font-size:8px; margin-left:4px;" onclick="deleteSnapshot('${s.id}')">DELETE</button>
            </td>
          `;
          tbody.appendChild(row);
        });
      }
    } catch(e) {
      console.error(e);
    }
  }

  async function createSnapshot() {
    const name = document.getElementById("snapshotNameInput").value.trim();
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;
    if (!name) {
      alert("Snapshot name is required");
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/snapshots`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ name })
      });
      if (!res.ok) {
        const data = await res.json();
        alert("Failed to take snapshot: " + (data.error || "Server error"));
        return;
      }
      document.getElementById("snapshotNameInput").value = "";
      loadSnapshotsList();
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function restoreSnapshot(snapshotId) {
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;
    if (!confirm("WARNING: Restoring this snapshot will drop all current dynamic physical schemas and restore variables and connections. Proceed?")) return;
    
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/snapshots/${snapshotId}/restore`, {
        method: "POST",
        headers: getHeaders()
      });
      if (!res.ok) {
        const data = await res.json();
        alert("Restoration failed: " + (data.error || "Server error"));
        return;
      }
      alert("Snapshot topology rolled back successfully!");
      // Reload active tab state
      const activeNav = document.querySelector(".nav-item.active");
      if (activeNav) {
        const pageId = activeNav.dataset.page;
        goTo(pageId, activeNav);
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function deleteSnapshot(snapshotId) {
    if (!confirm("Delete this snapshot permanently?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/snapshots/${snapshotId}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (!res.ok) {
        alert("Delete failed");
        return;
      }
      loadSnapshotsList();
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  /* ── DATABASE / REDIS CONNECTIONS CONFIGS ── */
  let activeConnId = null;

  async function loadConnectionConfig(dbEngine) {
    const configBox = document.getElementById("dbConnectionConfigBox");
    const db = dbEngine.toUpperCase();
    
    if (db === 'H2' || db === 'GRAPHQL') {
      configBox.style.display = "none";
      activeConnId = null;
      return;
    }

    configBox.style.display = "flex";
    document.getElementById("configBoxTitle").textContent = `${db} INSTANCE CONNECTION SETTINGS`;

    // Configure form views
    if (db === 'REDIS') {
      document.getElementById("connDatabaseRow").style.display = "none";
      document.getElementById("connAuthRow").innerHTML = `
        <div class="field"><div class="field-label">PASSWORD (OPTIONAL)</div><input type="password" id="connPassword" placeholder="••••••••" /></div>
      `;
      // Set defaults
      document.getElementById("connHost").value = "localhost";
      document.getElementById("connPort").value = "6379";
      document.getElementById("connActive").parentElement.style.display = "none"; // Redis console is active by default
    } else {
      document.getElementById("connDatabaseRow").style.display = "flex";
      document.getElementById("connAuthRow").innerHTML = `
        <div class="field"><div class="field-label">USERNAME</div><input type="text" id="connUser" placeholder="postgres" /></div>
        <div class="field"><div class="field-label">PASSWORD</div><input type="password" id="connPassword" placeholder="••••••••" /></div>
      `;
      document.getElementById("connHost").value = "localhost";
      document.getElementById("connPort").value = "5432";
      document.getElementById("connDatabase").value = "postgres";
      document.getElementById("connUser").value = "postgres";
      document.getElementById("connActive").checked = false;
      document.getElementById("connActive").parentElement.style.display = "flex";
    }

    // Load from backend
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;

    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/connections`, { headers: getHeaders() });
      if (!res.ok) return;
      const connections = await res.json();
      const match = connections.find(c => c.dbType === db);
      if (match) {
        activeConnId = match.id;
        document.getElementById("connHost").value = match.host;
        document.getElementById("connPort").value = match.port;
        document.getElementById("connPassword").value = match.password || "";
        
        if (db === 'POSTGRESQL') {
          document.getElementById("connDatabase").value = match.databaseName || "postgres";
          document.getElementById("connUser").value = match.username || "postgres";
          document.getElementById("connActive").checked = match.active;
        }
      } else {
        activeConnId = null;
      }
    } catch(e) {
      console.error("Failed to load connection: ", e);
    }
  }

  async function testDBConnection() {
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;

    const payload = {
      dbType: selectedDBEngine.toUpperCase(),
      host: document.getElementById("connHost").value.trim(),
      port: parseInt(document.getElementById("connPort").value) || 0,
      password: document.getElementById("connPassword").value
    };

    if (payload.dbType === 'POSTGRESQL') {
      payload.databaseName = document.getElementById("connDatabase").value.trim();
      payload.username = document.getElementById("connUser").value.trim();
    }

    clog('dbLog', `[INFO] Initiating connection test to ${payload.dbType} on ${payload.host}:${payload.port}...`, 'info');

    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/connections/test`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (data.success) {
        alert("Connection test successful!");
        clog('dbLog', `[OK] Test connection to ${payload.dbType} succeeded.`, 'ok');
      } else {
        alert("Connection test failed: " + data.error);
        clog('dbLog', `[ERR] Test connection to ${payload.dbType} failed: ` + data.error, 'err');
      }
    } catch(e) {
      alert("Error testing connection: " + e.message);
    }
  }

  async function saveDBConnection() {
    const projectId = localStorage.getItem("cp_project_id");
    if (!projectId) return;

    const payload = {
      dbType: selectedDBEngine.toUpperCase(),
      host: document.getElementById("connHost").value.trim(),
      port: parseInt(document.getElementById("connPort").value) || 0,
      password: document.getElementById("connPassword").value,
      active: true
    };

    if (payload.dbType === 'POSTGRESQL') {
      payload.databaseName = document.getElementById("connDatabase").value.trim();
      payload.username = document.getElementById("connUser").value.trim();
      payload.active = document.getElementById("connActive").checked;
    }

    try {
      const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/connections`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const data = await res.json();
        alert("Failed to save connection: " + (data.error || "Server error"));
        return;
      }
      alert("Connection configuration saved and applied!");
      clog('dbLog', `[OK] Saved and applied connection settings for ${payload.dbType}.`, 'ok');
      loadConnectionConfig(selectedDBEngine);
    } catch(e) {
      alert("Error saving connection: " + e.message);
    }
  }

  // Check GDrive connection status in URL
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get("gdrive") === "connected") {
    alert("Google Drive linked successfully!");
    window.history.replaceState({}, document.title, window.location.pathname);
  } else if (urlParams.get("gdrive") === "error") {
    alert("Failed to link Google Drive: " + urlParams.get("message"));
    window.history.replaceState({}, document.title, window.location.pathname);
  }

  /* ── PaaS COMPUTE PLATFORM OPERATIONS ── */
  let selectedComputeType = 'hub';
  let activeTestFunctionId = null;
  let activeTestFunctionName = null;
  let activeContainerId = null;
  let activeContainerName = null;
  let containerLogTimer = null;

  function loadComputeData() {
    backToComputeSelection();
  }

  function selectComputeOption(type) {
    selectedComputeType = type;
    document.getElementById('computeSelectionScreen').style.display = 'none';
    document.getElementById('staticConsole').style.display = 'none';
    document.getElementById('serverlessConsole').style.display = 'none';
    document.getElementById('containerConsole').style.display = 'none';

    if (type === 'static') {
      document.getElementById('staticConsole').style.display = 'flex';
      loadBucketsForStatic();
      loadStaticSites();
    } else if (type === 'serverless') {
      document.getElementById('serverlessConsole').style.display = 'flex';
      loadServerless();
    } else if (type === 'container') {
      document.getElementById('containerConsole').style.display = 'flex';
      loadContainers();
      startContainerLogPolling();
    }
  }

  function backToComputeSelection() {
    selectedComputeType = 'hub';
    document.getElementById('computeSelectionScreen').style.display = 'flex';
    document.getElementById('staticConsole').style.display = 'none';
    document.getElementById('serverlessConsole').style.display = 'none';
    document.getElementById('containerConsole').style.display = 'none';
    stopContainerLogPolling();
  }

  /* ─ Static Site Hosting ─ */
  async function loadBucketsForStatic() {
    try {
      const res = await fetch(`${API_BASE}/api/files/buckets`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const buckets = await res.json();
      const select = document.getElementById('staticBucketSelect');
      select.innerHTML = '';
      if (Array.isArray(buckets)) {
        buckets.forEach(b => {
          const opt = document.createElement('option');
          opt.value = b.name;
          opt.textContent = `${b.name} (${b.description || 'no description'})`;
          select.appendChild(opt);
        });
      }
    } catch(e) {
      clog('staticDeploymentsList', `[ERR] Failed to load buckets: ${e.message}`, 'error');
    }
  }

  function updateDomainPlaceholder() {
    const type = document.getElementById('staticDomainType').value;
    const domainInput = document.getElementById('staticDomain');
    if (type === 'web3') {
      domainInput.placeholder = 'my-project.cloudpool.eth';
    } else {
      domainInput.placeholder = 'my-awesome-app.cloudpool.dev';
    }
  }

  async function deployStaticSite() {
    const name = document.getElementById('staticName').value.trim();
    const bucketName = document.getElementById('staticBucketSelect').value;
    let domain = document.getElementById('staticDomain').value.trim();
    const type = document.getElementById('staticDomainType').value;

    if (!name || !domain) {
      alert("Name and Domain are required fields.");
      return;
    }

    if (type === 'web3' && !domain.endsWith('.eth')) {
      domain = domain + '.cloudpool.eth';
    } else if (type === 'web2' && !domain.endsWith('.dev')) {
      domain = domain + '.cloudpool.dev';
    }

    try {
      const res = await fetch(`${API_BASE}/api/compute/static`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ name, bucketName, domain })
      });
      if (!res.ok) {
        const err = await res.json();
        alert("Failed to deploy static site: " + (err.error || "Server error"));
        return;
      }
      alert("Static site successfully deployed!");
      document.getElementById('staticName').value = '';
      document.getElementById('staticDomain').value = '';
      loadStaticSites();
    } catch(e) {
      alert("Error deploying static site: " + e.message);
    }
  }

  async function loadStaticSites() {
    const listDiv = document.getElementById('staticDeploymentsList');
    listDiv.innerHTML = '<div style="color:var(--text-muted)">Loading deployments...</div>';

    try {
      const res = await fetch(`${API_BASE}/api/compute/static`, { headers: getHeaders() });
      if (!res.ok) {
        listDiv.innerHTML = '<div style="color:var(--text-accent)">Failed to fetch static sites.</div>';
        return;
      }
      const sites = await res.json();
      listDiv.innerHTML = '';
      if (!sites || sites.length === 0) {
        listDiv.innerHTML = '<div style="color:var(--text-muted);font-size:11px;text-align:center;padding:20px;">No static deployments registered.</div>';
        return;
      }

      sites.forEach(site => {
        const isWeb3 = site.domain.endsWith('.eth');
        const url = isWeb3 
          ? `${API_BASE}/api/compute/dns/gateway/${site.domain}?path=index.html`
          : `${API_BASE}/api/compute/static/serve/${site.domain}?path=index.html`;

        const badge = isWeb3 
          ? '<span class="status-badge" style="background:#6366f1;color:white;font-size:9px;padding:1px 5px;border-radius:4px;margin-left:6px;display:inline-block;vertical-align:middle;">Web3 ENS</span>' 
          : '<span class="status-badge" style="background:#4b5563;color:white;font-size:9px;padding:1px 5px;border-radius:4px;margin-left:6px;display:inline-block;vertical-align:middle;">Web2 DNS</span>';

        const card = document.createElement('div');
        card.className = 'log-item';
        card.style.display = 'flex';
        card.style.justifyContent = 'space-between';
        card.style.alignItems = 'center';
        card.style.padding = '10px 12px';
        card.style.background = '#1a1d24';
        card.style.border = '1px solid #30363d';
        card.style.borderRadius = '6px';
        card.innerHTML = `
          <div style="display:flex;flex-direction:column;gap:4px;">
            <div style="font-weight:600;font-size:12px;color:#c9d1d9;display:flex;align-items:center;">
              <span>${site.name}</span>
              ${badge}
            </div>
            <div style="font-size:11px;color:#8b949e;">Bucket: <span style="font-family:monospace;">${site.bucketName}</span></div>
            <div style="font-size:11px;color:#58a6ff;">Route: <a href="${url}" target="_blank" style="color:#58a6ff;text-decoration:underline;">${site.domain}</a></div>
          </div>
          <div style="display:flex;align-items:center;gap:12px;">
            <span class="status-badge" style="background:#1f2937;color:#34d399;font-size:10px;padding:2px 6px;border-radius:4px;">${site.status}</span>
            <button class="btn btn-outline" style="padding:2px 8px;font-size:10px;border-color:#f85149;color:#f85149;" onclick="deleteStaticSite('${site.id}')">UNDEPLOY</button>
          </div>
        `;
        listDiv.appendChild(card);
      });
    } catch(e) {
      listDiv.innerHTML = `<div style="color:var(--text-accent)">Error loading static sites: ${e.message}</div>`;
    }
  }

  async function deleteStaticSite(id) {
    if (!confirm("Are you sure you want to undeploy this static site?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/compute/static/${id}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (res.ok) {
        alert("Static site undeployed.");
        loadStaticSites();
      } else {
        alert("Failed to undeploy static site.");
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  /* ─ Serverless Edge Functions ─ */
  async function deployServerless() {
    const name = document.getElementById('funcName').value.trim();
    const triggerRoute = document.getElementById('funcRoute').value.trim();
    const code = document.getElementById('funcCode').value.trim();

    if (!name || !triggerRoute || !code) {
      alert("Function name, trigger route, and code are required.");
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/compute/serverless`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ name, triggerRoute, code })
      });
      if (!res.ok) {
        const err = await res.json();
        alert("Failed to deploy function: " + (err.error || "Server error"));
        return;
      }
      alert("Serverless Edge Function deployed successfully!");
      document.getElementById('funcName').value = '';
      document.getElementById('funcRoute').value = '';
      loadServerless();
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function loadServerless() {
    const listDiv = document.getElementById('serverlessList');
    listDiv.innerHTML = '<div style="color:var(--text-muted)">Loading functions...</div>';

    try {
      const res = await fetch(`${API_BASE}/api/compute/serverless`, { headers: getHeaders() });
      if (!res.ok) {
        listDiv.innerHTML = '<div style="color:var(--text-accent)">Failed to fetch functions.</div>';
        return;
      }
      const funcs = await res.json();
      listDiv.innerHTML = '';
      if (!funcs || funcs.length === 0) {
        listDiv.innerHTML = '<div style="color:var(--text-muted);font-size:11px;text-align:center;padding:20px;">No deployed serverless functions.</div>';
        document.getElementById('runTestFuncBtn').disabled = true;
        return;
      }

      funcs.forEach(func => {
        const card = document.createElement('div');
        const isActive = activeTestFunctionId === func.id;
        card.className = `log-item ${isActive ? 'selected-item' : ''}`;
        card.style.display = 'flex';
        card.style.justifyContent = 'space-between';
        card.style.alignItems = 'center';
        card.style.padding = '8px 10px';
        card.style.background = isActive ? '#1f2937' : '#1a1d24';
        card.style.border = isActive ? '1px solid #58a6ff' : '1px solid #30363d';
        card.style.borderRadius = '6px';
        card.style.cursor = 'pointer';
        card.onclick = (e) => {
          if (e.target.tagName !== 'BUTTON') {
            selectServerlessForTest(func.id, func.name);
          }
        };
        card.innerHTML = `
          <div style="display:flex;flex-direction:column;gap:2px;">
            <div style="font-weight:600;font-size:12px;color:#c9d1d9;">${func.name}</div>
            <div style="font-size:10px;color:#8b949e;font-family:monospace;">Route: ${func.triggerRoute}</div>
          </div>
          <div style="display:flex;align-items:center;gap:8px;">
            <span class="status-badge" style="background:#064e3b;color:#34d399;font-size:9px;padding:1px 5px;border-radius:4px;">ACTIVE</span>
            <button class="btn btn-outline" style="padding:1px 6px;font-size:9px;border-color:#f85149;color:#f85149;" onclick="deleteServerless('${func.id}')">DELETE</button>
          </div>
        `;
        listDiv.appendChild(card);
      });
    } catch(e) {
      listDiv.innerHTML = `<div style="color:var(--text-accent)">Error loading functions: ${e.message}</div>`;
    }
  }

  function selectServerlessForTest(id, name) {
    activeTestFunctionId = id;
    activeTestFunctionName = name;
    document.getElementById('runTestFuncBtn').disabled = false;
    document.getElementById('runTestFuncBtn').textContent = `RUN [${name}]`;
    loadServerless(); // reload list styling
  }

  async function runTestFunction() {
    if (!activeTestFunctionId) return;
    const logsBox = document.getElementById('funcLogs');
    logsBox.textContent = `Executing function ${activeTestFunctionName} inside sandbox...`;
    
    let paramsVal = {};
    try {
      paramsVal = JSON.parse(document.getElementById('testParams').value);
    } catch(e) {
      alert("Invalid JSON parameters. Please correct and retry.");
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/compute/serverless/${activeTestFunctionId}/execute`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify(paramsVal)
      });
      if (!res.ok) {
        logsBox.textContent = `[EXECUTION FAILURE] HTTP ${res.status}`;
        return;
      }
      const data = await res.json();
      logsBox.innerHTML = `
<span style="color:#58a6ff">[SANDBOX COMPILER STATE]</span> Wasm build target compiled successfully (JNI safe release)
<span style="color:#58a6ff">[CONTAINER LIFECYCLE]</span> Microsecond runtime VM spun up in 0.04ms
<span style="color:#34d399">[EXECUTION OUTPUT]</span>:
${data.executionOutput}
<span style="color:#8b949e">[METRICS]</span> Executed at ${data.timestamp}
      `;
    } catch(e) {
      logsBox.textContent = `[EXCEPTION] ${e.message}`;
    }
  }

  async function deleteServerless(id) {
    if (!confirm("Delete this edge function?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/compute/serverless/${id}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (res.ok) {
        if (activeTestFunctionId === id) {
          activeTestFunctionId = null;
          activeTestFunctionName = null;
          document.getElementById('runTestFuncBtn').disabled = true;
          document.getElementById('runTestFuncBtn').textContent = "RUN TEST";
          document.getElementById('funcLogs').textContent = "Select a function, enter parameters, and click RUN TEST.";
        }
        alert("Serverless function deleted.");
        loadServerless();
      } else {
        alert("Failed to delete serverless function.");
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  /* ─ Container Hosting ─ */
  async function deployContainer() {
    const name = document.getElementById('containerName').value.trim();
    const dockerImage = document.getElementById('containerImage').value.trim();
    const replicas = parseInt(document.getElementById('containerReplicas').value);
    const cpu = parseFloat(document.getElementById('containerCpu').value);
    const memory = parseInt(document.getElementById('containerMem').value);

    if (!name || !dockerImage) {
      alert("Workload name and Docker image are required.");
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/compute/container`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ name, dockerImage, replicas, cpu, memory })
      });
      if (!res.ok) {
        const err = await res.json();
        alert("Failed to schedule container: " + (err.error || "Server error"));
        return;
      }
      alert("Workload provisioned! Deployment process running in background via RabbitMQ worker queue.");
      document.getElementById('containerName').value = '';
      document.getElementById('containerImage').value = '';
      loadContainers();
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function loadContainers() {
    const listDiv = document.getElementById('containersList');
    try {
      const res = await fetch(`${API_BASE}/api/compute/container`, { headers: getHeaders() });
      if (!res.ok) {
        listDiv.innerHTML = '<div style="color:var(--text-accent)">Failed to fetch container workloads.</div>';
        return;
      }
      const items = await res.json();
      listDiv.innerHTML = '';
      if (!items || items.length === 0) {
        listDiv.innerHTML = '<div style="color:var(--text-muted);font-size:11px;text-align:center;padding:20px;">No container workloads deployed.</div>';
        return;
      }

      items.forEach(c => {
        const isSelected = activeContainerId === c.id;
        const card = document.createElement('div');
        card.className = `log-item ${isSelected ? 'selected-item' : ''}`;
        card.style.display = 'flex';
        card.style.justifyContent = 'space-between';
        card.style.alignItems = 'center';
        card.style.padding = '8px 10px';
        card.style.background = isSelected ? '#1f2937' : '#1a1d24';
        card.style.border = isSelected ? '1px solid #16a34a' : '1px solid #30363d';
        card.style.borderRadius = '6px';
        card.style.cursor = 'pointer';
        card.onclick = (e) => {
          if (e.target.tagName !== 'BUTTON' && e.target.tagName !== 'INPUT') {
            selectContainer(c.id, c.name);
          }
        };

        let badgeBg = '#ea580c';
        if (c.status === 'LIVE') badgeBg = '#16a34a';
        else if (c.status === 'BUILDING') badgeBg = '#2563eb';

        card.innerHTML = `
          <div style="display:flex;flex-direction:column;gap:2px;">
            <div style="font-weight:600;font-size:12px;color:#c9d1d9;">${c.name}</div>
            <div style="font-size:10px;color:#8b949e;font-family:monospace;">Image: ${c.dockerImage} | Spec: ${c.cpu}vCPU, ${c.memory}MB</div>
          </div>
          <div style="display:flex;align-items:center;gap:8px;">
            <span class="status-badge" style="background:${badgeBg};color:white;font-size:9px;padding:1px 5px;border-radius:4px;">${c.status}</span>
            <div style="display:flex;align-items:center;gap:4px;">
              <span style="font-size:10px;color:#8b949e;">Replicas:</span>
              <input type="number" min="1" max="10" value="${c.replicas}" style="width:38px;padding:2px;font-size:10px;background:#0d1117;color:white;border:1px solid #30363d;border-radius:4px;" onchange="changeReplicas('${c.id}', this.value)" />
            </div>
            <button class="btn btn-outline" style="padding:1px 6px;font-size:9px;border-color:#f85149;color:#f85149;" onclick="deleteContainer('${c.id}')">UNDEPLOY</button>
          </div>
        `;
        listDiv.appendChild(card);
      });
    } catch(e) {
      listDiv.innerHTML = `<div style="color:var(--text-accent)">Error loading containers: ${e.message}</div>`;
    }
  }

  function selectContainer(id, name) {
    activeContainerId = id;
    activeContainerName = name;
    loadContainers();
    fetchContainerLogs(id);
  }

  async function fetchContainerLogs(id) {
    try {
      const res = await fetch(`${API_BASE}/api/compute/container/${id}/logs`, { headers: getHeaders() });
      if (res.ok) {
        const data = await res.json();
        document.getElementById('containerLogs').textContent = data.logs;
      }
    } catch(e) {
      console.error("Failed to fetch logs: ", e);
    }
  }

  async function changeReplicas(id, count) {
    try {
      const res = await fetch(`${API_BASE}/api/compute/container/${id}/scale?replicas=${count}`, {
        method: "POST",
        headers: getHeaders()
      });
      if (res.ok) {
        loadContainers();
        if (activeContainerId === id) {
          fetchContainerLogs(id);
        }
      }
    } catch(e) {
      alert("Error scaling replicas: " + e.message);
    }
  }

  async function deleteContainer(id) {
    if (!confirm("Undeploy this workload container?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/compute/container/${id}`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (res.ok) {
        if (activeContainerId === id) {
          activeContainerId = null;
          activeContainerName = null;
          document.getElementById('containerLogs').textContent = "Select a container above to review Kubernetes scheduler and deployment logs.";
        }
        alert("Container workload undeployed.");
        loadContainers();
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  function startContainerLogPolling() {
    stopContainerLogPolling();
    containerLogTimer = setInterval(() => {
      if (selectedComputeType === 'container') {
        loadContainers();
        if (activeContainerId) {
          fetchContainerLogs(activeContainerId);
        }
      }
    }, 3000);
  }

  function stopContainerLogPolling() {
    if (containerLogTimer) {
      clearInterval(containerLogTimer);
      containerLogTimer = null;
    }
  }

  /* ── EMAIL SANDBOX SYSTEM ── */
  let currentEmailTab = 'outbox';

  function switchEmailTab(tab) {
    currentEmailTab = tab;
    const outboxTab = document.getElementById("email-tab-outbox");
    const inboxTab = document.getElementById("email-tab-inbox");
    const outboxList = document.getElementById("emailsOutboxList");
    const inboxList = document.getElementById("emailsInboxList");
    const clearOutboxBtn = document.getElementById("clearOutboxBtn");
    const clearInboxBtn = document.getElementById("clearInboxBtn");
    const refreshInboxBtn = document.getElementById("refreshInboxBtn");

    if (tab === 'outbox') {
      outboxTab.classList.add("active");
      outboxTab.style.borderColor = "#000";
      outboxTab.style.color = "#000";
      inboxTab.classList.remove("active");
      inboxTab.style.borderColor = "transparent";
      inboxTab.style.color = "var(--muted)";

      outboxList.style.display = "flex";
      inboxList.style.display = "none";

      clearOutboxBtn.style.display = "inline-flex";
      clearInboxBtn.style.display = "none";
      refreshInboxBtn.style.display = "none";

      loadEmailOutbox();
    } else {
      inboxTab.classList.add("active");
      inboxTab.style.borderColor = "#000";
      inboxTab.style.color = "#000";
      outboxTab.classList.remove("active");
      outboxTab.style.borderColor = "transparent";
      outboxTab.style.color = "var(--muted)";

      inboxList.style.display = "flex";
      outboxList.style.display = "none";

      clearOutboxBtn.style.display = "none";
      clearInboxBtn.style.display = "inline-flex";
      refreshInboxBtn.style.display = "inline-flex";

      loadEmailInbox();
    }
  }

  function loadActiveEmailTab() {
    if (currentEmailTab === 'outbox') {
      loadEmailOutbox();
    } else {
      loadEmailInbox();
    }
  }

  async function loadEmailOutbox() {
    try {
      const res = await fetch(`${API_BASE}/api/dev/emails`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const emails = await res.json();
      
      const container = document.getElementById("emailsOutboxList");
      container.innerHTML = "";
      
      if (!Array.isArray(emails) || emails.length === 0) {
        container.innerHTML = `<div style="color:var(--muted);padding:14px;text-align:center">No emails in the sandbox outbox log yet.</div>`;
        return;
      }

      emails.forEach(email => {
        const item = document.createElement("div");
        item.style.borderBottom = "1px solid var(--border2)";
        item.style.padding = "10px 12px";
        item.style.display = "flex";
        item.style.flexDirection = "column";
        item.style.gap = "4px";

        let statusBg = "#66bb6a"; // green
        if (email.status === 'FAILED') statusBg = "#ef5350"; // red
        if (email.status === 'QUEUED') statusBg = "#ffa726"; // orange

        item.innerHTML = `
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <div style="font-weight:700; font-size:10px;">TO: ${escapeHTML(email.toAddress)}</div>
            <span style="background:${statusBg}; color:#fff; padding:1px 6px; font-size:8px; font-weight:700;">${email.status}</span>
          </div>
          <div style="font-weight:600; font-size:9px; color:var(--text);">SUBJECT: ${escapeHTML(email.subject)}</div>
          <div style="font-size:9px; color:var(--muted); white-space:pre-wrap; background:#f9f9f9; padding:6px; border:1px solid #eee; margin-top:2px;">${escapeHTML(email.body)}</div>
          ${email.errorMessage ? `<div style="font-size:8px; color:#ef5350; font-weight:700; margin-top:2px;">ERROR: ${escapeHTML(email.errorMessage)}</div>` : ''}
          <div style="font-size:8px; color:var(--label); text-align:right; margin-top:2px;">DISPATCHED AT: ${new Date(email.sentAt).toLocaleString()}</div>
        `;
        container.appendChild(item);
      });
    } catch (e) {
      console.error("Error loading email outbox", e);
    }
  }

  async function clearEmailOutbox() {
    if (!confirm("Are you sure you want to clear all emails in the sandbox outbox log?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/dev/emails`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (res.ok) {
        clog('consoleBox', '[INFO] Sandbox email outbox log cleared', 'info');
        loadEmailOutbox();
      } else {
        alert("Failed to clear outbox");
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function loadEmailInbox() {
    try {
      const res = await fetch(`${API_BASE}/api/dev/emails/inbox`, { headers: getHeaders() });
      if (res.status === 401 || res.status === 403) { logout(); return; }
      const emails = await res.json();
      
      const container = document.getElementById("emailsInboxList");
      container.innerHTML = "";
      
      if (!Array.isArray(emails) || emails.length === 0) {
        container.innerHTML = `<div style="color:var(--muted);padding:14px;text-align:center">No emails in the self-hosted local inbox yet. Connect a client or SMTP sender to port 2525.</div>`;
        return;
      }

      emails.forEach(email => {
        const item = document.createElement("div");
        item.style.borderBottom = "1px solid var(--border2)";
        item.style.padding = "10px 12px";
        item.style.display = "flex";
        item.style.flexDirection = "column";
        item.style.gap = "4px";

        item.innerHTML = `
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <div style="font-weight:700; font-size:10px;">FROM: ${escapeHTML(email.fromAddress)}</div>
            <div style="font-weight:700; font-size:10px; color:var(--muted)">TO: ${escapeHTML(email.toAddress)}</div>
          </div>
          <div style="font-weight:600; font-size:9px; color:var(--text);">SUBJECT: ${escapeHTML(email.subject)}</div>
          <div style="font-size:9px; color:var(--muted); white-space:pre-wrap; background:#f9f9f9; padding:6px; border:1px solid #eee; margin-top:2px;">${escapeHTML(email.body)}</div>
          <div style="font-size:8px; color:var(--label); text-align:right; margin-top:2px;">RECEIVED AT: ${new Date(email.receivedAt).toLocaleString()}</div>
        `;
        container.appendChild(item);
      });
    } catch (e) {
      console.error("Error loading email inbox", e);
    }
  }

  async function clearEmailInbox() {
    if (!confirm("Are you sure you want to clear all emails in the local inbox?")) return;
    try {
      const res = await fetch(`${API_BASE}/api/dev/emails/inbox`, {
        method: "DELETE",
        headers: getHeaders()
      });
      if (res.ok) {
        clog('consoleBox', '[INFO] Self-hosted local email inbox cleared', 'info');
        loadEmailInbox();
      } else {
        alert("Failed to clear inbox");
      }
    } catch(e) {
      alert("Error: " + e.message);
    }
  }

  async function sendTestEmail() {
    const to = document.getElementById("testEmailTo").value.trim();
    const subject = document.getElementById("testEmailSubject").value.trim();
    const body = document.getElementById("testEmailBody").value.trim();

    if (!to) {
      alert("Recipient email address is required");
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/api/dev/emails/send-test`, {
        method: "POST",
        headers: getHeaders(),
        body: JSON.stringify({ to, subject, body })
      });
      const data = await res.json();
      if (!res.ok) {
        alert("Failed to send test email: " + (data.error || "Server error"));
        return;
      }
      alert("Test email triggered successfully!");
      document.getElementById("testEmailTo").value = "";
      document.getElementById("testEmailSubject").value = "";
      document.getElementById("testEmailBody").value = "";
      loadActiveEmailTab();
    } catch(e) {
      alert("Error triggering test email: " + e.message);
    }
  }

  // Init
  checkAuth();
