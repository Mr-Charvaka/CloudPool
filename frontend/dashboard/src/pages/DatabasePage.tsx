import { useState, useEffect } from 'react';

interface DevTable {
  id: string;
  name: string;
  displayName?: string;
  description?: string;
}

interface TableField {
  id: string;
  name: string;
  fieldType: string;
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = localStorage.getItem('cp_token');
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(path, { ...init, headers, credentials: 'include' });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  if (res.status === 204) return undefined as T;
  return res.json();
}

export default function DatabasePage() {
  const [selectedDB, setSelectedDB] = useState<string | null>(null);
  const [tables, setTables] = useState<DevTable[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [fields, setFields] = useState<TableField[]>([]);
  const [records, setRecords] = useState<Record<string, unknown>[]>([]);
  const [log, setLog] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (selectedDB && ['H2', 'POSTGRESQL'].includes(selectedDB)) {
      setLoading(true);
      api<DevTable[]>('/api/v1/db/tables')
        .then(data => { setTables(data); addLog(`Loaded ${data.length} tables`); })
        .catch(e => addLog(`Error: ${e}`))
        .finally(() => setLoading(false));
    }
  }, [selectedDB]);

  function addLog(msg: string) {
    setLog(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);
  }

  async function loadFields(tableId: string) {
    try {
      const data = await api<TableField[]>(`/api/v1/db/tables/${tableId}/fields`);
      setFields(data);
      setSelectedTable(tableId);
      addLog(`Loaded ${data.length} fields`);
    } catch (e) {
      addLog(`Failed to load fields: ${e}`);
    }
  }

  async function loadRecords(tableId: string) {
    setLoading(true);
    try {
      const data = await api<Record<string, unknown>[]>(`/api/v1/db/tables/${tableId}/records`);
      setRecords(data);
      addLog(`Queried ${data.length} records`);
    } catch (e) {
      addLog(`Query failed: ${e}`);
    } finally {
      setLoading(false);
    }
  }

  async function handleQuery(tableId: string) {
    await loadFields(tableId);
    await loadRecords(tableId);
  }

  if (selectedDB) {
    return (
      <div className="page active" id="page-database">
        <div id="dbQueryWorkspace" style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <div className="page-title" id="dbWorkspaceTitle">DATABASE QUERY INTERFACE — {selectedDB}</div>
              <div className="page-sub" id="dbWorkspaceSub">Execute queries against your database.</div>
            </div>
            <button className="btn btn-outline" style={{ padding: '4px 12px' }} onClick={() => setSelectedDB(null)}>← SWITCH DATABASE</button>
          </div>

          {tables.length > 0 && (
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              {tables.map(t => (
                <button
                  key={t.id}
                  className={`btn ${selectedTable === t.id ? '' : 'btn-outline'}`}
                  style={{ padding: '4px 12px', fontSize: '10px' }}
                  onClick={() => handleQuery(t.id)}
                >
                  {t.displayName || t.name}
                </button>
              ))}
            </div>
          )}

          <div className="panels">
            <div className="panel panel-left">
              <div className="panel-header">
                <div className="panel-title">TABLE BROWSER</div>
                <div className="panel-badge" id="dbBadgeStatus">{selectedTable ? 'CONNECTED' : 'IDLE'}</div>
              </div>
              <div className="panel-body" style={{ overflow: 'auto' }}>
                {loading ? <div style={{ padding: '12px', color: 'var(--muted)' }}>Loading...</div> : records.length === 0 ? (
                  <div style={{ padding: '12px', color: 'var(--muted)' }}>{selectedTable ? 'No records found.' : 'Select a table above to query.'}</div>
                ) : (
                  <table className="tbl" id="queryResults">
                    <thead id="queryHeaders">
                      <tr>{Object.keys(records[0]).map(k => <th key={k}>{k}</th>)}</tr>
                    </thead>
                    <tbody id="queryRows">
                      {records.map((r, i) => (
                        <tr key={i}>{Object.values(r).map((v, j) => <td key={j}>{String(v ?? '')}</td>)}</tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
            <div className="panel panel-right">
              <div className="panel-header">
                <div className="panel-title">EXECUTION STATUS</div>
                <div className="panel-badge" id="dbBadge">{loading ? 'RUNNING' : 'IDLE'}</div>
              </div>
              <div className="panel-body">
                <div className="console-wrap">
                  <div className="console-label">QUERY EXECUTION LOG</div>
                  <div className="console-box" id="dbLog">
                    {log.map((l, i) => <div key={i} className="log-line"><span>{l}</span></div>)}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page active" id="page-database">
      <div id="dbSelectionScreen" style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
        <div>
          <div className="page-title">DATABASE SELECTION HUB</div>
          <div className="page-sub">Select a database instance to connect, execute queries, and inspect schemas.</div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginTop: '20px' }}>
          {[
            { id: 'H2', tag: 'LOCAL', name: 'H2 LOCAL ENGINE', desc: 'Active in-memory / local file relational store. Perfect for rapid prototyping.' },
            { id: 'POSTGRESQL', tag: 'RDS_ACTIVE', name: 'POSTGRESQL INSTANCE', desc: 'Production-grade relational server. Supports advanced indexing and replication.' },
            { id: 'REDIS', tag: 'KVS_ACTIVE', name: 'REDIS KEY-VALUE CACHE', desc: 'In-memory data structure store.' },
            { id: 'GRAPHQL', tag: 'API_LAYER', name: 'GRAPHQL ENGINE', desc: 'Unified schema endpoint console.' },
          ].map(db => (
            <div key={db.id} className="pool-card" style={{ padding: '18px 16px', display: 'flex', flexDirection: 'column', gap: '8px' }} onClick={() => setSelectedDB(db.id)}>
              <div className="tag">{db.tag}</div>
              <div className="name" style={{ fontSize: '12px' }}>{db.name}</div>
              <div className="drives">{db.desc}</div>
              <div style={{ marginTop: 'auto', paddingTop: '10px' }}><button className="btn btn-outline" style={{ width: '100%', justifyContent: 'center' }}>CONNECT</button></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
