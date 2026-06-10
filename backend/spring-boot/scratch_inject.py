import re

html_path = r"d:\D\RESUME PROJECTS\Cloud Pool\backend\spring-boot\src\main\resources\static\index.html"

with open(html_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add Sidebar items
sidebar_injection = """
    <!-- SAAS SERVICES -->
    <div class="nav-item" data-page="kvstore" onclick="goTo('kvstore',this)">
      <svg class="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="1" y="2" width="8" height="6" rx="1"/><line x1="1" y1="5" x2="9" y2="5"/><line x1="4" y1="2" x2="4" y2="8"/></svg>
      KV STORE
    </div>
    <div class="nav-item" data-page="tenantauth" onclick="goTo('tenantauth',this)">
      <svg class="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="5" cy="3" r="1.5"/><path d="M2.5 8c0-1.5 1.5-2.5 2.5-2.5s2.5 1 2.5 2.5"/></svg>
      AUTH SERVICE
    </div>
    <div class="nav-item" data-page="cronjobs" onclick="goTo('cronjobs',this)">
      <svg class="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="5" cy="5" r="4"/><path d="M5 2v3l2 2"/></svg>
      CRON JOBS
    </div>
    <div class="nav-item" data-page="pubsub" onclick="goTo('pubsub',this)">
      <svg class="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 8A5 5 0 018 2M3 7a3 3 0 014-4M4.5 6a1 1 0 111-1 1 1 0 01-1 1z"/></svg>
      PUB/SUB
    </div>
    <div class="nav-item" data-page="waf" onclick="goTo('waf',this)">
      <svg class="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M5 1L1 3v3c0 2.5 3 3.5 4 3.5s4-1 4-3.5V3L5 1z"/></svg>
      WAF & FIREWALL
    </div>
    <div class="nav-item" data-page="tunnels" onclick="goTo('tunnels',this)">
      <svg class="nav-icon" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="1" y="4" width="8" height="2" rx="0.5"/><path d="M3 4V2M7 4V2M3 8v-2M7 8v-2"/></svg>
      CLOUD TUNNELS
    </div>
    <div class="sidebar-spacer"></div>"""

if 'data-page="kvstore"' not in content:
    content = content.replace('<div class="sidebar-spacer"></div>', sidebar_injection)

# 2. Add Pages
pages_injection = """
    <!-- KV STORE -->
    <div class="page" id="page-kvstore">
      <div>
        <div class="page-title">KEY-VALUE (KV) STORE</div>
        <div class="page-sub">In-memory ultra-fast cache backed by database persistence with TTL expiry.</div>
      </div>
      <div class="panels">
        <div class="panel panel-left">
          <div class="panel-header"><div class="panel-title">ACTIVE KEYS</div><button class="btn" style="padding:2px 8px;font-size:8px" onclick="loadKvKeys()">↻ REFRESH</button></div>
          <div class="panel-body" id="kvKeysList">
            <!-- Loaded dynamically -->
          </div>
        </div>
        <div class="panel panel-right">
          <div class="panel-header"><div class="panel-title">SET NEW KEY</div><div class="panel-badge">MEMORY_STORE</div></div>
          <div class="panel-body">
            <div class="field"><div class="field-label">KEY IDENTIFIER</div><input type="text" id="kvKeyName" placeholder="e.g. rate-limit-ip"/></div>
            <div class="field"><div class="field-label">VALUE (JSON OR TEXT)</div><textarea id="kvValue" style="height:80px" placeholder="{}"></textarea></div>
            <div class="field"><div class="field-label">TTL (SECONDS, OPTIONAL)</div><input type="number" id="kvTtl" placeholder="3600"/></div>
            <button class="btn" onclick="saveKvKey()">+ UPSERT KEY</button>
          </div>
        </div>
      </div>
    </div>

    <!-- TENANT AUTH -->
    <div class="page" id="page-tenantauth">
      <div>
        <div class="page-title">TENANT AUTHENTICATION</div>
        <div class="page-sub">Manage your application's end-users natively. CloudPool securely issues JWTs and hashes passwords.</div>
      </div>
      <div class="panels">
        <div class="panel panel-left">
          <div class="panel-header"><div class="panel-title">REGISTERED TENANT USERS</div><button class="btn" style="padding:2px 8px;font-size:8px" onclick="loadTenantUsers()">↻ REFRESH</button></div>
          <div class="panel-body" style="padding:0">
            <table class="tbl">
              <thead><tr><th>EMAIL</th><th>DISPLAY NAME</th><th>CREATED</th><th>ACTIONS</th></tr></thead>
              <tbody id="tenantUsersTable"></tbody>
            </table>
          </div>
        </div>
        <div class="panel panel-right">
          <div class="panel-header"><div class="panel-title">AUTH CONFIGURATION</div><div class="panel-badge">NATIVE_AUTH</div></div>
          <div class="panel-body">
            <div class="stat-row"><div class="stat-key">ALGORITHM</div><div class="stat-val">HS512 / BCrypt</div></div>
            <div class="stat-row"><div class="stat-key">JWT EXPIRY</div><div class="stat-val">1 HOUR</div></div>
            <div class="stat-row"><div class="stat-key">SESSION REFRESH</div><div class="stat-val">30 DAYS</div></div>
            <br>
            <div class="field-label">MANUAL END-USER REGISTRATION</div>
            <div class="field"><input type="text" id="tAuthEmail" placeholder="user@domain.com" style="margin-bottom:8px"/></div>
            <div class="field"><input type="password" id="tAuthPass" placeholder="Password" style="margin-bottom:8px"/></div>
            <div class="field"><input type="text" id="tAuthName" placeholder="Display Name (optional)" style="margin-bottom:8px"/></div>
            <button class="btn" style="width:100%;justify-content:center" onclick="createTenantUser()">+ CREATE USER</button>
          </div>
        </div>
      </div>
    </div>

    <!-- CRON JOBS -->
    <div class="page" id="page-cronjobs">
      <div>
        <div class="page-title">SERVERLESS CRON JOBS</div>
        <div class="page-sub">Schedule asynchronous background tasks to trigger HTTP webhooks.</div>
      </div>
      <div class="panels">
        <div class="panel panel-left" style="flex:1.5">
          <div class="panel-header"><div class="panel-title">ACTIVE CRON SCHEDULES</div><button class="btn" style="padding:2px 8px;font-size:8px" onclick="loadCronJobs()">↻ REFRESH</button></div>
          <div class="panel-body" style="padding:0;overflow-y:auto">
            <table class="tbl">
              <thead><tr><th>JOB NAME</th><th>SCHEDULE (CRON)</th><th>TARGET URL</th><th>STATUS</th><th>ACTIONS</th></tr></thead>
              <tbody id="cronJobsTable"></tbody>
            </table>
          </div>
        </div>
        <div class="panel panel-right" style="flex:0.8">
          <div class="panel-header"><div class="panel-title">CREATE SCHEDULE</div><div class="panel-badge">THREAD_POOL</div></div>
          <div class="panel-body">
            <div class="field"><div class="field-label">JOB NAME</div><input type="text" id="cronName" placeholder="daily-cleanup"/></div>
            <div class="field"><div class="field-label">CRON EXPRESSION</div><input type="text" id="cronExpr" placeholder="0 0 0 * * ?"/></div>
            <div class="field"><div class="field-label">TARGET URL</div><input type="text" id="cronUrl" placeholder="https://api.example.com/cleanup"/></div>
            <div class="field"><div class="field-label">HTTP METHOD</div><select id="cronMethod"><option value="POST">POST</option><option value="GET">GET</option></select></div>
            <div class="field"><div class="field-label">PAYLOAD (JSON, OPTIONAL)</div><textarea id="cronPayload" style="height:60px"></textarea></div>
            <button class="btn" onclick="saveCronJob()">+ SCHEDULE JOB</button>
          </div>
        </div>
      </div>
    </div>

    <!-- PUB/SUB -->
    <div class="page" id="page-pubsub">
      <div>
        <div class="page-title">REAL-TIME PUB/SUB WEBSOCKETS</div>
        <div class="page-sub">Broadcast dynamic messages securely to connected clients.</div>
      </div>
      <div class="panels">
        <div class="panel panel-left">
          <div class="panel-header"><div class="panel-title">MANUAL BROADCAST</div><div class="panel-badge">SERVER_PUSH</div></div>
          <div class="panel-body">
            <div class="field"><div class="field-label">CHANNEL NAME</div><input type="text" id="pubsubChannel" placeholder="e.g. project_updates"/></div>
            <div class="field"><div class="field-label">MESSAGE PAYLOAD (JSON)</div><textarea id="pubsubPayload" style="height:120px" placeholder='{"status":"deployed", "version":"1.2"}'></textarea></div>
            <button class="btn" onclick="broadcastPubSub()">⫸ BROADCAST TO CHANNEL</button>
          </div>
        </div>
        <div class="panel panel-right">
          <div class="panel-header"><div class="panel-title">LIVE EVENT STREAM</div><div class="panel-badge">LISTENING</div></div>
          <div class="panel-body">
            <div class="console-wrap">
              <div class="console-label">LOCAL CLIENT LISTENER (SUBSCRIBE TO CHANNEL)</div>
              <div style="display:flex;gap:8px;margin-bottom:8px">
                <input type="text" id="pubsubListenChannel" placeholder="Channel to listen" style="flex:1"/>
                <button class="btn btn-outline" style="padding:4px 8px;font-size:8px" onclick="connectPubSubListener()">CONNECT</button>
              </div>
              <div class="console-box" id="pubsubLog" style="height:200px"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- WAF & FIREWALL -->
    <div class="page" id="page-waf">
      <div>
        <div class="page-title">WEB APPLICATION FIREWALL (WAF)</div>
        <div class="page-sub">Protect endpoints with rate limiting, IP blocking, and SQL injection screening.</div>
      </div>
      <div class="panels">
        <div class="panel panel-left">
          <div class="panel-header"><div class="panel-title">ACTIVE FIREWALL RULES</div><button class="btn" style="padding:2px 8px;font-size:8px" onclick="loadWafRules()">↻ REFRESH</button></div>
          <div class="panel-body" style="padding:0">
            <table class="tbl">
              <thead><tr><th>TYPE</th><th>PATTERN / LIMIT</th><th>ACTION</th><th>STATUS</th><th>DELETE</th></tr></thead>
              <tbody id="wafRulesTable"></tbody>
            </table>
          </div>
        </div>
        <div class="panel panel-right">
          <div class="panel-header"><div class="panel-title">CREATE WAF RULE</div><div class="panel-badge">EDGE_FILTER</div></div>
          <div class="panel-body">
            <div class="field"><div class="field-label">RULE TYPE</div>
              <select id="wafType">
                <option value="RATE_LIMIT">Rate Limiting (RPS)</option>
                <option value="IP_BLOCK">IP Address Block</option>
                <option value="SQLI_BLOCK">SQL Injection Block</option>
              </select>
            </div>
            <div class="field"><div class="field-label">PATTERN / VALUE (e.g. '10' for RPS, '192.168.1.1' for IP)</div>
              <input type="text" id="wafPattern" placeholder="e.g. 10"/>
            </div>
            <button class="btn" onclick="saveWafRule()">+ ADD SECURITY RULE</button>
          </div>
        </div>
      </div>
    </div>

    <!-- CLOUD TUNNELS -->
    <div class="page" id="page-tunnels">
      <div>
        <div class="page-title">CLOUD TUNNELS</div>
        <div class="page-sub">Expose local environments via secure WebSocket proxies (Ngrok alternative).</div>
      </div>
      <div class="panels">
        <div class="panel panel-left" style="flex:1">
          <div class="panel-header"><div class="panel-title">TUNNEL STATUS</div></div>
          <div class="panel-body">
            <div style="padding:16px;border:1px dashed var(--border2);text-align:center">
              <div style="font-size:18px;margin-bottom:8px">🚇</div>
              <div style="font-size:12px;font-weight:600;margin-bottom:4px">NO ACTIVE TUNNELS</div>
              <div style="font-size:9px;color:var(--muted)">Use the CloudPool CLI to initiate a secure tunnel:</div>
              <div style="background:#000;color:#fff;padding:8px;margin-top:10px;font-family:monospace;letter-spacing:1px">cloudpool tunnel --port 3000</div>
            </div>
          </div>
        </div>
        <div class="panel panel-right">
          <div class="panel-header"><div class="panel-title">TUNNEL METRICS</div></div>
          <div class="panel-body">
            <div class="stat-row"><div class="stat-key">ACTIVE CONNECTIONS</div><div class="stat-val">0</div></div>
            <div class="stat-row"><div class="stat-key">DATA TRANSFERRED</div><div class="stat-val">0 MB</div></div>
            <div class="stat-row"><div class="stat-key">PROXY PROTOCOL</div><div class="stat-val">WebSocket over TLS</div></div>
          </div>
        </div>
      </div>
    </div>
"""

# Find closing main tag
main_close_index = content.rfind('  </div>\n\n  <!-- SETTINGS MODALS -->')
if main_close_index == -1:
    # fallback
    main_close_index = content.rfind('  </div>\n</div>\n\n<!-- FOOTER -->')

if 'id="page-kvstore"' not in content:
    content = content[:main_close_index] + pages_injection + "\n" + content[main_close_index:]

# 3. Add JS functions
js_injection = """
  // === SAAS SERVICES LOGIC === //

  async function loadKvKeys() {
    if(!activeProjectId) return;
    try {
      const res = await fetch(`/api/v1/projects/${activeProjectId}/kv`, { headers: getAuthHeaders() });
      const data = await res.json();
      const list = document.getElementById('kvKeysList');
      list.innerHTML = '';
      if(data.length === 0) list.innerHTML = '<div class="dz-sub">No keys found in store.</div>';
      data.forEach(k => {
        list.innerHTML += `<div class="key-row">
          <div class="key-name">${k.keyName}</div>
          <div class="key-val">${k.kvValue}</div>
          <button class="btn btn-outline" style="padding:2px 6px;font-size:8px" onclick="deleteKvKey('${k.keyName}')">DELETE</button>
        </div>`;
      });
    } catch(e) { console.error(e); }
  }

  async function saveKvKey() {
    const k = document.getElementById('kvKeyName').value;
    const v = document.getElementById('kvValue').value;
    const t = document.getElementById('kvTtl').value;
    if(!k || !v) return;
    try {
      await fetch(`/api/v1/projects/${activeProjectId}/kv/${k}`, {
        method: 'PUT',
        headers: getAuthHeaders(),
        body: JSON.stringify({ value: v, ttlSeconds: t ? parseInt(t) : null })
      });
      document.getElementById('kvKeyName').value = '';
      document.getElementById('kvValue').value = '';
      loadKvKeys();
    } catch(e) { alert('Error saving KV'); }
  }

  async function deleteKvKey(k) {
    await fetch(`/api/v1/projects/${activeProjectId}/kv/${k}`, { method: 'DELETE', headers: getAuthHeaders() });
    loadKvKeys();
  }

  async function loadTenantUsers() {
    if(!activeProjectId) return;
    try {
      const res = await fetch(`/api/v1/projects/${activeProjectId}/auth/users`, { headers: getAuthHeaders() });
      const data = await res.json();
      const tbody = document.getElementById('tenantUsersTable');
      tbody.innerHTML = '';
      data.forEach(u => {
        tbody.innerHTML += `<tr>
          <td>${u.email}</td>
          <td>${u.displayName || '-'}</td>
          <td>${new Date(u.createdAt).toLocaleString()}</td>
          <td><button class="btn btn-outline" style="padding:2px 6px;font-size:8px" onclick="deleteTenantUser('${u.id}')">REVOKE</button></td>
        </tr>`;
      });
    } catch(e) {}
  }

  async function createTenantUser() {
    const email = document.getElementById('tAuthEmail').value;
    const pass = document.getElementById('tAuthPass').value;
    const name = document.getElementById('tAuthName').value;
    if(!email || !pass) return;
    try {
      await fetch(`/api/v1/projects/${activeProjectId}/auth/signup`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ email, password: pass, displayName: name })
      });
      document.getElementById('tAuthEmail').value = '';
      document.getElementById('tAuthPass').value = '';
      loadTenantUsers();
    } catch(e) {}
  }

  async function deleteTenantUser(id) {
    if(!confirm("Are you sure?")) return;
    await fetch(`/api/v1/projects/${activeProjectId}/auth/users/${id}`, { method: 'DELETE', headers: getAuthHeaders() });
    loadTenantUsers();
  }

  async function loadCronJobs() {
    if(!activeProjectId) return;
    try {
      const res = await fetch(`/api/v1/projects/${activeProjectId}/cron`, { headers: getAuthHeaders() });
      const data = await res.json();
      const tbody = document.getElementById('cronJobsTable');
      tbody.innerHTML = '';
      data.forEach(c => {
        tbody.innerHTML += `<tr>
          <td>${c.name}</td>
          <td>${c.cronExpression}</td>
          <td>${c.targetUrl}</td>
          <td><span class="chip active" style="color:green">ACTIVE</span></td>
          <td><button class="btn btn-outline" style="padding:2px 6px;font-size:8px" onclick="deleteCron('${c.id}')">DELETE</button></td>
        </tr>`;
      });
    } catch(e) {}
  }

  async function saveCronJob() {
    const n = document.getElementById('cronName').value;
    const e = document.getElementById('cronExpr').value;
    const u = document.getElementById('cronUrl').value;
    const m = document.getElementById('cronMethod').value;
    const p = document.getElementById('cronPayload').value;
    if(!n || !e || !u) return;
    await fetch(`/api/v1/projects/${activeProjectId}/cron`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ name: n, cronExpression: e, targetUrl: u, httpMethod: m, payload: p, isActive: true })
    });
    loadCronJobs();
  }

  async function deleteCron(id) {
    await fetch(`/api/v1/projects/${activeProjectId}/cron/${id}`, { method: 'DELETE', headers: getAuthHeaders() });
    loadCronJobs();
  }

  let pubsubSocket = null;
  function connectPubSubListener() {
    const channel = document.getElementById('pubsubListenChannel').value;
    if(!channel) return;
    if(pubsubSocket) pubsubSocket.close();
    
    pubsubSocket = new WebSocket(`ws://${window.location.host}/ws/pubsub`);
    pubsubSocket.onopen = () => {
      pubsubSocket.send(JSON.stringify({ action: "subscribe", channel: channel, projectId: activeProjectId }));
      document.getElementById('pubsubLog').innerHTML += `<div class="log-line"><span class="log-ts">[LOCAL]</span><span class="log-ok">Subscribed to ${channel}</span></div>`;
    };
    pubsubSocket.onmessage = (e) => {
      document.getElementById('pubsubLog').innerHTML += `<div class="log-line"><span class="log-ts">[RECV]</span><span class="log-info">${e.data}</span></div>`;
    };
  }

  async function broadcastPubSub() {
    const channel = document.getElementById('pubsubChannel').value;
    const payload = document.getElementById('pubsubPayload').value;
    if(!channel || !payload) return;
    await fetch(`/api/v1/projects/${activeProjectId}/pubsub/broadcast`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ channel, payloadJson: payload })
    });
  }

  async function loadWafRules() {
    if(!activeProjectId) return;
    try {
      const res = await fetch(`/api/v1/projects/${activeProjectId}/waf`, { headers: getAuthHeaders() });
      const data = await res.json();
      const tbody = document.getElementById('wafRulesTable');
      tbody.innerHTML = '';
      data.forEach(r => {
        tbody.innerHTML += `<tr>
          <td>${r.ruleType}</td>
          <td>${r.pattern}</td>
          <td>${r.action}</td>
          <td><span class="chip active">ENABLED</span></td>
          <td><button class="btn btn-outline" style="padding:2px 6px;font-size:8px" onclick="deleteWaf('${r.id}')">DELETE</button></td>
        </tr>`;
      });
    } catch(e) {}
  }

  async function saveWafRule() {
    const type = document.getElementById('wafType').value;
    const pattern = document.getElementById('wafPattern').value;
    if(!pattern) return;
    await fetch(`/api/v1/projects/${activeProjectId}/waf`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ ruleType: type, pattern: pattern, action: 'BLOCK' })
    });
    document.getElementById('wafPattern').value = '';
    loadWafRules();
  }

  async function deleteWaf(id) {
    await fetch(`/api/v1/projects/${activeProjectId}/waf/${id}`, { method: 'DELETE', headers: getAuthHeaders() });
    loadWafRules();
  }

"""

if "function loadKvKeys()" not in content:
    # Inject before the final </script> tag
    script_close = content.rfind('</script>')
    content = content[:script_close] + js_injection + content[script_close:]


# 4. Modify switchProject to load new data if those pages are active
switch_project_injection = """
    // Automatically load data for active page
    const activePage = document.querySelector('.nav-item.active').getAttribute('data-page');
    if(activePage === 'kvstore') loadKvKeys();
    if(activePage === 'tenantauth') loadTenantUsers();
    if(activePage === 'cronjobs') loadCronJobs();
    if(activePage === 'waf') loadWafRules();
"""
if "if(activePage === 'kvstore')" not in content:
    content = content.replace("loadEmailInbox();", "loadEmailInbox();\n" + switch_project_injection)

# 5. Modify goTo function to load data
goto_injection = """
    if(pageId === 'kvstore') loadKvKeys();
    if(pageId === 'tenantauth') loadTenantUsers();
    if(pageId === 'cronjobs') loadCronJobs();
    if(pageId === 'waf') loadWafRules();
"""
if "if(pageId === 'kvstore')" not in content:
    # Find the goTo function and inject inside
    goto_end = content.find("}", content.find("function goTo(pageId, el)"))
    content = content[:goto_end] + goto_injection + content[goto_end:]


with open(html_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Successfully injected SaaS UI into index.html")
