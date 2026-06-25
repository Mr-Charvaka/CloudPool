import { useState, useEffect } from 'react';
import { fetchBuckets, fetchStaticSites, deployStaticSite, deleteStaticSite, fetchServerlessFunctions, deployServerlessFunction, deleteServerlessFunction, executeServerlessFunction, fetchContainers, deployContainer, deleteContainer, scaleContainer, fetchContainerLogs, type Bucket, type StaticSite, type ServerlessFunction, type ContainerDeployment } from '../lib/api';

type ComputeTab = 'hub' | 'static' | 'serverless' | 'container';

export default function ComputePage() {
  const [tab, setTab] = useState<ComputeTab>('hub');

  return (
    <div className="page active">
      {tab === 'hub' && <ComputeHub onSelect={setTab} />}
      {tab === 'static' && <StaticConsole onBack={() => setTab('hub')} />}
      {tab === 'serverless' && <ServerlessConsole onBack={() => setTab('hub')} />}
      {tab === 'container' && <ContainerConsole onBack={() => setTab('hub')} />}
    </div>
  );
}

function ComputeHub({ onSelect }: { onSelect: (t: ComputeTab) => void }) {
  const items = [
    { id: 'static' as ComputeTab, tag: 'STATIC', tagBg: '#ea580c', name: 'STATIC SITE HOSTING', desc: 'Deploy React, Vue, Next.js exports, or plain HTML/CSS static assets served at sub-millisecond edge latency from storage pools.' },
    { id: 'serverless' as ComputeTab, tag: 'EDGE_FUNC', tagBg: '#2563eb', name: 'SERVERLESS FUNCTIONS', desc: 'Write serverless logic compiled into isolated WebAssembly runtimes. Execute dynamic backend handlers securely in isolated sandboxes.' },
    { id: 'container' as ComputeTab, tag: 'CONTAINERS', tagBg: '#16a34a', name: 'CONTAINER HOSTING', desc: 'Deploy full Docker containers or custom backend workloads onto our high-availability Kubernetes runtime environment.' },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
      <div>
        <div className="page-title">PaaS COMPUTE HUB</div>
        <div className="page-sub">Deploy application workloads, serverless functions, and static assets onto CloudPool's hosting platform.</div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginTop: '20px' }}>
        {items.map(item => (
          <div key={item.id} className="pool-card" style={{ padding: '18px 16px', display: 'flex', flexDirection: 'column', gap: '8px', cursor: 'pointer' }} onClick={() => onSelect(item.id)}>
            <div style={{ background: item.tagBg, color: 'white', width: 'fit-content', padding: '2px 6px', fontSize: '9px', fontWeight: 600 }}>{item.tag}</div>
            <div className="name" style={{ fontSize: '14px', fontWeight: 600, marginTop: '4px' }}>{item.name}</div>
            <div className="drives" style={{ fontSize: '11px', lineHeight: 1.4 }}>{item.desc}</div>
            <div style={{ marginTop: 'auto', paddingTop: '10px' }}><button className="btn btn-outline" style={{ width: '100%', justifyContent: 'center' }}>OPEN CONSOLE</button></div>
          </div>
        ))}
      </div>
    </div>
  );
}

function StaticConsole({ onBack }: { onBack: () => void }) {
  const [buckets, setBuckets] = useState<Bucket[]>([]);
  const [sites, setSites] = useState<StaticSite[]>([]);
  const [name, setName] = useState('');
  const [bucketName, setBucketName] = useState('');
  const [domain, setDomain] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => {
    fetchBuckets().then(setBuckets).catch(() => {});
    fetchStaticSites().then(setSites).catch(() => {});
  }, []);

  async function handleDeploy() {
    if (!name || !domain) { setErr('Name and domain required'); return; }
    try {
      await deployStaticSite(name, bucketName || buckets[0]?.name || 'default', domain);
      setName(''); setDomain('');
      setSites(await fetchStaticSites());
    } catch (e) { setErr(`Deploy failed: ${e}`); }
  }

  async function handleDelete(id: string) {
    if (!confirm('Undeploy this static site?')) return;
    try { await deleteStaticSite(id); setSites(await fetchStaticSites()); }
    catch (e) { setErr(`Delete failed: ${e}`); }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div className="page-title">STATIC SITE HOSTING CONSOLE</div>
          <div className="page-sub">Expose storage pool buckets as static websites mapped to developer domains.</div>
        </div>
        <button className="btn btn-outline" style={{ padding: '4px 12px' }} onClick={onBack}>← SWITCH OPTION</button>
      </div>
      {err && <div style={{ color: '#ef5350', fontSize: '10px' }}>{err}</div>}
      <div className="panels">
        <div className="panel panel-left" style={{ flex: 1 }}>
          <div className="panel-header"><div className="panel-title">DEPLOY NEW STATIC SITE</div></div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="field"><div className="field-label">DEPLOYMENT NAME</div><input type="text" placeholder="my-awesome-frontend" value={name} onChange={e => setName(e.target.value)} /></div>
            <div className="field"><div className="field-label">STORAGE BUCKET</div><select value={bucketName} onChange={e => setBucketName(e.target.value)}>{buckets.map(b => <option key={b.id} value={b.name}>{b.name}</option>)}</select></div>
            <div className="field"><div className="field-label">CUSTOM DOMAIN</div><input type="text" placeholder="my-app.cloudpool.dev" value={domain} onChange={e => setDomain(e.target.value)} /></div>
            <div><button className="btn" onClick={handleDeploy}>⊙ DEPLOY SITE</button></div>
          </div>
        </div>
        <div className="panel panel-right" style={{ flex: 1.5 }}>
          <div className="panel-header"><div className="panel-title">ACTIVE STATIC DEPLOYMENTS</div></div>
          <div className="panel-body" style={{ gap: '8px' }}>
            {sites.length === 0 ? (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '20px' }}>No static deployments.</div>
            ) : sites.map(s => (
              <div key={s.id} style={{ border: '1px solid var(--border2)', padding: '10px 12px', borderRadius: '4px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: '12px' }}>{s.name}</div>
                    <div style={{ fontSize: '10px', color: 'var(--muted)' }}>Domain: {s.domain}</div>
                    <div style={{ fontSize: '10px', color: 'var(--muted)' }}>Bucket: {s.bucketName}</div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span className="chip active">{s.status}</span>
                    <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '9px', borderColor: '#ef5350', color: '#ef5350' }} onClick={() => handleDelete(s.id)}>UNDEPLOY</button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ServerlessConsole({ onBack }: { onBack: () => void }) {
  const [funcs, setFuncs] = useState<ServerlessFunction[]>([]);
  const [name, setName] = useState('');
  const [route, setRoute] = useState('');
  const [code, setCode] = useState('');
  const [activeFuncId, setActiveFuncId] = useState<string | null>(null);
  const [activeFuncName, setActiveFuncName] = useState('');
  const [testParams, setTestParams] = useState('{"name":"Developer Alpha"}');
  const [funcLogs, setFuncLogs] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => { loadFuncs(); }, []);

  async function loadFuncs() {
    try { setFuncs(await fetchServerlessFunctions()); }
    catch { setErr('Failed to load functions'); }
  }

  async function handleDeploy() {
    if (!name || !route || !code) { setErr('All fields required'); return; }
    try {
      await deployServerlessFunction(name, route, code);
      setName(''); setRoute(''); setCode('');
      await loadFuncs();
    } catch (e) { setErr(`Deploy failed: ${e}`); }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this function?')) return;
    try {
      await deleteServerlessFunction(id);
      if (activeFuncId === id) { setActiveFuncId(null); setActiveFuncName(''); setFuncLogs(''); }
      await loadFuncs();
    } catch (e) { setErr(`Delete failed: ${e}`); }
  }

  async function handleTest() {
    if (!activeFuncId) return;
    let params: Record<string, unknown> = {};
    try { params = JSON.parse(testParams); }
    catch { setErr('Invalid JSON params'); return; }
    try {
      const result = await executeServerlessFunction(activeFuncId, params);
      setFuncLogs(result.executionOutput);
    } catch (e) { setFuncLogs(`Execution failed: ${e}`); }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div className="page-title">SERVERLESS FUNCTIONS RUNTIME</div>
          <div className="page-sub">Write isolated backend services running inside execution runtimes.</div>
        </div>
        <button className="btn btn-outline" style={{ padding: '4px 12px' }} onClick={onBack}>← SWITCH OPTION</button>
      </div>
      {err && <div style={{ color: '#ef5350', fontSize: '10px' }}>{err}</div>}
      <div className="panels">
        <div className="panel panel-left" style={{ flex: 1.4 }}>
          <div className="panel-header"><div className="panel-title">CREATE SERVERLESS FUNCTION</div></div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="field-row" style={{ display: 'flex', gap: '12px' }}>
              <div className="field" style={{ flex: 1 }}><div className="field-label">FUNCTION NAME</div><input type="text" placeholder="hello-world" value={name} onChange={e => setName(e.target.value)} /></div>
              <div className="field" style={{ flex: 1 }}><div className="field-label">TRIGGER ROUTE</div><input type="text" placeholder="/api/hello" value={route} onChange={e => setRoute(e.target.value)} /></div>
            </div>
            <div className="field"><div className="field-label">CODE EDITOR</div><textarea className="code-editor" style={{ height: '220px' }} placeholder="// Write function logic&#10;let parsed = JSON.parse(params);&#10;let name = parsed.name || 'Developer';&#10;'Greetings ' + name + ' from isolated CloudPool Serverless container!'" value={code} onChange={e => setCode(e.target.value)} /></div>
            <div><button className="btn" onClick={handleDeploy}>⊙ DEPLOY EDGE FUNCTION</button></div>
          </div>
        </div>
        <div className="panel panel-right" style={{ flex: 1.1 }}>
          <div className="panel-header"><div className="panel-title">DEPLOYED FUNCTIONS</div></div>
          <div className="panel-body" style={{ gap: '8px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', overflowY: 'auto', maxHeight: '200px' }}>
              {funcs.length === 0 ? (
                <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '10px' }}>No functions deployed.</div>
              ) : funcs.map(f => (
                <div key={f.id} onClick={() => { setActiveFuncId(f.id); setActiveFuncName(f.name); setFuncLogs(''); }} style={{ border: activeFuncId === f.id ? '1px solid #000' : '1px solid var(--border2)', padding: '8px 10px', cursor: 'pointer', borderRadius: '4px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '11px' }}>{f.name}</div>
                      <div style={{ fontSize: '9px', color: 'var(--muted)' }}>{f.triggerRoute}</div>
                    </div>
                    <button className="btn btn-outline" style={{ padding: '1px 6px', fontSize: '9px', borderColor: '#ef5350', color: '#ef5350' }} onClick={e => { e.stopPropagation(); handleDelete(f.id); }}>DEL</button>
                  </div>
                </div>
              ))}
            </div>
            <div className="console-wrap" style={{ flex: 1 }}>
              <div className="console-label">SANDBOX TEST CONSOLE</div>
              <div className="field" style={{ marginTop: '4px' }}><div className="field-label">TEST PARAMETERS (JSON)</div><input type="text" value={testParams} onChange={e => setTestParams(e.target.value)} style={{ fontFamily: 'monospace', fontSize: '10px' }} /></div>
              <button className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '10px', margin: '4px 0' }} onClick={handleTest} disabled={!activeFuncId}>RUN {activeFuncName ? `[${activeFuncName}]` : 'TEST'}</button>
              <div className="console-box" style={{ height: '100px', fontSize: '10px', overflowY: 'auto', whiteSpace: 'pre-wrap' }}>{funcLogs || 'Select a function and click RUN TEST.'}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function ContainerConsole({ onBack }: { onBack: () => void }) {
  const [containers, setContainers] = useState<ContainerDeployment[]>([]);
  const [name, setName] = useState('');
  const [image, setImage] = useState('');
  const [replicas, setReplicas] = useState(1);
  const [cpu, setCpu] = useState(0.5);
  const [memory, setMemory] = useState(512);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [logOutput, setLogOutput] = useState('');
  const [err, setErr] = useState('');

  useEffect(() => { loadContainers(); }, []);

  async function loadContainers() {
    try { setContainers(await fetchContainers()); }
    catch { setErr('Failed to load containers'); }
  }

  async function handleDeploy() {
    if (!name || !image) { setErr('Name and image required'); return; }
    try {
      await deployContainer(name, image, replicas, cpu, memory);
      setName(''); setImage('');
      await loadContainers();
    } catch (e) { setErr(`Deploy failed: ${e}`); }
  }

  async function handleSelect(id: string) {
    setActiveId(id);
    try {
      const { logs } = await fetchContainerLogs(id);
      setLogOutput(logs);
    } catch { setLogOutput('No logs available.'); }
  }

  async function handleScale(id: string, count: number) {
    try { await scaleContainer(id, count); await loadContainers(); }
    catch (e) { setErr(`Scale failed: ${e}`); }
  }

  async function handleDelete(id: string) {
    if (!confirm('Undeploy this container?')) return;
    try {
      await deleteContainer(id);
      if (activeId === id) { setActiveId(null); setLogOutput(''); }
      await loadContainers();
    } catch (e) { setErr(`Delete failed: ${e}`); }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div className="page-title">CONTAINER ORCHESTRATION CONSOLE</div>
          <div className="page-sub">Deploy long-lived servers, Node.js applications, or Docker containers onto Kubernetes clusters.</div>
        </div>
        <button className="btn btn-outline" style={{ padding: '4px 12px' }} onClick={onBack}>← SWITCH OPTION</button>
      </div>
      {err && <div style={{ color: '#ef5350', fontSize: '10px' }}>{err}</div>}
      <div className="panels">
        <div className="panel panel-left" style={{ flex: 1 }}>
          <div className="panel-header"><div className="panel-title">PROVISION CONTAINER INSTANCE</div></div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div className="field"><div className="field-label">DEPLOYMENT NAME</div><input type="text" placeholder="api-server-prod" value={name} onChange={e => setName(e.target.value)} /></div>
            <div className="field"><div className="field-label">DOCKER IMAGE</div><input type="text" placeholder="node:18-alpine" value={image} onChange={e => setImage(e.target.value)} /></div>
            <div className="field">
              <div className="field-label">REPLICAS COUNT ({replicas})</div>
              <input type="range" min={1} max={10} value={replicas} onChange={e => setReplicas(Number(e.target.value))} style={{ width: '100%', cursor: 'pointer' }} />
            </div>
            <div className="field-row" style={{ display: 'flex', gap: '12px' }}>
              <div className="field" style={{ flex: 1 }}><div className="field-label">CPU CORES: {cpu}</div><input type="range" min={0.1} max={4.0} step={0.1} value={cpu} onChange={e => setCpu(Number(e.target.value))} style={{ width: '100%', cursor: 'pointer' }} /></div>
              <div className="field" style={{ flex: 1 }}><div className="field-label">MEMORY MB: {memory}</div><input type="range" min={128} max={4096} step={128} value={memory} onChange={e => setMemory(Number(e.target.value))} style={{ width: '100%', cursor: 'pointer' }} /></div>
            </div>
            <div><button className="btn" onClick={handleDeploy}>⊙ LAUNCH WORKLOAD</button></div>
          </div>
        </div>
        <div className="panel panel-right" style={{ flex: 1.3 }}>
          <div className="panel-header"><div className="panel-title">RUNNING CONTAINER WORKLOADS</div></div>
          <div className="panel-body" style={{ gap: '12px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', overflowY: 'auto', maxHeight: '200px' }}>
              {containers.length === 0 ? (
                <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '10px' }}>No container workloads.</div>
              ) : containers.map(c => (
                <div key={c.id} onClick={() => handleSelect(c.id)} style={{ border: activeId === c.id ? '1px solid #000' : '1px solid var(--border2)', padding: '8px 10px', cursor: 'pointer', borderRadius: '4px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '11px' }}>{c.name}</div>
                      <div style={{ fontSize: '9px', color: 'var(--muted)' }}>{c.dockerImage} | {c.cpu}vCPU, {c.memory}MB</div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span className="chip active">{c.status}</span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <span style={{ fontSize: '9px', color: 'var(--muted)' }}>R:</span>
                        <input type="number" min={1} max={10} value={c.replicas} onChange={e => handleScale(c.id, Number(e.target.value))} style={{ width: '36px', padding: '2px', fontSize: '9px' }} />
                      </div>
                      <button className="btn btn-outline" style={{ padding: '1px 6px', fontSize: '9px', borderColor: '#ef5350', color: '#ef5350' }} onClick={e => { e.stopPropagation(); handleDelete(c.id); }}>DEL</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="console-wrap" style={{ flex: 1 }}>
              <div className="console-label">CONTAINER LOGS</div>
              <div className="console-box" style={{ height: '140px', fontSize: '9px', overflowY: 'auto', whiteSpace: 'pre-wrap' }}>{logOutput || 'Select a container to view logs.'}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
