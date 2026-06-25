import { useState } from 'react';
import { vectorSearch, type VectorResult } from '../lib/api';

export default function VectorPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<VectorResult[]>([]);
  const [log, setLog] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  function addLog(msg: string) {
    setLog(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);
  }

  async function handleSearch() {
    if (!query.trim()) return;
    setLoading(true);
    setResults([]);
    addLog(`Searching for: "${query}"...`);
    try {
      const data = await vectorSearch(query);
      setResults(data);
      addLog(`Found ${data.length} matching documents.`);
    } catch (e) {
      addLog(`Search failed: ${e}`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page active">
      <div>
        <div className="page-title">VECTOR SEARCH ENGINE</div>
        <div className="page-sub">Perform relevance-based semantic searches across files.</div>
      </div>
      <div className="panels">
        <div className="panel panel-left">
          <div className="panel-header"><div className="panel-title">SEMANTIC QUERY INTERFACE</div><div className="panel-badge">LOCAL_INDEX</div></div>
          <div className="panel-body">
            <div className="field">
              <div className="field-label">QUERY TERM</div>
              <textarea style={{ height: '64px' }} placeholder="Search keywords..." value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleSearch()} />
            </div>
            <div><button className="btn" onClick={handleSearch} disabled={loading}>⊙ EXECUTE VECTOR SEARCH</button></div>
            <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {results.map(r => (
                <div key={r.id} className="result-card">
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '3px' }}>
                    <div className="rc-title">{r.name}</div>
                    <div className="rc-score">{r.score.toFixed(2)} RELEVANCE</div>
                  </div>
                  <div className="rc-meta">
                    <span>Pool: {r.pool}</span>
                    <span>Size: {(r.size / 1024 / 1024).toFixed(2)} MB</span>
                    <span>Type: {r.type}</span>
                  </div>
                </div>
              ))}
              {!loading && results.length === 0 && query && (
                <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '20px' }}>No results found.</div>
              )}
            </div>
          </div>
        </div>
        <div className="panel panel-right">
          <div className="panel-header"><div className="panel-title">INDEX STATUS</div><div className="panel-badge">ACTIVE</div></div>
          <div className="panel-body">
            <div className="console-wrap">
              <div className="console-label">SEARCH EXECUTION LOG</div>
              <div className="console-box" id="vecLog">
                {log.length === 0 && <div className="log-line"><span className="log-ts">--:--:--</span><span className="log-info">Ready. Enter a query to search.</span></div>}
                {log.map((l, i) => <div key={i} className="log-line"><span>{l}</span></div>)}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
