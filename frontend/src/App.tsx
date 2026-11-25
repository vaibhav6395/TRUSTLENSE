import { FormEvent, useEffect, useState } from 'react';
import {
  Activity, ArrowUpRight, BookOpen, Check, ChevronRight, CircleHelp,
  FileLock2, FileText, Fingerprint, History, LayoutDashboard, LockKeyhole,
  LogOut, RefreshCw, ShieldAlert, ShieldCheck, SlidersHorizontal, Users
} from 'lucide-react';
import {
  apiGet, apiPost, apiUpload, apiDownload, clearCsrf, type AuditItem, type CurrentUser,
  type DecisionItem, type DocumentItem, type GrantItem, type Health
} from './api';

type Page = 'Overview' | 'Documents' | 'People & roles' | 'Policy sandbox' | 'Decisions' | 'Audit history';
type AccessResult = { allowed: boolean; outcome: string; reasonCode: string; explanation: string; decisionId: string };

function Badge({ children, tone = 'neutral' }: { children: string; tone?: 'neutral' | 'green' | 'amber' | 'red' }) {
  return <span className={`badge ${tone}`}><i />{children}</span>;
}
function SectionHeading({ eyebrow, title, detail }: { eyebrow: string; title: string; detail?: string }) {
  return <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2></div>{detail && <span className="muted">{detail}</span>}</div>;
}
type LoginChallenge = { authenticated: false; mfaRequired: boolean; mfaEnrollmentRequired: boolean; username: string; challengeToken: string };
type LoginResult = { authenticated: true; username: string; authorities: string[]; recoveryCodes?: string[] };
function Login({ onLogin, onAuthenticated }: {
  onLogin: (username: string, password: string) => Promise<LoginChallenge>;
  onAuthenticated: (result: LoginResult) => Promise<void>;
}) {
  const [mode,setMode] = useState<'signin'|'signup'>('signin');
  const [stage,setStage] = useState<'password'|'mfa'>('password');
  const [username,setUsername] = useState('');
  const [password,setPassword] = useState('');
  const [email,setEmail] = useState('');
  const [displayName,setDisplayName] = useState('');
  const [departmentCode,setDepartmentCode] = useState('CSE');
  const [facultyIdentifier,setFacultyIdentifier] = useState('');
  const [challenge,setChallenge] = useState<LoginChallenge|null>(null);
  const [setup,setSetup] = useState<{secret:string;otpauthUri:string}|null>(null);
  const [code,setCode] = useState('');
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState('');
  const [success,setSuccess] = useState('');
  async function submitLogin(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try {
      const result = await onLogin(username,password);
      setChallenge(result); setStage('mfa');
      if (result.mfaEnrollmentRequired) {
        const next = await apiPost<{secret:string;otpauthUri:string}>('/api/v1/auth/mfa/setup', {
          username: result.username, challengeToken: result.challengeToken,
        });
        setSetup(next);
      }
    } catch (err) { setError(err instanceof Error ? err.message : 'Sign-in failed'); }
    finally { setBusy(false); }
  }
  async function submitSignup(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try {
      const result = await apiPost<{message:string}>('/api/v1/auth/signup', {
        username, institutionalEmail: email, password, displayName,
        departmentCode, facultyIdentifier,
      });
      setSuccess(result.message); setMode('signin'); setPassword('');
    } catch (err) { setError(err instanceof Error ? err.message : 'Signup failed'); }
    finally { setBusy(false); }
  }
  async function submitMfa(e: FormEvent) {
    e.preventDefault(); if (!challenge) return;
    setBusy(true); setError('');
    try {
      const result = await apiPost<LoginResult>('/api/v1/auth/mfa/verify', {
        username: challenge.username, challengeToken: challenge.challengeToken, code,
      });
      await onAuthenticated(result);
    } catch (err) { setError(err instanceof Error ? err.message : 'MFA verification failed'); }
    finally { setBusy(false); }
  }
  function backToLogin() { setStage('password'); setChallenge(null); setSetup(null); setCode(''); setError(''); }
  return <main className="login-shell"><div className="login-card">
    <div className="brand-mark login-mark"><ShieldCheck size={25}/></div>
    <span className="eyebrow">CAMPUS SECURITY PLATFORM</span>
    <h1>TRUSTLENS</h1>
    {stage==='mfa' && challenge ? <>
      <p>{challenge.mfaEnrollmentRequired ? 'Secure your account by enrolling an authenticator app before continuing.' : 'Enter the current six-digit code from your authenticator app.'}</p>
      {setup && <div className="notice"><div><strong>Authenticator setup</strong><p>Add TRUSTLENS to an authenticator app using this setup key:</p><code>{setup.secret}</code><p className="footnote">Keep this key private. It will not be shown again after enrollment.</p></div></div>}
      <form onSubmit={submitMfa}>
        <label>Authenticator code or 16-character recovery code<input inputMode="text" autoComplete="one-time-code" maxLength={16} value={code} onChange={e=>setCode(e.target.value.toUpperCase())} required/></label>
        {error && <div className="connection-notice"><ShieldAlert size={15}/>{error}</div>}
        <button className="primary full" disabled={busy || (code.length!==6 && code.length!==16)}>{busy?'Verifying…':'Verify and continue'} <ArrowUpRight size={16}/></button>
        <button type="button" className="secondary full" onClick={backToLogin}>Back to sign in</button>
      </form>
    </> : mode==='signin' ? <>
      <p>Sign in with your approved campus account. MFA is required before protected access is granted.</p>
      <form onSubmit={submitLogin}>
        <label>Username<input autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)} required/></label>
        <label>Password<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required/></label>
        {error && <div className="connection-notice"><ShieldAlert size={15}/>{error}</div>}
        {success && <div className="notice">{success}</div>}
        <button className="primary full" disabled={busy}>{busy?'Checking…':'Continue'} <ArrowUpRight size={16}/></button>
      </form>
      <button type="button" className="secondary full" onClick={()=>{setMode('signup');setError('');setSuccess('');}}>Request faculty access</button>
    </> : <>
      <p>Submit your institutional details. An administrator must verify and approve your request before you can sign in.</p>
      <form onSubmit={submitSignup}>
        <label>Full name<input value={displayName} onChange={e=>setDisplayName(e.target.value)} required maxLength={160}/></label>
        <label>Username<input autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)} required minLength={3} maxLength={80}/></label>
        <label>Institutional email<input type="email" value={email} onChange={e=>setEmail(e.target.value)} required maxLength={254}/></label>
        <label>Faculty / employee identifier<input value={facultyIdentifier} onChange={e=>setFacultyIdentifier(e.target.value)} required maxLength={80}/></label>
        <label>Department code<input value={departmentCode} onChange={e=>setDepartmentCode(e.target.value)} required maxLength={40}/></label>
        <label>Password (12–72 characters)<input type="password" autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} required minLength={12} maxLength={72}/></label>
        {error && <div className="connection-notice"><ShieldAlert size={15}/>{error}</div>}
        <button className="primary full" disabled={busy}>{busy?'Submitting…':'Submit verification request'}</button>
      </form>
      <button type="button" className="secondary full" onClick={()=>{setMode('signin');setError('');}}>Back to sign in</button>
    </>}
    <div className="login-hint">Administrator approval and MFA are enforced by the backend, not by this interface.</div>
  </div></main>;
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
function Documents({ documents, onRead, busyId, onUpload, onDownload }: {
  documents: DocumentItem[]; onRead: (d: DocumentItem) => void; busyId: string;
  onUpload: (file: File, title: string, classification: string) => Promise<void>;
  onDownload: (d: DocumentItem) => Promise<void>;
}) {
  const [query,setQuery] = useState('');
  const [file,setFile] = useState<File | null>(null);
  const [title,setTitle] = useState('');
  const [classification,setClassification] = useState('INTERNAL');
  const [uploading,setUploading] = useState(false);
  const [error,setError] = useState('');
  const filtered = documents.filter(d => (d.title + d.owner + d.department + d.classification).toLowerCase().includes(query.toLowerCase()));
  async function submitUpload(e: FormEvent) {
    e.preventDefault(); setError('');
    if (!file) { setError('Choose a file to upload.'); return; }
    setUploading(true);
    try { await onUpload(file, title, classification); setFile(null); setTitle(''); }
    catch (err) { setError(err instanceof Error ? err.message : 'Upload failed'); }
    finally { setUploading(false); }
  }
  async function download(doc: DocumentItem) {
    setError('');
    try { await onDownload(doc); }
    catch (err) { setError(err instanceof Error ? err.message : 'Download failed'); }
  }
  return <><SectionHeading eyebrow="DOCUMENT GOVERNANCE" title="Documents" detail="Visible under your current identity"/>
    <form className="toolbar" onSubmit={submitUpload}>
      <input aria-label="Document title" value={title} onChange={e=>setTitle(e.target.value)} placeholder="Document title" required maxLength={240}/>
      <select aria-label="Classification" value={classification} onChange={e=>setClassification(e.target.value)}>
        <option>PUBLIC</option><option>INTERNAL</option><option>CONFIDENTIAL</option><option>RESTRICTED</option>
      </select>
      <input aria-label="Choose file" type="file" accept=".pdf,.txt,.png,.jpg,.jpeg,application/pdf,text/plain,image/png,image/jpeg" onChange={e=>setFile(e.target.files?.[0] ?? null)} required/>
      <button className="primary" disabled={uploading}>{uploading ? 'Uploading…' : 'Upload document'}</button>
    </form>
    <p className="footnote"><LockKeyhole size={14}/> Private uploads accept PDF, TXT, PNG and JPEG files up to 10 MB. Files are stored outside the public web root.</p>
    {error && <div className="connection-notice">{error}</div>}
    <div className="toolbar"><input className="search-input" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search visible documents"/></div>
    <div className="table-wrap"><table><thead><tr><th>DOCUMENT</th><th>OWNER</th><th>DEPARTMENT</th><th>CLASSIFICATION</th><th>ACCESS</th></tr></thead><tbody>
      {filtered.map(d => <tr key={d.id}><td><div className="doc-cell"><div className="file-icon"><FileText size={17}/></div><div><strong>{d.title}</strong><small>{d.id}</small></div></div></td><td>{d.owner}</td><td>{d.department}</td><td><Badge tone={d.classification === 'PUBLIC' ? 'green' : d.classification === 'RESTRICTED' ? 'red' : 'amber'}>{d.classification}</Badge></td><td><div className="toolbar"><button className="secondary" disabled={busyId === d.id} onClick={() => onRead(d)}>{busyId === d.id ? 'Checking…' : 'Request read'}</button><button className="secondary" onClick={() => void download(d)}>Download</button></div></td></tr>)}
    </tbody></table>{filtered.length===0 && <div className="empty">No visible documents found.</div>}</div>
    <p className="footnote"><LockKeyhole size={14}/> Every read and download is checked by the backend. Old application URLs do not bypass current grant checks.</p>
  </>;
}
type SandboxScenario = {
  id: number; user: string; role: string; department: string; document: string;
  classification: string; documentDepartment: string; owner: boolean; grant: boolean; action: 'READ' | 'DOWNLOAD';
};

const sandboxScenarios: SandboxScenario[] = [
  { id: 1, user: 'A. Sharma', role: 'FACULTY', department: 'CSE', document: 'Research proposal', classification: 'CONFIDENTIAL', documentDepartment: 'CSE', owner: false, grant: true, action: 'READ' },
  { id: 2, user: 'R. Mehta', role: 'FACULTY', department: 'CSE', document: 'Lab handbook', classification: 'INTERNAL', documentDepartment: 'CSE', owner: false, grant: false, action: 'READ' },
  { id: 3, user: 'K. Rao', role: 'FACULTY', department: 'ECE', document: 'Research proposal', classification: 'CONFIDENTIAL', documentDepartment: 'CSE', owner: false, grant: false, action: 'READ' },
  { id: 4, user: 'M. Das', role: 'SECURITY_ADMIN', department: 'CSE', document: 'Research proposal', classification: 'CONFIDENTIAL', documentDepartment: 'CSE', owner: false, grant: false, action: 'READ' },
  { id: 5, user: 'S. Khan', role: 'STUDENT', department: 'IT', document: 'Public notice', classification: 'PUBLIC', documentDepartment: 'ADMIN', owner: false, grant: false, action: 'READ' },
  { id: 6, user: 'P. Verma', role: 'FACULTY', department: 'CSE', document: 'Research proposal', classification: 'CONFIDENTIAL', documentDepartment: 'CSE', owner: false, grant: false, action: 'DOWNLOAD' },
];

function PolicySandbox() {
  const [departmentRead, setDepartmentRead] = useState(false);
  const [publicRead, setPublicRead] = useState(true);
  const [hasSimulated, setHasSimulated] = useState(false);
  const evaluate = (s: SandboxScenario, proposed: boolean) => {
    if (s.owner) return { allowed: true, reason: 'DOCUMENT_OWNER' };
    if ((!proposed || publicRead) && s.classification === 'PUBLIC' && s.action === 'READ') return { allowed: true, reason: 'PUBLIC_READ' };
    if (s.grant) return { allowed: true, reason: 'ACTIVE_GRANT' };
    if (s.role === 'SECURITY_ADMIN' && s.department === s.documentDepartment && s.action === 'READ') return { allowed: true, reason: 'SECURITY_ADMIN_SAME_DEPARTMENT' };
    if (proposed && departmentRead && s.role === 'FACULTY' && s.department === s.documentDepartment && s.action === 'READ') return { allowed: true, reason: 'PROPOSED_FACULTY_DEPARTMENT_RULE' };
    return { allowed: false, reason: 'NO_MATCHING_POLICY' };
  };
  const rows = sandboxScenarios.map(s => {
    const current = evaluate(s, false);
    const next = evaluate(s, true);
    return { ...s, current, next, changed: current.allowed !== next.allowed };
  });
  const gained = rows.filter(r => !r.current.allowed && r.next.allowed).length;
  const lost = rows.filter(r => r.current.allowed && !r.next.allowed).length;
  const unchanged = rows.length - gained - lost;
  return <><SectionHeading eyebrow="POLICY INTELLIGENCE" title="Policy sandbox" detail="Read-only synthetic simulation"/>
    <div className="notice"><ShieldAlert size={16}/> Simulation uses six fabricated scenarios in the browser. It does not query or modify live grants, publish policies, or make production access decisions.</div>
    <div className="sandbox-grid">
      <div className="panel">
        <div className="panel-heading"><div><span className="eyebrow">DRAFT POLICY</span><h3>Proposed rule controls</h3></div><SlidersHorizontal size={19}/></div>
        <label className="sandbox-toggle"><input type="checkbox" checked={publicRead} onChange={e => {setPublicRead(e.target.checked);setHasSimulated(false);}}/> Permit READ for PUBLIC documents</label>
        <label className="sandbox-toggle"><input type="checkbox" checked={departmentRead} onChange={e => {setDepartmentRead(e.target.checked);setHasSimulated(false);}}/> Permit same-department FACULTY READ</label>
        <p className="muted">Current baseline mirrors the implemented evaluator. The faculty department rule is an experimental draft condition.</p>
        <button className="primary" onClick={() => setHasSimulated(true)}>Run simulation <ChevronRight size={15}/></button>
        <div className="footnote"><ShieldCheck size={14}/> Read-only: changing controls never writes to the backend.</div>
      </div>
      <div className="panel">
        <div className="panel-heading"><div><span className="eyebrow">IMPACT SUMMARY</span><h3>Predicted access changes</h3></div><Activity size={19}/></div>
        <div className="metrics sandbox-metrics">
          <div className="metric"><span className="metric-top">Gain access</span><strong>{hasSimulated ? gained : '—'}</strong><small>New ALLOW outcomes</small></div>
          <div className="metric"><span className="metric-top">Lose access</span><strong>{hasSimulated ? lost : '—'}</strong><small>New DENY outcomes</small></div>
        </div>
        <p className="muted">{hasSimulated ? `${unchanged} of ${rows.length} scenarios remain unchanged. Review each changed outcome below.` : 'Run the simulation to calculate the impact of your draft rules.'}</p>
      </div>
    </div>
    <div className="section-heading"><div><span className="eyebrow">SCENARIO ANALYSIS</span><h2>Predicted decision comparison</h2></div><span className="muted">Synthetic identities only · {rows.length} cases</span></div>
    <div className="table-wrap"><table><thead><tr><th>IDENTITY</th><th>RESOURCE / ACTION</th><th>CURRENT</th><th>PROPOSED</th><th>EXPLANATION</th></tr></thead><tbody>
      {rows.map(r => <tr key={r.id}><td><strong>{r.user}</strong><small>{r.role} · {r.department}</small></td><td>{r.document}<small>{r.classification} · {r.action}</small></td><td><Badge tone={r.current.allowed ? 'green' : 'red'}>{r.current.allowed ? 'ALLOW' : 'DENY'}</Badge></td><td><Badge tone={r.next.allowed ? 'green' : 'red'}>{r.next.allowed ? 'ALLOW' : 'DENY'}</Badge></td><td>{hasSimulated ? <><strong>{r.changed ? 'CHANGED' : 'UNCHANGED'}</strong><small>{r.next.reason}</small></> : <small>Run simulation to review impact</small>}</td></tr>)}
    </tbody></table></div>
    <div className="panel explanation"><span className="eyebrow">NEXT POLICY LIFECYCLE STEPS</span><h3>Draft → Simulate → Review → Publish immutable version → Verify observed impact</h3><p className="muted">This prototype demonstrates impact comparison on synthetic scenarios. Policy persistence, approval, immutable publication, and Policy Impact Verification against real observed decisions remain planned backend work.</p></div>
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
type FacultySignupRequest = {
  id: string; username: string; institutionalEmail: string; displayName: string;
  departmentCode: string; facultyIdentifier: string; status: string; reviewNote: string|null; createdAt: string;
};
function FacultyRequests() {
  const [requests,setRequests] = useState<FacultySignupRequest[]>([]);
  const [error,setError] = useState('');
  const [busy,setBusy] = useState('');
  async function load() {
    try { setRequests(await apiGet<FacultySignupRequest[]>('/api/v1/admin/signup-requests')); setError(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load faculty requests'); }
  }
  useEffect(() => { void load(); }, []);
  async function review(id: string, action: 'approve'|'reject') {
    setBusy(id); setError('');
    try {
      await apiPost(`/api/v1/admin/signup-requests/${id}/${action}`, {note: action==='approve'?'Institutional details verified':'Request rejected after review'});
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : 'Review action failed'); }
    finally { setBusy(''); }
  }
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">IDENTITY VERIFICATION</span><h3>Faculty signup requests</h3></div><Users size={19}/></div>
    <p className="muted">Review institutional identity details before an account is created. Approved faculty must enroll MFA at first sign-in.</p>
    {error && <div className="connection-notice">{error}</div>}
    <div className="table-wrap"><table><thead><tr><th>APPLICANT</th><th>INSTITUTIONAL DETAILS</th><th>STATUS</th><th>REVIEW</th></tr></thead><tbody>
      {requests.map(r=><tr key={r.id}><td><strong>{r.displayName}</strong><small>{r.username}</small></td><td>{r.institutionalEmail}<small>{r.departmentCode} · ID {r.facultyIdentifier}</small></td><td><Badge tone={r.status==='APPROVED'?'green':r.status==='REJECTED'?'red':'amber'}>{r.status}</Badge></td><td>{r.status==='PENDING' ? <div className="toolbar"><button className="primary" disabled={busy===r.id} onClick={()=>void review(r.id,'approve')}>Approve</button><button className="secondary" disabled={busy===r.id} onClick={()=>void review(r.id,'reject')}>Reject</button></div> : (r.reviewNote ?? 'Reviewed')}</td></tr>)}
    </tbody></table>{requests.length===0 && <div className="empty">No faculty signup requests.</div>}</div>
  </section>;
}
type ManagedUser = {
  id: string; username: string; displayName: string; departmentCode: string;
  enabled: boolean; mfaEnabled: boolean; roles: string;
};
function AdminUsers() {
  const [users,setUsers] = useState<ManagedUser[]>([]);
  const [error,setError] = useState('');
  const [busy,setBusy] = useState('');
  async function load() {
    try { setUsers(await apiGet<ManagedUser[]>('/api/v1/admin/users')); setError(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load accounts'); }
  }
  useEffect(() => { void load(); }, []);
  async function toggle(user: ManagedUser) {
    setBusy(user.username); setError('');
    try {
      await apiPost(`/api/v1/admin/users/${encodeURIComponent(user.username)}/${user.enabled?'disable':'enable'}`);
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : 'Account update failed'); }
    finally { setBusy(''); }
  }
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">ACCOUNT LIFECYCLE</span><h3>Campus accounts</h3></div><Users size={19}/></div>
    <p className="muted">Disablement takes effect on the next protected request. The backend prevents disabling the last active security administrator.</p>
    {error && <div className="connection-notice">{error}</div>}
    <div className="table-wrap"><table><thead><tr><th>ACCOUNT</th><th>ROLE / DEPARTMENT</th><th>MFA</th><th>STATUS</th><th/></tr></thead><tbody>
      {users.map(u=><tr key={u.id}><td><strong>{u.displayName}</strong><small>{u.username}</small></td><td>{u.roles || 'No role'}<small>{u.departmentCode}</small></td><td><Badge tone={u.mfaEnabled?'green':'amber'}>{u.mfaEnabled?'ENROLLED':'PENDING'}</Badge></td><td><Badge tone={u.enabled?'green':'red'}>{u.enabled?'ACTIVE':'DISABLED'}</Badge></td><td><button className="secondary" disabled={busy===u.username} onClick={()=>void toggle(u)}>{busy===u.username?'Saving…':u.enabled?'Disable':'Enable'}</button></td></tr>)}
    </tbody></table>{users.length===0 && <div className="empty">No accounts found.</div>}</div>
  </section>;
}
function People({ user, documents, onChanged }: { user: CurrentUser; documents: DocumentItem[]; onChanged: () => Promise<void> }) {
  const [grants,setGrants] = useState<GrantItem[]>([]);
  const [error,setError] = useState('');
  const [busy,setBusy] = useState('');
  const [subject,setSubject] = useState('');
  const [documentId,setDocumentId] = useState('');
  const [permission,setPermission] = useState('READ');
  const [expiresAt,setExpiresAt] = useState('');
  const isAdmin = user.authorities.includes('ROLE_SECURITY_ADMIN');
  async function load() {
    try { setGrants(await apiGet<GrantItem[]>('/api/v1/grants')); setError(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load grants'); }
  }
  useEffect(() => { if (isAdmin) void load(); }, [isAdmin]);
  async function revoke(id: string) {
    setBusy(id); setError('');
    try { await apiPost(`/api/v1/grants/${id}/revoke`); await load(); await onChanged(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Revocation failed'); }
    finally { setBusy(''); }
  }
  async function createGrant(e: FormEvent) {
    e.preventDefault(); setBusy('create'); setError('');
    try {
      await apiPost('/api/v1/grants', {
        documentId, subjectUsername: subject.trim(), permission,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      });
      setSubject(''); setExpiresAt(''); await load(); await onChanged();
    } catch (e) { setError(e instanceof Error ? e.message : 'Grant creation failed'); }
    finally { setBusy(''); }
  }
  return <><SectionHeading eyebrow="IDENTITY GOVERNANCE" title="People & roles" detail="Database-backed grant administration"/>
    {!isAdmin && <div className="notice"><ShieldAlert size={16}/> Your account does not have the SECURITY_ADMIN role. Grant administration is restricted to security administrators.</div>}
    {error && <div className="connection-notice">{error}</div>}
    {isAdmin && <><FacultyRequests/><AdminUsers/><div className="notice"><CircleHelp size={16}/> Grants are evaluated against current server-side state. Expired and revoked grants cannot authorize subsequent requests.</div>
      <form className="toolbar" onSubmit={createGrant}>
        <select aria-label="Document" value={documentId} onChange={e=>setDocumentId(e.target.value)} required>
          <option value="">Select document</option>{documents.map(d=><option key={d.id} value={d.id}>{d.title}</option>)}
        </select>
        <input aria-label="Subject username" value={subject} onChange={e=>setSubject(e.target.value)} placeholder="Existing username" required/>
        <select aria-label="Permission" value={permission} onChange={e=>setPermission(e.target.value)}><option value="READ">READ</option><option value="DOWNLOAD">DOWNLOAD</option></select>
        <input aria-label="Grant expiry" type="datetime-local" value={expiresAt} onChange={e=>setExpiresAt(e.target.value)}/>
        <button className="primary" disabled={busy==='create'}>{busy==='create'?'Creating…':'Create grant'}</button>
      </form>
      <div className="table-wrap"><table><thead><tr><th>DOCUMENT</th><th>SUBJECT</th><th>PERMISSION</th><th>STATUS</th><th/></tr></thead><tbody>
        {grants.map(g=><tr key={g.id}><td>{g.document}</td><td>{g.subject}</td><td>{g.permission}</td><td><Badge tone={g.revokedAt?'red':'green'}>{g.revokedAt?'REVOKED':'ACTIVE'}</Badge></td><td>{!g.revokedAt && <button className="secondary" disabled={busy===g.id} onClick={()=>void revoke(g.id)}>{busy===g.id?'Revoking…':'Revoke grant'}</button>}</td></tr>)}
      </tbody></table>{grants.length===0 && <div className="empty">No grants found.</div>}</div>
    </>}
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
  const [recoveryCodes,setRecoveryCodes] = useState<string[]>([]);
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
  async function login(username: string,password: string): Promise<LoginChallenge> {
    return apiPost<LoginChallenge>('/api/v1/auth/login',{username,password});
  }
  async function completeLogin(result: LoginResult) {
    setUser({authenticated:result.authenticated,username:result.username,authorities:result.authorities});
    setRecoveryCodes(result.recoveryCodes ?? []);
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
  if (!user) return <Login onLogin={login} onAuthenticated={completeLogin}/>;

  return <main className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><ShieldCheck size={22}/></div><div><strong>TRUSTLENS</strong><small>ACCESS GOVERNANCE</small></div></div>
      <div><div className="nav-label">WORKSPACE</div>{[{name:'Overview' as Page,icon:LayoutDashboard},{name:'Documents' as Page,icon:FileLock2},{name:'People & roles' as Page,icon:Users}].map(item=><button key={item.name} className={`nav ${page===item.name?'active':''}`} onClick={()=>setPage(item.name)}><item.icon size={17}/>{item.name}</button>)}</div>
      <div><div className="nav-label">SECURITY</div>{[{name:'Policy sandbox' as Page,icon:SlidersHorizontal},{name:'Decisions' as Page,icon:Fingerprint},{name:'Audit history' as Page,icon:History}].map(item=><button key={item.name} className={`nav ${page===item.name?'active':''}`} onClick={()=>setPage(item.name)}><item.icon size={17}/>{item.name}</button>)}</div>
      <div className="sidebar-bottom"><div className="secure-icon"><ShieldCheck size={17}/></div><strong>Authenticated session</strong><p>Protected requests are evaluated by the backend.</p></div>
    </aside>
    <section className="content">
      <header className="topbar"><div><span className="eyebrow">CAMPUS SECURITY PLATFORM</span><h1>{page}</h1></div><div className="topbar-right"><div className="status-pill"><span className={health?.status==='UP'?'dot online':'dot'}/>{health?.status==='UP'?'API connected':'API offline'}</div><div className="user-chip"><div className="avatar">{user.username.slice(0,2).toUpperCase()}</div><span>{user.username}</span></div><button className="icon-button" title="Refresh data" onClick={() => refreshData().catch(e=>setMessage(e.message))}><RefreshCw size={15}/></button><button className="icon-button" title="Sign out" onClick={() => logout().catch(e=>setMessage(e.message))}><LogOut size={15}/></button></div></header>
      {recoveryCodes.length>0 && <section className="panel explanation">
        <div className="panel-heading"><div><span className="eyebrow">SAVE THESE NOW</span><h3>MFA recovery codes</h3></div><ShieldCheck size={19}/></div>
        <p>These ten codes are shown once. Store them somewhere private. Each code can replace one authenticator code for a single sign-in.</p>
        <div className="metrics">{recoveryCodes.map(code=><code className="metric" key={code}>{code}</code>)}</div>
        <button className="primary" onClick={()=>setRecoveryCodes([])}>I have saved my recovery codes</button>
      </section>}
      {message && <div className="connection-notice"><Check size={15}/>{message}</div>}
      {page==='Overview' && <Overview health={health} documents={documents} decisions={decisions} audit={audit} setPage={setPage}/>}
      {page==='Documents' && <Documents documents={documents} onRead={readDocument} busyId={busyId}
        onUpload={async (file,title,classification) => { const form=new FormData(); form.append('file',file); form.append('title',title); form.append('classification',classification); await apiUpload('/api/v1/documents',form); await refreshData(); setMessage('Document uploaded to private storage.'); }}
        onDownload={async doc => { await apiDownload(`/api/v1/documents/${doc.id}/content`,doc.title); await refreshData(); setMessage('Download authorized by the current server-side policy.'); }}/>}
      
      {page==='People & roles' && <People user={user} documents={documents} onChanged={refreshData}/>}
      {page==='Policy sandbox' && <PolicySandbox/>}
      {page==='Decisions' && <Decisions decisions={decisions}/>}
      {page==='Audit history' && <AuditHistory audit={audit}/>}
      <footer><span>TRUSTLENS · FULL-STACK VERTICAL SLICE</span><span>Session user · {user.username}</span></footer>
    </section>
  </main>;
}
