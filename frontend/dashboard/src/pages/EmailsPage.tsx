import { useState, useEffect } from 'react';
import { fetchEmailOutbox, fetchEmailInbox, clearEmailOutbox, clearEmailInbox, sendTestEmail, type EmailItem } from '../lib/api';

export default function EmailsPage() {
  const [tab, setTab] = useState<'outbox' | 'inbox'>('outbox');
  const [outbox, setOutbox] = useState<EmailItem[]>([]);
  const [inbox, setInbox] = useState<EmailItem[]>([]);
  const [to, setTo] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => { loadTab(tab); }, [tab]);

  async function loadTab(t: string) {
    try {
      if (t === 'outbox') setOutbox(await fetchEmailOutbox());
      else setInbox(await fetchEmailInbox());
    } catch { setErr('Failed to load emails'); }
  }

  async function handleClearOutbox() {
    if (!confirm('Clear all outbox emails?')) return;
    try { await clearEmailOutbox(); setOutbox(await fetchEmailOutbox()); }
    catch (e) { setErr(`Clear failed: ${e}`); }
  }

  async function handleClearInbox() {
    if (!confirm('Clear all inbox emails?')) return;
    try { await clearEmailInbox(); setInbox(await fetchEmailInbox()); }
    catch (e) { setErr(`Clear failed: ${e}`); }
  }

  async function handleSend() {
    if (!to) { setErr('Recipient email required'); return; }
    try {
      await sendTestEmail(to, subject || '(no subject)', body || '(no body)');
      setTo(''); setSubject(''); setBody('');
      loadTab(tab);
    } catch (e) { setErr(`Send failed: ${e}`); }
  }

  const items = tab === 'outbox' ? outbox : inbox;

  return (
    <div className="page active">
      <div>
        <div className="page-title">LOCAL EMAIL SANDBOX</div>
        <div className="page-sub">Monitor and audit emails dispatched by CloudPool. In Sandbox mode, emails are stored locally instead of sent to external SMTP servers.</div>
      </div>
      {err && <div style={{ color: '#ef5350', fontSize: '10px' }}>{err}</div>}
      <div className="panels">
        <div className="panel panel-left" style={{ display: 'flex', flexDirection: 'column' }}>
          <div className="panel-header" style={{ padding: '0 12px', display: 'flex', alignItems: 'stretch', height: '32px', flexShrink: 0 }}>
            <div style={{ display: 'flex', gap: '16px', alignItems: 'stretch' }}>
              <div onClick={() => setTab('outbox')} style={{ fontSize: '10px', fontWeight: 600, letterSpacing: '0.1em', display: 'flex', alignItems: 'center', borderBottom: tab === 'outbox' ? '2px solid #000' : '2px solid transparent', cursor: 'pointer', padding: '0 4px', userSelect: 'none', color: tab === 'outbox' ? '#000' : 'var(--muted)' }}>DISPATCHED OUTBOX</div>
              <div onClick={() => setTab('inbox')} style={{ fontSize: '10px', fontWeight: 600, letterSpacing: '0.1em', display: 'flex', alignItems: 'center', borderBottom: tab === 'inbox' ? '2px solid #000' : '2px solid transparent', cursor: 'pointer', padding: '0 4px', userSelect: 'none', color: tab === 'inbox' ? '#000' : 'var(--muted)' }}>RECEIVED INBOX</div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: 'auto' }}>
              {tab === 'outbox' ? (
                <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '8px' }} onClick={handleClearOutbox}>CLEAR OUTBOX</button>
              ) : (
                <>
                  <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '8px' }} onClick={handleClearInbox}>CLEAR INBOX</button>
                  <button className="btn" style={{ padding: '2px 8px', fontSize: '8px' }} onClick={() => loadTab(tab)}>🔄 RELOAD</button>
                </>
              )}
            </div>
          </div>
          <div className="panel-body" style={{ padding: 0, overflowY: 'auto', flex: 1 }}>
            {items.length === 0 ? (
              <div style={{ color: 'var(--muted)', padding: '14px', textAlign: 'center' }}>
                {tab === 'outbox' ? 'No emails in the sandbox outbox log yet.' : 'No emails in the inbox yet.'}
              </div>
            ) : items.map((email, i) => (
              <div key={email.id || i} style={{ borderBottom: '1px solid var(--border2)', padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ fontWeight: 700, fontSize: '10px' }}>{tab === 'outbox' ? `TO: ${email.toAddress}` : `FROM: ${email.fromAddress || email.toAddress}`}</div>
                  {email.status && (
                    <span style={{ background: email.status === 'FAILED' ? '#ef5350' : email.status === 'QUEUED' ? '#ffa726' : '#66bb6a', color: '#fff', padding: '1px 6px', fontSize: '8px', fontWeight: 700 }}>{email.status}</span>
                  )}
                </div>
                <div style={{ fontWeight: 600, fontSize: '9px' }}>SUBJECT: {email.subject}</div>
                <div style={{ fontSize: '9px', color: 'var(--muted)', whiteSpace: 'pre-wrap', background: '#f9f9f9', padding: '6px', border: '1px solid #eee', marginTop: '2px' }}>{email.body}</div>
                {email.errorMessage && <div style={{ fontSize: '8px', color: '#ef5350', fontWeight: 700 }}>ERROR: {email.errorMessage}</div>}
                <div style={{ fontSize: '8px', color: 'var(--label)', textAlign: 'right' }}>
                  {tab === 'outbox' ? `DISPATCHED AT: ${email.sentAt ? new Date(email.sentAt).toLocaleString() : ''}` : `RECEIVED AT: ${email.receivedAt ? new Date(email.receivedAt).toLocaleString() : ''}`}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="panel panel-right">
          <div className="panel-header"><div className="panel-title">TRIGGER TEST EMAIL</div><div className="panel-badge">LOCAL/SMTP</div></div>
          <div className="panel-body">
            <div className="field"><div className="field-label">RECIPIENT EMAIL</div><input type="text" placeholder="receiver@example.com" value={to} onChange={e => setTo(e.target.value)} /></div>
            <div className="field"><div className="field-label">EMAIL SUBJECT</div><input type="text" placeholder="Welcome to CloudPool!" value={subject} onChange={e => setSubject(e.target.value)} /></div>
            <div className="field"><div className="field-label">EMAIL BODY (PLAIN TEXT)</div><textarea style={{ height: '120px' }} placeholder="Test notification body..." value={body} onChange={e => setBody(e.target.value)} /></div>
            <button className="btn" onClick={handleSend}>⚡ SEND TEST EMAIL</button>
          </div>
        </div>
      </div>
    </div>
  );
}
