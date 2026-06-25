import { useState, useEffect } from 'react';
import { fetchSecrets, saveSecret, deleteSecret, fetchSnapshots, createSnapshot, restoreSnapshot, deleteSnapshot, type Secret, type Snapshot } from '../lib/api';

export default function TopologyPage() {
  const [secrets, setSecrets] = useState<Secret[]>([]);
  const [snapshots, setSnapshots] = useState<Snapshot[]>([]);
  const [secretKey, setSecretKey] = useState('');
  const [secretVal, setSecretVal] = useState('');
  const [snapName, setSnapName] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => { loadAll(); }, []);

  async function loadAll() {
    try {
      const [s, sn] = await Promise.all([
        fetchSecrets().catch(() => []),
        fetchSnapshots().catch(() => []),
      ]);
      setSecrets(s);
      setSnapshots(sn);
    } catch { setErr('Failed to load topology data'); }
  }

  async function handleSaveSecret() {
    if (!secretKey || !secretVal) { setErr('Key and value required'); return; }
    try {
      await saveSecret(secretKey, secretVal);
      setSecretKey(''); setSecretVal('');
      setSecrets(await fetchSecrets());
    } catch (e) { setErr(`Failed to save secret: ${e}`); }
  }

  async function handleDeleteSecret(id: string) {
    if (!confirm('Delete this secret?')) return;
    try {
      await deleteSecret(id);
      setSecrets(await fetchSecrets());
    } catch (e) { setErr(`Delete failed: ${e}`); }
  }

  async function handleCreateSnapshot() {
    if (!snapName) { setErr('Snapshot name required'); return; }
    try {
      await createSnapshot(snapName);
      setSnapName('');
      setSnapshots(await fetchSnapshots());
    } catch (e) { setErr(`Snapshot failed: ${e}`); }
  }

  async function handleRestoreSnapshot(id: string) {
    if (!confirm('Restoring will drop current schemas. Proceed?')) return;
    try {
      await restoreSnapshot(id);
      loadAll();
    } catch (e) { setErr(`Restore failed: ${e}`); }
  }

  async function handleDeleteSnapshot(id: string) {
    if (!confirm('Delete this snapshot permanently?')) return;
    try {
      await deleteSnapshot(id);
      setSnapshots(await fetchSnapshots());
    } catch (e) { setErr(`Delete failed: ${e}`); }
  }

  return (
    <div className="page active">
      <div>
        <div className="page-title">PROJECT TOPOLOGY & INFRASTRUCTURE</div>
        <div className="page-sub">Manage project secrets, view topology state, and take versioned infrastructure snapshots.</div>
      </div>
      {err && <div style={{ color: '#ef5350', fontSize: '10px' }}>{err}</div>}
      <div className="panels">
        <div className="panel panel-left">
          <div className="panel-header">
            <div className="panel-title">ENVIRONMENT SECRETS VAULT</div>
            <div className="panel-badge">SECURE_VAULT</div>
          </div>
          <div className="panel-body">
            <div style={{ border: '1px solid var(--border2)', overflowY: 'auto', maxHeight: '220px' }}>
              <table className="tbl">
                <thead><tr><th>SECRET KEY</th><th>OBFUSCATED VALUE</th><th>ACTIONS</th></tr></thead>
                <tbody>
                  {secrets.length === 0 ? (
                    <tr><td colSpan={3} style={{ textAlign: 'center', color: 'var(--muted)' }}>No secrets set.</td></tr>
                  ) : secrets.map(s => (
                    <tr key={s.id}>
                      <td style={{ fontWeight: 600 }}>{s.secretKey}</td>
                      <td style={{ color: 'var(--muted)' }}>••••••••</td>
                      <td><button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '8px' }} onClick={() => handleDeleteSecret(s.id)}>DELETE</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ borderTop: '1px solid var(--border2)', paddingTop: '10px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div className="panel-title" style={{ fontSize: '9px' }}>ADD OR UPDATE VARIABLE</div>
              <div className="field-row">
                <div className="field"><div className="field-label">KEY</div><input type="text" placeholder="DATABASE_URL" value={secretKey} onChange={e => setSecretKey(e.target.value)} /></div>
                <div className="field"><div className="field-label">VALUE</div><input type="password" placeholder="supersecret..." value={secretVal} onChange={e => setSecretVal(e.target.value)} /></div>
              </div>
              <div><button className="btn" onClick={handleSaveSecret}>⚡ ADD/UPDATE SECRET</button></div>
            </div>
          </div>
        </div>
        <div className="panel panel-right">
          <div className="panel-header">
            <div className="panel-title">INFRASTRUCTURE SNAPSHOTS</div>
            <div className="panel-badge">VERSIONED</div>
          </div>
          <div className="panel-body">
            <div style={{ border: '1px solid var(--border2)', overflowY: 'auto', maxHeight: '220px' }}>
              <table className="tbl">
                <thead><tr><th>SNAPSHOT NAME</th><th>CREATED</th><th>ACTIONS</th></tr></thead>
                <tbody>
                  {snapshots.length === 0 ? (
                    <tr><td colSpan={3} style={{ textAlign: 'center', color: 'var(--muted)' }}>No snapshots taken.</td></tr>
                  ) : snapshots.map(s => (
                    <tr key={s.id}>
                      <td style={{ fontWeight: 600 }}>{s.name}</td>
                      <td>{new Date(s.createdAt).toLocaleString()}</td>
                      <td>
                        <button className="btn" style={{ padding: '2px 8px', fontSize: '8px' }} onClick={() => handleRestoreSnapshot(s.id)}>RESTORE</button>
                        <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '8px', marginLeft: '4px' }} onClick={() => handleDeleteSnapshot(s.id)}>DELETE</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ borderTop: '1px solid var(--border2)', paddingTop: '10px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div className="panel-title" style={{ fontSize: '9px' }}>CREATE NEW BACKUP SNAPSHOT</div>
              <div className="field"><div className="field-label">SNAPSHOT LABEL</div><input type="text" placeholder="e.g. snapshot-v1-stable" value={snapName} onChange={e => setSnapName(e.target.value)} /></div>
              <div><button className="btn" onClick={handleCreateSnapshot}>📸 TAKE SNAPSHOT</button></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
