import { useState, useEffect } from 'react';
import { fetchApiKeys, generateApiKey, deleteApiKey, type ApiKey } from '../lib/api';

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [newKeyName, setNewKeyName] = useState('');
  const [newKeyVal, setNewKeyVal] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => { loadKeys(); }, []);

  async function loadKeys() {
    try {
      setKeys(await fetchApiKeys());
    } catch { setErr('Failed to load keys'); }
  }

  async function handleGenerate() {
    const name = newKeyName.trim() || 'service-key';
    try {
      const { apiKey } = await generateApiKey(name);
      setNewKeyVal(apiKey);
      setNewKeyName('');
      await loadKeys();
    } catch (e) { setErr(`Generate failed: ${e}`); }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this API key? Cannot be undone.')) return;
    try {
      await deleteApiKey(id);
      await loadKeys();
    } catch (e) { setErr(`Delete failed: ${e}`); }
  }

  return (
    <div className="page active">
      <div>
        <div className="page-title">API KEY MANAGEMENT</div>
        <div className="page-sub">Create and view API keys for accessing CloudPool routes.</div>
      </div>
      {err && <div style={{ color: '#ef5350', fontSize: '10px' }}>{err}</div>}
      <div className="panels">
        <div className="panel panel-left">
          <div className="panel-header"><div className="panel-title">ACTIVE API KEYS</div></div>
          <div className="panel-body" style={{ gap: '8px' }}>
            {keys.length === 0 ? (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '20px' }}>No API keys generated yet.</div>
            ) : keys.map(k => (
              <div key={k.id} className="key-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1, minWidth: 0 }}>
                  <div style={{ width: '120px', flexShrink: 0 }}>
                    <div className="key-name" style={{ fontWeight: 700 }}>{k.name}</div>
                    <div style={{ fontSize: '8px', color: 'var(--muted)', marginTop: '2px' }}>EXPIRES: {k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'NEVER'}</div>
                  </div>
                  <div className="key-val" style={{ fontSize: '9px', wordBreak: 'break-all' }}>{k.keyHash}</div>
                  <div className={`chip ${k.active ? 'active' : ''}`} style={{ fontSize: '8px', flexShrink: 0 }}>{k.active ? 'ACTIVE' : 'REVOKED'}</div>
                </div>
                <button className="btn" onClick={() => handleDelete(k.id)} style={{ background: '#ef5350', color: '#fff', border: 'none', padding: '4px 8px', fontSize: '9px', cursor: 'pointer', marginLeft: '12px', flexShrink: 0 }}>DELETE</button>
              </div>
            ))}
          </div>
        </div>
        <div className="panel panel-right">
          <div className="panel-header"><div className="panel-title">GENERATE NEW KEY</div><div className="panel-badge">SECURE</div></div>
          <div className="panel-body">
            <div className="field"><div className="field-label">KEY NAME</div><input type="text" placeholder="my-service-key" value={newKeyName} onChange={e => setNewKeyName(e.target.value)} /></div>
            <button className="btn" onClick={handleGenerate}>+ GENERATE API KEY</button>
            {newKeyVal && (
              <div style={{ border: '1px solid #000', padding: '10px', fontSize: '10px', wordBreak: 'break-all', lineHeight: 1.6, marginTop: '10px' }}>
                <div style={{ fontSize: '9px', color: 'var(--muted)', marginBottom: '4px', letterSpacing: '.08em' }}>NEW API KEY — COPY NOW, SHOWN ONCE</div>
                <div style={{ fontWeight: 700, fontSize: '11px', background: '#f9f9f9', padding: '6px', border: '1px solid #000' }}>{newKeyVal}</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
