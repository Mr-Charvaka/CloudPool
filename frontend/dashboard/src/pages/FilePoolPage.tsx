import { useState, useEffect } from 'react';
import { fetchFiles, downloadFile, type FileMetadata } from '../lib/api';

export default function FilePoolPage() {
  const [files, setFiles] = useState<FileMetadata[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchFiles().then(setFiles).catch(() => setFiles([])).finally(() => setLoading(false));
  }, []);

  const filtered = files.filter(f =>
    f.originalName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="page active" id="page-filepool">
      <div>
        <div className="page-title">FILE POOL EXPLORER</div>
        <div className="page-sub">Browse, search, and manage files across all storage pools.</div>
      </div>
      <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexShrink: 0 }}>
        <div className="search-bar" style={{ flex: 1 }}>
          <input type="text" id="searchFilesInput" placeholder="Search files..." value={search} onChange={e => setSearch(e.target.value)} />
          <button className="btn" style={{ border: '1px solid #000' }} onClick={() => fetchFiles().then(setFiles)}>REFRESH</button>
        </div>
      </div>
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', border: '1px solid var(--border)' }}>
        <div className="panel-header">
          <div className="panel-title">FILE INDEX</div>
        </div>
        <div style={{ overflowY: 'auto', flex: 1 }}>
          <table className="tbl">
            <thead>
              <tr>
                <th>FILE NAME</th>
                <th>POOL</th>
                <th>SIZE</th>
                <th>TYPE</th>
                <th>UPLOADED</th>
                <th>ACTIONS</th>
              </tr>
            </thead>
            <tbody id="fileTable">
              {loading ? (
                <tr><td colSpan={6} style={{ textAlign: 'center', padding: '20px', color: 'var(--muted)' }}>Loading...</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: 'center', padding: '20px', color: 'var(--muted)' }}>{search ? 'No matching files.' : 'No files indexed.'}</td></tr>
              ) : filtered.map(f => (
                <tr key={f.id}>
                  <td>{f.originalName}</td>
                  <td>{f.bucket?.name || '-'}</td>
                  <td>{(f.size / 1024).toFixed(1)} KB</td>
                  <td>{f.mimeType || f.extension || '-'}</td>
                  <td>{new Date(f.createdAt).toLocaleDateString()}</td>
                  <td>
                    <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '9px' }} onClick={() => downloadFile(f.id)}>DL</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
