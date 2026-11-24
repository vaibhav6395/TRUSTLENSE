import { FormEvent, useEffect, useState } from 'react';
import {
  Activity, ArrowUpRight, BookOpen, Check, ChevronRight, CircleHelp,
  FileLock2, FileText, Fingerprint, History, LayoutDashboard, LockKeyhole,
  LogOut, RefreshCw, ShieldAlert, ShieldCheck, SlidersHorizontal, Users
} from 'lucide-react';
import {
  apiGet, apiPost, clearCsrf, type AuditItem, type CurrentUser,
  type DecisionItem, type DocumentItem, type Health
} from './api';

type Page = 'Overview' | 'Documents' | 'People & roles' | 'Policy sandbox' | 'Decisions' | 'Audit history';
type LoginResult = { authenticated: boolean; username: string; authorities: string[] };
type AccessResult = { allowed: boolean; outcome: string; reasonCode: string; explanation: string; decisionId: string };

function Badge({ children, tone = 'neutral' }: { children: string; tone?: 'neutral' | 'green' | 'amber' | 'red' }) {
  return <span className={`badge ${tone}`}><i />{children}</span>;
}
function SectionHeading({ eyebrow, title, detail }: { eyebrow: string; title: string; detail?: string }) {
  return <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2></div>{detail && <span className="muted">{detail}</span>}</div>;
}
function Login({ onLogin }: { onLogin: (username: string, password: string) => Promise<void> }) {
  const [username, setUsername] = useState('demo');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { await onLogin(username, password); }
    catch (err) { setError(err instanceof Error ? err.message : 'Sign-in failed'); }
    finally { setBusy(false); }
  }
  return <main className="login-shell"><form className="login-card" onSubmit={submit}>
    <div className="brand-mark login-mark"><ShieldCheck size={25}/></div>
    <span className="eyebrow">CAMPUS SECURITY PLATFORM</span>
    <h1>TRUSTLENS</h1>
    <p>Sign in to inspect protected document access and security decisions.</p>
    <label>Username<input autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} required/></label>
    <label>Password<input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required/></label>
    {error && <div className="connection-notice"><ShieldAlert size={15}/>{error}</div>}
    <button className="primary full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'} <ArrowUpRight size={16}/></button>
    <div className="login-hint">Local demo account: <strong>demo</strong> / <strong>TrustLensDemo#2026</strong></div>
  </form></main>;
}
function Overview({ health, documents, decisions, audit, setPage }: {
  health: Health | null; documents: DocumentItem[]; decisions: DecisionItem[];
  audit: AuditItem[]; setPage: (p: Page) => void;
}) {
  const stats = [
    { label: 'Visible documents', value: documents.length, icon: FileLock2 },
    { label: 'Recorded decisions', value: decisions.length, icon: Fingerprint },
    { label: 'Audit events', value: audit.length, icon: History },
    { label: 'API status', value: health?.status ?? '—', icon: Activity },
  ];
  return <>
    <section className="hero"><div className="hero-copy"><span className="hero-tag"><ShieldCheck size={14}/> SERVER-SIDE ACCESS GOVERNANCE</span>
      <h2>Trust every request.<br/><em>Verify every decision.</em></h2>
      <p>TRUSTLENS evaluates document requests using the authenticated identity and current server-side grant state.</p>
      <button className="primary" onClick={() => setPage('Documents')}>Explore documents <ArrowUpRight size={16}/></button>
    </div><div className="hero-art" aria-hidden="true"><div className="ring ring-one"/><div className="ring ring-two"/><div className="shield-core"><ShieldCheck size={54}/></div><div className="orbit orbit-a"><LockKeyhole size={17}/></div><div className="orbit orbit-b"><Fingerprint size={18}/></div><div className="orbit orbit-c"><Activity size={16}/></div></div></section>
    <div className="notice"><CircleHelp size={16}/><span>Live backend vertical slice: session login, visible document listing, server-side access decisions and audit events. Policy simulation remains a prototype.</span></div>
    <SectionHeading eyebrow="LIVE SYSTEM SNAPSHOT" title="Governance at a glance"/>
    <section className="metrics">{stats.map(({label,value,icon:Icon}) => <article className="metric" key={label}><div className="metric-top"><span>{label}</span><Icon size={18}/></div><strong>{value}</strong><small>Loaded from the connected API</small></article>)}</section>
    <SectionHeading eyebrow="CONTROL CENTER" title="Workspace modules"/>
    <section className="module-grid">{[
      {name:'Document access',detail:'View documents visible to your identity',icon:FileLock2,page:'Documents' as Page},
      {name:'Policy sandbox',detail:'Prototype area; live policy publishing is not connected',icon:SlidersHorizontal,page:'Policy sandbox' as Page},
      {name:'Decision explorer',detail:'Inspect persisted allow and deny decisions',icon:Fingerprint,page:'Decisions' as Page},
      {name:'Audit history',detail:'Review hash-linked security events',icon:History,page:'Audit history' as Page},
      {name:'People & roles',detail:'Identity and role administration roadmap',icon:Users,page:'People & roles' as Page},
      {name:'API connection',detail:health?.status === 'UP' ? 'Backend health endpoint is responding' : 'Backend unavailable',icon:Activity,page:'Overview' as Page},
    ].map(({name,detail,icon:Icon,page}) => <button className="module" key={name} onClick={() => setPage(page)}><div className="module-icon"><Icon size={20}/></div><div className="module-title"><h3>{name}</h3><ChevronRight size={16}/></div><p>{detail}</p><div className="module-foot">Open module <ArrowUpRight size={14}/></div></button>)}</section>
  </>;
}
function Documents({ documents, onRead, busyId }: { documents: DocumentItem[]; onRead: (d: DocumentItem) => void; busyId: string }) {
  const [query,setQuery] = useState('');
  const filtered = documents.filter(d => (d.title + d.owner + d.department + d.classification).toLowerCase().includes(query.toLowerCase()));
  return <><SectionHeading eyebrow="DOCUMENT GOVERNANCE" title="Documents" detail="Visible under your current identity"/>
    <div className="toolbar"><input className="search-input" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search visible documents"/></div>
    <div className="table-wrap"><table><thead><tr><th>DOCUMENT</th><th>OWNER</th><th>DEPARTMENT</th><th>CLASSIFICATION</th><th>ACCESS</th></tr></thead><tbody>
      {filtered.map(d => <tr key={d.id}><td><div className="doc-cell"><div className="file-icon"><FileText size={17}/></div><div><strong>{d.title}</strong><small>{d.id}</small></div></div></td><td>{d.owner}</td><td>{d.department}</td><td><Badge tone={d.classification === 'PUBLIC' ? 'green' : d.classification === 'RESTRICTED' ? 'red' : 'amber'}>{d.classification}</Badge></td><td><button className="secondary" disabled={busyId === d.id} onClick={() => onRead(d)}>{busyId === d.id ? 'Checking…' : 'Request read'}</button></td></tr>)}
    </tbody></table>{filtered.length===0 && <div className="empty">No visible documents found.</div>}</div>
    <p className="footnote"><LockKeyhole size={14}/> A document read request is authorized by the backend. Changing an ID in the browser does not grant access.</p>
  </>;
}
function PolicySandbox() {
  return <><SectionHeading eyebrow="POLICY INTELLIGENCE" title="Policy sandbox" detail="Prototype only"/>
    <div className="notice"><ShieldAlert size={16}/> The current backend does not yet implement draft policy simulation or publication. This area is intentionally marked as a prototype and does not claim to make live access decisions.</div>
    <div className="panel explanation"><span className="eyebrow">PLANNED WORKFLOW</span><h3>Draft → Simulate → Review → Publish immutable version → Verify observed impact</h3><p className="muted">Policy Impact Verification (PIV) will compare the predicted outcomes from a simulation snapshot with actual decisions made under the published policy version.</p></div>
  </>;
}
function Decisions({ decisions }: { decisions: DecisionItem[] }) {
  return <><SectionHeading eyebrow="EXPLAINABILITY" title="Decision explorer" detail="Persisted backend decisions"/>
    <div className="table-wrap"><table><thead><tr><th>TIME</th><th>DOCUMENT</th><th>ACTION</th><th>OUTCOME</th><th>REASON</th></tr></thead><tbody>
      {decisions.map(d => <tr key={d.id}><td>{new Date(d.decidedAt).toLocaleString()}</td><td>{d.document ?? 'Unknown document'}</td><td>{d.action}</td><td><Badge tone={d.outcome === 'ALLOW' ? 'green' : 'red'}>{d.outcome}</Badge></td><td title={d.explanation}>{d.reasonCode}</td></tr>)}
    </tbody></table>{decisions.length===0 && <div className="empty">No decisions recorded yet. Request access to a document to create one.</div>}</div>
  </>;
}
function AuditHistory({ audit }: { audit: AuditItem[] }) {
  return <><SectionHeading eyebrow="ACCOUNTABILITY" title="Audit history" detail="Hash-linked backend events"/>
    <div className="notice"><ShieldAlert size={16}/> Hash linking provides tamper evidence under its trust assumptions; it is not an absolute guarantee. External signed checkpoints are future hardening work.</div>
    <div className="timeline">{audit.map(e => <article className="timeline-item" key={e.id}><div className="timeline-dot"/><div className="timeline-content"><div className="timeline-top"><strong>{e.eventType}</strong><span>#{e.sequence}</span></div><p>{e.actor} · {new Date(e.occurredAt).toLocaleString()}</p><small className="hash-line">Hash: {e.eventHash}</small></div></article>)}</div>
    {audit.length===0 && <div className="empty">No audit events recorded yet.</div>}
  </>;
}
function People() {
  return <><SectionHeading eyebrow="IDENTITY GOVERNANCE" title="People & roles" detail="Administration roadmap"/>
    <div className="notice"><CircleHelp size={16}/> Authentication is backed by PostgreSQL users and roles. Full user/role administration endpoints and UI are not implemented in this vertical slice.</div>
  </>;
}

export default function App() {
  const [user,setUser] = useState<CurrentUser | null>(null);
  const [health,setHealth] = useState<Health | null>(null);
  const [page,setPage] = useState<Page>('Overview');
  const [documents,setDocuments] = useState<DocumentItem[]>([]);
  const [decisions,setDecisions] = useState<DecisionItem[]>([]);
  const [audit,setAudit] = useState<AuditItem[]>([]);
  const [busyId,setBusyId] = useState('');
  const [message,setMessage] = useState('');
  const [loading,setLoading] = useState(true);

  async function refreshData() {
    const [d,dec,a] = await Promise.all([
      apiGet<DocumentItem[]>('/api/v1/documents'),
      apiGet<DecisionItem[]>('/api/v1/decisions'),
      apiGet<AuditItem[]>('/api/v1/audit-events'),
    ]);
    setDocuments(d); setDecisions(dec); setAudit(a);
  }
  useEffect(() => {
    apiGet<Health>('/api/v1/health').then(setHealth).catch(() => setHealth(null));
    apiGet<CurrentUser>('/api/v1/auth/me')
      .then(async current => { if (current.authenticated) { setUser(current); await refreshData(); } })
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);
  async function login(username: string,password: string) {
    const result = await apiPost<LoginResult>('/api/v1/auth/login',{username,password});
    setUser({authenticated:result.authenticated,username:result.username,authorities:result.authorities});
    await refreshData();
  }
  async function logout() {
    await apiPost<void>('/api/v1/auth/logout');
    clearCsrf(); setUser(null); setDocuments([]); setDecisions([]); setAudit([]);
  }
  async function readDocument(doc: DocumentItem) {
    setBusyId(doc.id); setMessage('');
    try {
      const result = await apiGet<{document:DocumentItem;decision:AccessResult}>(`/api/v1/documents/${doc.id}`);
      setMessage(`ALLOW — ${result.decision.explanation}`);
      await refreshData();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Access request failed');
      await refreshData();
    } finally { setBusyId(''); }
  }

  if (loading) return <main className="login-shell"><div className="login-card">Connecting to TRUSTLENS…</div></main>;
  if (!user) return <Login onLogin={login}/>;

  return <main className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><ShieldCheck size={22}/></div><div><strong>TRUSTLENS</strong><small>ACCESS GOVERNANCE</small></div></div>
      <div><div className="nav-label">WORKSPACE</div>{[{name:'Overview' as Page,icon:LayoutDashboard},{name:'Documents' as Page,icon:FileLock2},{name:'People & roles' as Page,icon:Users}].map(item=><button key={item.name} className={`nav ${page===item.name?'active':''}`} onClick={()=>setPage(item.name)}><item.icon size={17}/>{item.name}</button>)}</div>
      <div><div className="nav-label">SECURITY</div>{[{name:'Policy sandbox' as Page,icon:SlidersHorizontal},{name:'Decisions' as Page,icon:Fingerprint},{name:'Audit history' as Page,icon:History}].map(item=><button key={item.name} className={`nav ${page===item.name?'active':''}`} onClick={()=>setPage(item.name)}><item.icon size={17}/>{item.name}</button>)}</div>
      <div className="sidebar-bottom"><div className="secure-icon"><ShieldCheck size={17}/></div><strong>Authenticated session</strong><p>Protected requests are evaluated by the backend.</p></div>
    </aside>
    <section className="content">
      <header className="topbar"><div><span className="eyebrow">CAMPUS SECURITY PLATFORM</span><h1>{page}</h1></div><div className="topbar-right"><div className="status-pill"><span className={health?.status==='UP'?'dot online':'dot'}/>{health?.status==='UP'?'API connected':'API offline'}</div><div className="user-chip"><div className="avatar">{user.username.slice(0,2).toUpperCase()}</div><span>{user.username}</span></div><button className="icon-button" title="Refresh data" onClick={() => refreshData().catch(e=>setMessage(e.message))}><RefreshCw size={15}/></button><button className="icon-button" title="Sign out" onClick={() => logout().catch(e=>setMessage(e.message))}><LogOut size={15}/></button></div></header>
      {message && <div className="connection-notice"><Check size={15}/>{message}</div>}
      {page==='Overview' && <Overview health={health} documents={documents} decisions={decisions} audit={audit} setPage={setPage}/>}
      {page==='Documents' && <Documents documents={documents} onRead={readDocument} busyId={busyId}/>}
      {page==='People & roles' && <People/>}
      {page==='Policy sandbox' && <PolicySandbox/>}
      {page==='Decisions' && <Decisions decisions={decisions}/>}
      {page==='Audit history' && <AuditHistory audit={audit}/>}
      <footer><span>TRUSTLENS · FULL-STACK VERTICAL SLICE</span><span>Session user · {user.username}</span></footer>
    </section>
  </main>;
}
