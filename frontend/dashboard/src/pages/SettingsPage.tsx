export default function SettingsPage() {
  return (
    <div className="page active">
      <div>
        <div className="page-title">SYSTEM SETTINGS</div>
        <div className="page-sub">Credentials and settings indicators.</div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', overflowY: 'auto', flex: 1 }}>
        <div className="settings-section">
          <div className="settings-header">STORAGE PROFILE (LOCAL ENVIRONMENT ACTIVE)</div>
          <div className="settings-body">
            <div className="settings-row">
              <div className="s-label">FALLBACK FOLDER</div>
              <div className="s-val"><input type="text" disabled value="./storage/" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">DATABASE</div>
              <div className="s-val"><input type="text" disabled value="H2 (InMemory)" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">AUTH PROVIDER</div>
              <div className="s-val"><input type="text" disabled value="JWT / OAuth2" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">ENCRYPTION</div>
              <div className="s-val"><input type="text" disabled value="AES-256-GCM" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">VECTOR INDEX</div>
              <div className="s-val"><input type="text" disabled value="Weaviate 1.21" /></div>
            </div>
          </div>
        </div>

        <div className="settings-section">
          <div className="settings-header">SYSTEM CONFIGURATION</div>
          <div className="settings-body">
            <div className="settings-row">
              <div className="s-label">API GATEWAY</div>
              <div className="s-val"><input type="text" disabled value="Port 8080" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">RUST NATIVE LAYER</div>
              <div className="s-val"><input type="text" disabled value="JNI Bridge Active" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">MESSAGE BROKER</div>
              <div className="s-val"><input type="text" disabled value="RabbitMQ" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">CACHE</div>
              <div className="s-val"><input type="text" disabled value="Redis 7" /></div>
            </div>
            <div className="settings-row">
              <div className="s-label">MODE</div>
              <div className="s-val"><input type="text" disabled value="Student-Researcher" /></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
