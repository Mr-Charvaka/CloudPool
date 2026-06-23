import { useState, useEffect } from 'react';
import { fetchBuckets } from '../lib/api';

function getToken(): string | null {
  return localStorage.getItem('cp_token');
}

function getUserEmail(): string {
  const token = getToken();
  if (!token) return 'DEVELOPER';
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.sub || payload.email || 'DEVELOPER';
  } catch {
    return 'DEVELOPER';
  }
}

async function handleLogout() {
  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${getToken()}` },
      credentials: 'include',
    });
  } catch {
    // proceed with local logout even if server call fails
  }
  localStorage.removeItem('cp_token');
  window.location.reload();
}

export default function Topbar() {
  const [email] = useState(getUserEmail);
  const [project, setProject] = useState('default-project');

  useEffect(() => {
    fetchBuckets().then(buckets => {
      if (buckets.length > 0) setProject(buckets[0].name);
    }).catch(() => {});
  }, []);

  return (
    <div id="topbar">
      <div>
        <div className="brand">CLOUDPOOL</div>
        <div className="ver">V0.1.0_SNAPSHOT</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', borderRight: '1px solid var(--border2)', paddingRight: '16px' }}>
          <span style={{ fontSize: '9px', color: 'var(--muted)', letterSpacing: '.08em' }}>PROJECT:</span>
          <select id="projectSelector" style={{ width: '130px', height: '20px', padding: '2px', fontSize: '9px' }} value={project} onChange={e => setProject(e.target.value)}>
            <option value={project}>{project}</option>
          </select>
          <button className="btn" style={{ padding: '2px 8px', fontSize: '8px', height: '20px' }}>+ NEW</button>
        </div>
        <div style={{ fontSize: '9px', color: 'var(--muted)' }} id="userDisplay">{email}</div>
        <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '8px' }} onClick={handleLogout}>LOGOUT</button>
        <div className="badge" id="topBadge">POOL_CONNECTED_ONLINE</div>
      </div>
    </div>
  );
}
