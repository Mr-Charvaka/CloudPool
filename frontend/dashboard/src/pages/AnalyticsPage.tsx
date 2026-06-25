import { useState, useEffect } from 'react';
import { fetchAnalyticsSummary, fetchAnalyticsLogs, type AnalyticsSummary, type AnalyticsLog } from '../lib/api';

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [logs, setLogs] = useState<AnalyticsLog[]>([]);

  useEffect(() => {
    Promise.all([
      fetchAnalyticsSummary().catch(() => null),
      fetchAnalyticsLogs().catch(() => []),
    ]).then(([s, l]) => { setSummary(s); setLogs(l); });
  }, []);

  return (
    <div className="page active">
      <div>
        <div className="page-title">REQUEST TRAFFIC ANALYTICS</div>
        <div className="page-sub">Real-time telemetry, status code distributions, and route metrics for all project API traffic.</div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '12px', marginBottom: '4px' }}>
        {[
          { lbl: 'TOTAL REQUESTS', val: summary?.totalRequests ?? 0, color: '#fff' },
          { lbl: 'AVERAGE LATENCY', val: summary ? `${Math.round(summary.averageLatencyMs)} ms` : '0 ms', color: '#fff' },
          { lbl: 'SUCCESS RATE', val: summary ? `${Math.round(summary.successRate)}%` : '100%', color: '#66bb6a' },
          { lbl: 'SERVER ERRORS (5XX)', val: summary?.errorCount ?? 0, color: '#ef5350' },
        ].map((card, i) => (
          <div key={i} className="panel" style={{ padding: '14px', background: '#1e1e1e', color: '#fff', borderColor: '#333' }}>
            <div style={{ fontSize: '8px', color: '#888', letterSpacing: '.08em', fontWeight: 700 }}>{card.lbl}</div>
            <div style={{ fontSize: '20px', fontWeight: 700, marginTop: '6px', fontFamily: "'JetBrains Mono', monospace", color: card.color }}>{card.val}</div>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', flex: 1, minHeight: 0, overflow: 'hidden' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', overflowY: 'auto' }}>
          <div className="panel" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            <div className="panel-header"><div className="panel-title">REQUESTS BY STATUS CODE</div></div>
            <div className="panel-body" style={{ padding: 0, overflowY: 'auto', flex: 1 }}>
              {summary?.statusDistribution && Object.keys(summary.statusDistribution).length > 0 ? (
                Object.entries(summary.statusDistribution).map(([status, count]) => (
                  <div key={status} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', borderBottom: '1px solid var(--border2)' }}>
                    <div style={{ fontWeight: 700 }}>{status}</div>
                    <div style={{ fontFamily: "'JetBrains Mono', monospace", fontWeight: 700 }}>{count} hits</div>
                  </div>
                ))
              ) : <div style={{ color: 'var(--muted)', padding: '10px', textAlign: 'center' }}>No metrics available.</div>}
            </div>
          </div>
          <div className="panel" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            <div className="panel-header"><div className="panel-title">REQUESTS BY PATH</div></div>
            <div className="panel-body" style={{ padding: 0, overflowY: 'auto', flex: 1 }}>
              {summary?.topPaths && summary.topPaths.length > 0 ? (
                summary.topPaths.map((tp, i) => (
                  <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', borderBottom: '1px solid var(--border2)' }}>
                    <div style={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--muted)', wordBreak: 'break-all', maxWidth: '70%' }}>{tp.path}</div>
                    <div style={{ fontWeight: 700, flexShrink: 0 }}>{tp.count} hits</div>
                  </div>
                ))
              ) : <div style={{ color: 'var(--muted)', padding: '10px', textAlign: 'center' }}>No metrics available.</div>}
            </div>
          </div>
        </div>

        <div className="panel" style={{ display: 'flex', flexDirection: 'column' }}>
          <div className="panel-header"><div className="panel-title">LIVE REQUEST METRIC STREAM</div><div className="panel-badge">REAL-TIME</div></div>
          <div className="panel-body" style={{ flex: 1, overflowY: 'auto', gap: '0' }}>
            {logs.length === 0 ? (
              <div style={{ color: 'var(--muted)', padding: '10px', textAlign: 'center' }}>No request logs recorded yet.</div>
            ) : logs.map(l => (
              <div key={l.id} style={{ borderBottom: '1px solid var(--border2)', padding: '6px 8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0, flex: 1 }}>
                  <span style={{ fontWeight: 700, color: l.requestMethod === 'GET' ? '#42a5f5' : '#ab47bc', width: '40px', flexShrink: 0 }}>{l.requestMethod}</span>
                  <span style={{ wordBreak: 'break-all', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{l.requestPath}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0, marginLeft: '8px' }}>
                  <span style={{ color: 'var(--muted)', fontSize: '8px' }}>{l.durationMs}ms</span>
                  <span style={{ background: l.statusCode >= 500 ? '#ef5350' : l.statusCode >= 400 ? '#ffa726' : '#66bb6a', color: '#fff', padding: '1px 4px', fontSize: '8px', fontWeight: 700 }}>{l.statusCode}</span>
                  <span style={{ color: 'var(--label)', fontSize: '8px' }}>{new Date(l.timestamp).toLocaleTimeString()}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
