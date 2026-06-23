import { useState, useEffect, useRef } from 'react';
import { fetchFiles, fetchBuckets, fetchQuota, uploadFile, downloadFile, shareFile, fetchLogs, type FileMetadata, type Bucket, type Quota } from '../lib/api';

export default function DashboardPage() {
  const [files, setFiles] = useState<FileMetadata[]>([]);
  const [buckets, setBuckets] = useState<Bucket[]>([]);
  const [quota, setQuota] = useState<Quota>({ limit: 0, usage: 0 });
  const [logs, setLogs] = useState<Array<{ id: string; action: string; details: string; timestamp: string }>>([]);
  const [selectedBucket, setSelectedBucket] = useState('default-pool');
  const [uploading, setUploading] = useState(false);
  const [statusMsg, setStatusMsg] = useState('Idle');
  const [shareEmail, setShareEmail] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    try {
      const [filesData, bucketsData, quotaData, logsData] = await Promise.all([
        fetchFiles().catch(() => []),
        fetchBuckets().catch(() => []),
        fetchQuota().catch(() => ({ limit: 0, usage: 0 })),
        fetchLogs().catch(() => []),
      ]);
      setFiles(filesData);
      setBuckets(bucketsData);
      setQuota(quotaData);
      setLogs(logsData);
      if (bucketsData.length > 0 && !bucketsData.find(b => b.name === selectedBucket)) {
        setSelectedBucket(bucketsData[0].name);
      }
    } catch (e) {
      setStatusMsg(`Failed to load data: ${e}`);
    }
  }

  async function handleUpload() {
    const input = fileInputRef.current;
    if (!input || !input.files || input.files.length === 0) {
      setStatusMsg('No file selected');
      return;
    }
    setUploading(true);
    setStatusMsg('Uploading...');
    try {
      const result = await uploadFile(input.files[0], selectedBucket);
      setStatusMsg(`Uploaded: ${result.originalName}`);
      input.value = '';
      loadData();
    } catch (e) {
      setStatusMsg(`Upload failed: ${e}`);
    } finally {
      setUploading(false);
    }
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    const input = fileInputRef.current;
    if (input && e.dataTransfer.files.length > 0) {
      input.files = e.dataTransfer.files;
      handleUpload();
    }
  }

  const usagePercent = quota.limit > 0 ? Math.round((quota.usage / quota.limit) * 100) : 0;
  const totalFiles = files.length;
  const lastLog = logs.length > 0 ? logs[0] : null;

  return (
    <div className="page active" id="page-dashboard">
      <div>
        <div className="page-title">FILE POOL INTERFACE</div>
        <div className="page-sub">Manage file uploads to your pooled storage. Select a pool, configure parameters, and push to the distributed storage layer.</div>
      </div>

      <div className="pool-row" id="dashboardPools">
        {buckets.length === 0 ? (
          <div className="pool-card active">
            <div className="tag">DEFAULT</div>
            <div className="name">default-pool</div>
            <div className="drives">Primary storage pool.</div>
            <div className="bar-wrap"><div className="bar" style={{ width: `${usagePercent}%` }}></div></div>
            <div className="sizes"><span>{(quota.usage / 1e9).toFixed(1)} GB USED</span><span>{(quota.limit / 1e9).toFixed(1)} GB TOTAL</span></div>
          </div>
        ) : buckets.map(b => (
          <div key={b.id} className={`pool-card ${b.name === selectedBucket ? 'active' : ''}`} onClick={() => setSelectedBucket(b.name)}>
            <div className="tag">{b.name === selectedBucket ? 'ACTIVE' : 'AVAILABLE'}</div>
            <div className="name">{b.name}</div>
            <div className="drives">{b.description}</div>
            <div className="bar-wrap"><div className="bar" style={{ width: `${usagePercent}%` }}></div></div>
            <div className="sizes"><span>{(quota.usage / 1e9).toFixed(1)} GB USED</span><span>{(quota.limit / 1e9).toFixed(1)} GB TOTAL</span></div>
          </div>
        ))}
      </div>

      <div className="panels">
        <div className="panel panel-left">
          <div className="panel-header">
            <div className="panel-title">FILE UPLOAD INTERFACE</div>
            <div className="panel-badge">POOL_AUTH_ENABLED</div>
          </div>
          <form className="panel-body" onSubmit={(e) => { e.preventDefault(); handleUpload(); }}>
            <div className="field">
              <div className="field-label">FILE IDENTIFIER (NAME)</div>
              <input type="text" id="fileId" placeholder="Click browse to select a file..." value={fileInputRef.current?.files?.[0]?.name || ''} readOnly />
            </div>
            <div className="field-row">
              <div className="field">
                <div className="field-label">TARGET POOL</div>
                <select id="poolSelect" value={selectedBucket} onChange={e => setSelectedBucket(e.target.value)}>
                  {buckets.length === 0 ? <option value="default-pool">default-pool</option> : buckets.map(b => <option key={b.id} value={b.name}>{b.name}</option>)}
                </select>
              </div>
              <div className="field">
                <div className="field-label">VISIBILITY</div>
                <select id="fileVisibility">
                  <option value="PRIVATE">PRIVATE</option>
                  <option value="SHARED">SHARED</option>
                  <option value="PUBLIC">PUBLIC</option>
                </select>
              </div>
            </div>
            <div className="drop-zone" onDragOver={e => e.preventDefault()} onDrop={handleDrop} onClick={() => fileInputRef.current?.click()}>
              <div className="dz-icon">⬆</div>
              <div className="dz-text">{uploading ? 'UPLOADING...' : 'DROP FILE OR CLICK TO BROWSE'}</div>
              <div className="dz-sub">MAX 5 GB · PDF, DOCX, PNG, ZIP, CSV, TXT SUPPORTED</div>
              <input type="file" id="fileInput" ref={fileInputRef} style={{ display: 'none' }} onChange={() => handleUpload()} />
            </div>
            <div>
              <button type="submit" className="btn" disabled={uploading}>⬆ PUSH TO POOL INSTANCE</button>
            </div>
          </form>

          {files.length > 0 && (
            <div style={{ marginTop: '16px', borderTop: '1px solid var(--border2)', paddingTop: '12px' }}>
              <div className="panel-header">
                <div className="panel-title">RECENT FILES</div>
              </div>
              <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
                {files.map(f => (
                  <div key={f.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--border2)', fontSize: '11px' }}>
                    <div style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.originalName}</div>
                    <div style={{ color: 'var(--muted)', margin: '0 12px' }}>{(f.size / 1024).toFixed(1)} KB</div>
                    <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '9px' }} onClick={() => downloadFile(f.id)}>DL</button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="panel panel-right">
          <div className="panel-header">
            <div className="panel-title">POOL EXECUTION STATUS</div>
            <div className="panel-badge" id="statusBadge">{uploading ? 'BUSY' : 'ACTIVE'}</div>
          </div>
          <div className="panel-body">
            <div>
              <div className="stat-row"><div className="stat-key">ACTIVE POOL</div><div className="stat-val" id="activePanelPool">{selectedBucket}</div></div>
              <div className="stat-row"><div className="stat-key">TOTAL USER FILES</div><div className="stat-val" id="mFilesCount">{totalFiles}</div></div>
              <div className="stat-row"><div className="stat-key">LAST SYNC</div><div className="stat-val">{lastLog ? new Date(lastLog.timestamp).toLocaleTimeString() : 'N/A'}</div></div>
              <div className="stat-row"><div className="stat-key">ENCRYPTION</div><div className="stat-val">AES-256</div></div>
            </div>
            <div className="console-wrap">
              <div className="console-label">POOL OPERATION LOG</div>
              <div className="console-box" id="consoleBox">
                {logs.slice(0, 5).map(log => (
                  <div key={log.id} className="log-line">
                    <span className="log-ts">{new Date(log.timestamp).toLocaleTimeString()}</span>
                    <span className="log-info">[{log.action}]</span>
                    <span>{log.details}</span>
                  </div>
                ))}
                {logs.length === 0 && (
                  <div className="log-line"><span className="log-ts">--:--:--</span><span className="log-info">[SYS]</span><span>Pool connection established</span></div>
                )}
              </div>
            </div>
          </div>
          <div className="metric-strip">
            <div className="metric-cell"><div className="m-val" id="mStatus">{uploading ? 'BUSY' : 'OK'}</div><div className="m-lbl">STATUS</div></div>
            <div className="metric-cell"><div className="m-val" id="mLatency">{quota.limit > 0 ? `${usagePercent}%` : '0%'}</div><div className="m-lbl">QUOTA USED</div></div>
            <div className="metric-cell"><div className="m-val" id="mIndexedCount">{totalFiles}</div><div className="m-lbl">FILES INDEXED</div></div>
            <div className="metric-cell"><div className="m-val" id="mUserMail">@</div><div className="m-lbl">ACTIVE DEV</div></div>
          </div>
        </div>
      </div>
    </div>
  );
}
