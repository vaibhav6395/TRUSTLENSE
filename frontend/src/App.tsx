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
function Documents({ documents, onRead, busyId, onUpload, onDownload, onRetire, username, isAdmin }: {
  documents: DocumentItem[]; onRead: (d: DocumentItem) => void; busyId: string;
  onUpload: (file: File,title: string,classification: string,description: string,tags: string[],requiredClearance: number) => Promise<void>;
  onDownload: (d: DocumentItem) => Promise<void>; onRetire: (d:DocumentItem)=>Promise<void>;
  username: string; isAdmin: boolean;
}) {
  const [query,setQuery]=useState(''); const [file,setFile]=useState<File|null>(null);
  const [title,setTitle]=useState(''); const [description,setDescription]=useState('');
  const [tags,setTags]=useState('general'); const [requiredClearance,setRequiredClearance]=useState('0');
  const [classification,setClassification]=useState('INTERNAL'); const [uploading,setUploading]=useState(false);
  const [error,setError]=useState('');
  const filtered=documents.filter(d=>(d.title+d.owner+d.department+d.classification+(d.tags??[]).join(' ')).toLowerCase().includes(query.toLowerCase()));
  async function submitUpload(e:FormEvent){e.preventDefault();setError('');if(!file){setError('Choose a file to upload.');return;}setUploading(true);
    try{await onUpload(file,title,classification,description,tags.split(',').map(t=>t.trim()).filter(Boolean),Number(requiredClearance));setFile(null);setTitle('');setDescription('');}
    catch(err){setError(err instanceof Error?err.message:'Upload failed');}finally{setUploading(false);}}
  async function download(doc:DocumentItem){setError('');try{await onDownload(doc);}catch(err){setError(err instanceof Error?err.message:'Download failed');}}
  async function retire(doc:DocumentItem){if(!window.confirm(`Retire "${doc.title}"? All active grants will be revoked.`))return;setError('');
    try{await onRetire(doc);}catch(err){setError(err instanceof Error?err.message:'Retirement failed');}}
  return <><SectionHeading eyebrow="DOCUMENT GOVERNANCE" title="Documents" detail="Private storage, classification and ABAC metadata"/>
    <form className="toolbar document-upload-form" onSubmit={submitUpload}>
      <input aria-label="Document title" value={title} onChange={e=>setTitle(e.target.value)} placeholder="Document title" required maxLength={240}/>
      <select aria-label="Classification" value={classification} onChange={e=>setClassification(e.target.value)}><option>PUBLIC</option><option>INTERNAL</option><option>CONFIDENTIAL</option><option>RESTRICTED</option></select>
      <input aria-label="Description" value={description} onChange={e=>setDescription(e.target.value)} placeholder="Description" maxLength={1000}/>
      <input aria-label="Tags" value={tags} onChange={e=>setTags(e.target.value)} placeholder="Tags, comma separated"/>
      <select aria-label="Required clearance" value={requiredClearance} onChange={e=>setRequiredClearance(e.target.value)}>{[0,1,2,3,4,5].map(n=><option key={n} value={n}>Clearance {n}</option>)}</select>
      <input aria-label="Choose file" type="file" accept=".pdf,.txt,.png,.jpg,.jpeg,application/pdf,text/plain,image/png,image/jpeg" onChange={e=>setFile(e.target.files?.[0]??null)} required/>
      <button className="primary" disabled={uploading}>{uploading?'Uploading…':'Upload document'}</button>
    </form>
    <p className="footnote"><LockKeyhole size={14}/> PDF, TXT, PNG and JPEG up to 10 MB. Department, classification, tags and required clearance are checked server-side.</p>
    {error&&<div className="connection-notice">{error}</div>}
    <div className="toolbar"><input className="search-input" value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search visible documents"/></div>
    <div className="table-wrap"><table><thead><tr><th>DOCUMENT</th><th>OWNER</th><th>DEPARTMENT</th><th>CLASSIFICATION / CLEARANCE</th><th>ACCESS</th></tr></thead><tbody>
      {filtered.map(d=><tr key={d.id}><td><div className="doc-cell"><div className="file-icon"><FileText size={17}/></div><div><strong>{d.title}</strong><small>{d.description||d.id}</small><small>{(d.tags??[]).join(' · ')}</small></div></div></td><td>{d.owner}</td><td>{d.department}</td><td><Badge tone={d.classification==='PUBLIC'?'green':d.classification==='RESTRICTED'?'red':'amber'}>{d.classification}</Badge><small>Clearance {d.required_clearance??0}</small></td><td><div className="toolbar"><button className="secondary" disabled={busyId===d.id} onClick={()=>onRead(d)}>Read</button><button className="secondary" onClick={()=>void download(d)}>Download</button>{(isAdmin||d.ownerUsername===username)&&<button className="secondary" onClick={()=>void retire(d)}>Retire</button>}</div></td></tr>)}
    </tbody></table>{filtered.length===0&&<div className="empty">No visible documents found.</div>}</div>
  </>;
}
type PolicyVersion = {
  id: string; policyKey: string; version: number; status: string;
  createdAt: string; publishedAt: string|null; createdBy: string|null;
  approvedBy: string|null; reviewNote: string|null;
};
const demoPolicyScenarios = [
  {id:'owner-read',accountEnabled:true,documentActive:true,owner:true,action:'READ',actorDepartment:'CSE',documentDepartment:'CSE',classification:'CONFIDENTIAL',actorClearance:3,requiredClearance:2,roles:['FACULTY'],documentTags:['research'],activeGrant:false},
  {id:'active-grant-download',accountEnabled:true,documentActive:true,owner:false,action:'DOWNLOAD',actorDepartment:'ECE',documentDepartment:'CSE',classification:'CONFIDENTIAL',actorClearance:3,requiredClearance:2,roles:['FACULTY'],documentTags:['research'],activeGrant:true},
  {id:'expired-grant-read',accountEnabled:true,documentActive:true,owner:false,action:'READ',actorDepartment:'ECE',documentDepartment:'CSE',classification:'INTERNAL',actorClearance:2,requiredClearance:1,roles:['FACULTY'],documentTags:[],activeGrant:false},
  {id:'insufficient-clearance',accountEnabled:true,documentActive:true,owner:false,action:'READ',actorDepartment:'CSE',documentDepartment:'CSE',classification:'RESTRICTED',actorClearance:1,requiredClearance:4,roles:['FACULTY'],documentTags:['exam'],activeGrant:true},
  {id:'disabled-account',accountEnabled:false,documentActive:true,owner:true,action:'READ',actorDepartment:'CSE',documentDepartment:'CSE',classification:'PUBLIC',actorClearance:5,requiredClearance:0,roles:['SECURITY_ADMIN'],documentTags:[],activeGrant:true},
  {id:'same-department-admin',accountEnabled:true,documentActive:true,owner:false,action:'READ',actorDepartment:'CSE',documentDepartment:'CSE',classification:'INTERNAL',actorClearance:3,requiredClearance:1,roles:['SECURITY_ADMIN'],documentTags:['policy'],activeGrant:false},
];
function PolicySandbox() {
  const [rulesText,setRulesText] = useState('');
  const [versions,setVersions] = useState<PolicyVersion[]>([]);
  const [activeVersion,setActiveVersion] = useState('');
  const [results,setResults] = useState<{id:string;allowed:boolean;reasonCode:string;explanation:string}[]>([]);
  const [error,setError] = useState('');
  const [busy,setBusy] = useState('');
  async function load() {
    try {
      const data=await apiGet<{versions:PolicyVersion[];activeRules:unknown;activeVersionId:string}>('/api/v1/policies');
      setVersions(data.versions); setActiveVersion(data.activeVersionId);
      if(!rulesText) setRulesText(JSON.stringify(data.activeRules,null,2));
      setError('');
    } catch(e) { setError(e instanceof Error?e.message:'Could not load policy lifecycle'); }
  }
  useEffect(()=>{void load();},[]);
  function parseRules() {
    try { return JSON.parse(rulesText); }
    catch { throw new Error('Policy JSON is invalid. Correct the JSON before simulation or submission.'); }
  }
  async function simulate() {
    setBusy('simulate');setError('');
    try {
      const result=await apiPost<{results:{id:string;allowed:boolean;reasonCode:string;explanation:string}[]}>('/api/v1/policies/simulate',{rules:parseRules(),scenarios:demoPolicyScenarios});
      setResults(result.results);
    } catch(e) {setError(e instanceof Error?e.message:'Simulation failed');}
    finally {setBusy('');}
  }
  async function submitDraft() {
    setBusy('submit');setError('');
    try {
      const draft=await apiPost<{id:string;version:number}>('/api/v1/policies/drafts',{rules:parseRules(),note:'Policy change submitted from TRUSTLENS policy studio'});
      await apiPost(`/api/v1/policies/${draft.id}/submit`,{});
      await load();
    } catch(e) {setError(e instanceof Error?e.message:'Could not submit policy draft');}
    finally {setBusy('');}
  }
  async function review(id:string,action:'approve'|'reject') {
    setBusy(id);setError('');
    try {
      await apiPost(`/api/v1/policies/${id}/${action}`,{note:action==='approve'?'Independent security review approved':'Policy rejected during review'});
      await load();
    } catch(e) {setError(e instanceof Error?e.message:'Policy review failed');}
    finally {setBusy('');}
  }
  const allowed=results.filter(r=>r.allowed).length;
  return <><SectionHeading eyebrow="POLICY INTELLIGENCE" title="Policy studio" detail="Live ABAC simulation, versioning and independent publication"/>
    <div className="notice"><ShieldAlert size={16}/> Only SECURITY_ADMIN can manage policies. Simulation uses the backend evaluator. A different administrator must approve a submitted version; the author cannot approve their own change. System guardrails always deny disabled accounts, inactive documents and insufficient clearance.</div>
    {error&&<div className="connection-notice">{error}</div>}
    <div className="sandbox-grid">
      <section className="panel">
        <div className="panel-heading"><div><span className="eyebrow">POLICY RULESET</span><h3>JSON rule editor</h3></div><SlidersHorizontal size={19}/></div>
        <textarea className="policy-editor" rows={22} spellCheck={false} value={rulesText} onChange={e=>setRulesText(e.target.value)} placeholder="Loading active policy rules…"/>
        <div className="toolbar">
          <button className="secondary" disabled={busy!==''} onClick={()=>void simulate()}>{busy==='simulate'?'Simulating…':'Run backend simulation'}</button>
          <button className="primary" disabled={busy!==''} onClick={()=>void submitDraft()}>{busy==='submit'?'Submitting…':'Create and submit draft'}</button>
        </div>
        <p className="footnote">Supported rule attributes: actions, effect, priority, subject roles/departments/clearance, resource classification/department/tags, owner and active-grant conditions. Unknown scripting is not executed.</p>
      </section>
      <section className="panel">
        <div className="panel-heading"><div><span className="eyebrow">ACTIVE VERSION</span><h3>Published policy</h3></div><ShieldCheck size={19}/></div>
        <strong>{activeVersion||'Built-in baseline policy'}</strong>
        <p className="muted">The active published version is used by the backend on every document read and download request. New versions do not rewrite historical decisions.</p>
        <div className="metrics sandbox-metrics">
          <div className="metric"><span className="metric-top">Simulated ALLOW</span><strong>{results.length?allowed:'—'}</strong></div>
          <div className="metric"><span className="metric-top">Simulated DENY</span><strong>{results.length?results.length-allowed:'—'}</strong></div>
        </div>
      </section>
    </div>
    {results.length>0&&<section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">SERVER-SIDE EVALUATION</span><h3>Simulation results</h3></div><Activity size={19}/></div>
      <div className="table-wrap"><table><thead><tr><th>SCENARIO</th><th>RESULT</th><th>REASON</th><th>EXPLANATION</th></tr></thead><tbody>
        {results.map(r=><tr key={r.id}><td>{r.id}</td><td><Badge tone={r.allowed?'green':'red'}>{r.allowed?'ALLOW':'DENY'}</Badge></td><td>{r.reasonCode}</td><td>{r.explanation}</td></tr>)}
      </tbody></table></div>
    </section>}
    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">IMMUTABLE HISTORY</span><h3>Policy versions and review queue</h3></div><History size={19}/></div>
      <div className="table-wrap"><table><thead><tr><th>VERSION</th><th>STATUS</th><th>AUTHOR</th><th>REVIEWER</th><th>ACTION</th></tr></thead><tbody>
        {versions.map(v=><tr key={v.id}><td>v{v.version}<small>{v.id}</small></td><td><Badge tone={v.status==='PUBLISHED'?'green':v.status==='REJECTED'?'red':'amber'}>{v.status}</Badge></td><td>{v.createdBy??'—'}</td><td>{v.approvedBy??'—'}</td><td>{v.status==='PENDING_APPROVAL'?<div className="toolbar"><button className="primary" disabled={busy===v.id} onClick={()=>void review(v.id,'approve')}>Approve & publish</button><button className="secondary" disabled={busy===v.id} onClick={()=>void review(v.id,'reject')}>Reject</button></div>:v.reviewNote??'—'}</td></tr>)}
      </tbody></table>{versions.length===0&&<div className="empty">No published or draft versions yet. The built-in baseline is active.</div>}</div>
    </section>
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
  id:string;username:string;displayName:string;departmentCode:string;enabled:boolean;
  mfaEnabled:boolean;roles:string;clearanceLevel:number;institutionalEmail?:string;
};
function AdminUsers() {
  const [users,setUsers]=useState<ManagedUser[]>([]);
  const [error,setError]=useState('');const [busy,setBusy]=useState('');
  const [roleChoice,setRoleChoice]=useState<Record<string,string>>({});
  const [profiles,setProfiles]=useState<Record<string,{departmentCode:string;clearanceLevel:number}>>({});
  async function load(){try{const data=await apiGet<ManagedUser[]>('/api/v1/admin/users');setUsers(data);
    setProfiles(Object.fromEntries(data.map(u=>[u.username,{departmentCode:u.departmentCode,clearanceLevel:u.clearanceLevel??0}])));setError('');}
    catch(e){setError(e instanceof Error?e.message:'Could not load accounts');}}
  useEffect(()=>{void load();},[]);
  async function toggle(user:ManagedUser){setBusy(user.username);setError('');
    try{await apiPost(`/api/v1/admin/users/${encodeURIComponent(user.username)}/${user.enabled?'disable':'enable'}`,{});await load();}
    catch(e){setError(e instanceof Error?e.message:'Account update failed');}finally{setBusy('');}}
  async function assignRole(username:string,role:string,remove=false){setBusy(username);setError('');
    try{const method=remove?'DELETE':'POST';await apiRequest(`/api/v1/admin/users/${encodeURIComponent(username)}/roles/${role}`,{method});await load();}
    catch(e){setError(e instanceof Error?e.message:'Role update failed');}finally{setBusy('');}}
  async function saveProfile(username:string){const profile=profiles[username];if(!profile)return;setBusy(username);setError('');
    try{await apiRequest(`/api/v1/admin/users/${encodeURIComponent(username)}/access-profile`,{method:'PATCH',body:JSON.stringify({...profile,attributes:{}})});await load();}
    catch(e){setError(e instanceof Error?e.message:'Access profile update failed');}finally{setBusy('');}}
  return <section className="panel">
    <div className="panel-heading"><div><span className="eyebrow">ACCOUNT LIFECYCLE & RBAC</span><h3>Campus accounts and access profiles</h3></div><Users size={19}/></div>
    <p className="muted">Assign RBAC roles and ABAC attributes. Clearance ranges from 0 to 5. The last active security administrator cannot be disabled or demoted.</p>
    {error&&<div className="connection-notice">{error}</div>}
    <div className="table-wrap"><table><thead><tr><th>ACCOUNT</th><th>ROLES</th><th>DEPARTMENT / CLEARANCE</th><th>MFA / STATUS</th><th>ACTIONS</th></tr></thead><tbody>
      {users.map(u=><tr key={u.id}>
        <td><strong>{u.displayName}</strong><small>{u.username}</small><small>{u.institutionalEmail??''}</small></td>
        <td>{u.roles||'No role'}<div className="toolbar"><select aria-label={`Role for ${u.username}`} value={roleChoice[u.username]??'FACULTY'} onChange={e=>setRoleChoice(v=>({...v,[u.username]:e.target.value}))}><option>FACULTY</option><option>STUDENT</option><option>STAFF</option><option>SECURITY_ADMIN</option></select><button className="secondary" disabled={busy===u.username} onClick={()=>void assignRole(u.username,roleChoice[u.username]??'FACULTY')}>Add</button></div>
          <div className="toolbar">{['FACULTY','STUDENT','STAFF','SECURITY_ADMIN'].filter(role=>u.roles.split(',').includes(role)).map(role=><button key={role} className="secondary" disabled={busy===u.username} onClick={()=>void assignRole(u.username,role,true)}>Remove {role}</button>)}</div>
        </td>
        <td><input aria-label={`Department for ${u.username}`} value={profiles[u.username]?.departmentCode??''} onChange={e=>setProfiles(v=>({...v,[u.username]:{...(v[u.username]??{clearanceLevel:0}),departmentCode:e.target.value}}))}/><select aria-label={`Clearance for ${u.username}`} value={profiles[u.username]?.clearanceLevel??0} onChange={e=>setProfiles(v=>({...v,[u.username]:{...(v[u.username]??{departmentCode:u.departmentCode}),clearanceLevel:Number(e.target.value)}}))}>{[0,1,2,3,4,5].map(n=><option key={n} value={n}>{n}</option>)}</select><button className="secondary" disabled={busy===u.username} onClick={()=>void saveProfile(u.username)}>Save profile</button></td>
        <td><Badge tone={u.mfaEnabled?'green':'amber'}>{u.mfaEnabled?'MFA ON':'MFA SETUP'}</Badge><Badge tone={u.enabled?'green':'red'}>{u.enabled?'ACTIVE':'DISABLED'}</Badge></td>
        <td><button className="secondary" disabled={busy===u.username} onClick={()=>void toggle(u)}>{busy===u.username?'Saving…':u.enabled?'Disable':'Enable'}</button></td>
      </tr>)}
    </tbody></table>{users.length===0&&<div className="empty">No accounts found.</div>}</div>
  </section>;
}
function People({ user, documents, onChanged }: { user: CurrentUser; documents: DocumentItem[]; onChanged: () => Promise<void> }) {
  const [grants,setGrants]=useState<GrantItem[]>([]);
  const [error,setError]=useState('');const [busy,setBusy]=useState('');
  const [subject,setSubject]=useState('');const [documentId,setDocumentId]=useState('');
  const [permission,setPermission]=useState('READ');const [purpose,setPurpose]=useState('');
  const [startsAt,setStartsAt]=useState('');const [expiresAt,setExpiresAt]=useState('');const [maxDownloads,setMaxDownloads]=useState('');
  const isAdmin=user.authorities.includes('ROLE_SECURITY_ADMIN');
  const shareable=documents.filter(d=>isAdmin||d.ownerUsername===user.username);
  async function load(){try{setGrants(await apiGet<GrantItem[]>('/api/v1/grants'));setError('');}
    catch(e){setError(e instanceof Error?e.message:'Could not load shares');}}
  useEffect(()=>{void load();},[]);
  async function revoke(id:string){setBusy(id);setError('');
    try{await apiPost(`/api/v1/grants/${id}/revoke`,{});await load();await onChanged();}
    catch(e){setError(e instanceof Error?e.message:'Revocation failed');}finally{setBusy('');}}
  async function createShare(e:FormEvent){e.preventDefault();setBusy('create');setError('');
    try{await apiPost('/api/v1/grants',{documentId,subjectUsername:subject.trim(),permission,
      startsAt:startsAt?new Date(startsAt).toISOString():null,expiresAt:expiresAt?new Date(expiresAt).toISOString():null,purpose,
      maxDownloads:maxDownloads?Number(maxDownloads):null});
      setSubject('');setPurpose('');setStartsAt('');setExpiresAt('');setMaxDownloads('');await load();await onChanged();}
    catch(e){setError(e instanceof Error?e.message:'Share creation failed');}finally{setBusy('');}}
  return <><SectionHeading eyebrow="IDENTITY GOVERNANCE" title="People & roles" detail="RBAC, ABAC attributes and controlled sharing"/>
    {error&&<div className="connection-notice">{error}</div>}
    {isAdmin&&<><FacultyRequests/><AdminUsers/>
      <div className="notice"><ShieldCheck size={16}/> Administrators can assign roles and access profiles. Policy publication requires a second administrator's independent approval.</div>
    </>}
    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">CONTROLLED SHARING</span><h3>Share a document</h3></div><LockKeyhole size={19}/></div>
      <p className="muted">Owners can share within their department. Security administrators can share across departments. Every grant has an explicit permission, expiry and optional download cap.</p>
      {shareable.length===0?<div className="empty">Upload a document to create a share grant.</div>:<form className="toolbar" onSubmit={createShare}>
        <select aria-label="Document to share" value={documentId} onChange={e=>setDocumentId(e.target.value)} required>
          <option value="">Choose an owned document</option>{shareable.map(d=><option key={d.id} value={d.id}>{d.title} · {d.classification}</option>)}
        </select>
        <input aria-label="Recipient username" value={subject} onChange={e=>setSubject(e.target.value)} placeholder="Recipient username" required/>
        <select aria-label="Permission" value={permission} onChange={e=>setPermission(e.target.value)}><option value="READ">READ</option><option value="DOWNLOAD">DOWNLOAD</option></select>
        <input aria-label="Share purpose" value={purpose} onChange={e=>setPurpose(e.target.value)} placeholder="Purpose / reason" maxLength={500}/>
        <input aria-label="Grant starts at" type="datetime-local" value={startsAt} onChange={e=>setStartsAt(e.target.value)}/>
        <input aria-label="Grant expiry" type="datetime-local" value={expiresAt} onChange={e=>setExpiresAt(e.target.value)}/>
        <input aria-label="Maximum downloads" type="number" min="1" max="10000" value={maxDownloads} onChange={e=>setMaxDownloads(e.target.value)} placeholder="Download limit (optional)"/>
        <button className="primary" disabled={busy==='create'}>{busy==='create'?'Sharing…':'Create share grant'}</button>
      </form>}
    </section>
    <section className="panel">
      <div className="panel-heading"><div><span className="eyebrow">GRANT LEDGER</span><h3>Current and historical shares</h3></div><History size={19}/></div>
      <div className="table-wrap"><table><thead><tr><th>DOCUMENT / PURPOSE</th><th>RECIPIENT</th><th>PERMISSION</th><th>EXPIRY / DOWNLOADS</th><th>STATUS</th><th/></tr></thead><tbody>
        {grants.map(g=><tr key={g.id}><td>{g.document}<small>{g.purpose||'No purpose specified'}</small></td><td>{g.subject}</td><td>{g.permission}</td><td>{g.expiresAt?new Date(g.expiresAt).toLocaleString():'No expiry'}<small>{g.maxDownloads?(`${g.downloadCount??0}/${g.maxDownloads} downloads`):'No download cap'}</small></td><td><Badge tone={g.status==='ACTIVE'?'green':g.status==='REVOKED'||g.status==='EXPIRED'||g.status==='EXHAUSTED'?'red':'amber'}>{g.status??(g.revokedAt?'REVOKED':'ACTIVE')}</Badge></td><td>{!g.revokedAt&&<button className="secondary" disabled={busy===g.id} onClick={()=>void revoke(g.id)}>{busy===g.id?'Revoking…':'Revoke'}</button>}</td></tr>)}
      </tbody></table>{grants.length===0&&<div className="empty">No grants created yet.</div>}</div>
    </section>
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
        username={user.username} isAdmin={user.authorities.includes('ROLE_SECURITY_ADMIN')}
        onUpload={async (file,title,classification,description,tags,requiredClearance) => { const form=new FormData(); form.append('file',file); form.append('title',title); form.append('classification',classification); form.append('description',description); tags.forEach(tag=>form.append('tags',tag)); form.append('requiredClearance',String(requiredClearance)); await apiUpload('/api/v1/documents',form); await refreshData(); setMessage('Document uploaded to private storage.'); }}
        onDownload={async doc => { await apiDownload(`/api/v1/documents/${doc.id}/content`,doc.title); await refreshData(); setMessage('Download authorized by the current server-side policy.'); }}
        onRetire={async doc=>{await apiPost(`/api/v1/documents/${doc.id}/retire`,{});await refreshData();setMessage('Document retired and active grants revoked.');}}/>}
      
      {page==='People & roles' && <People user={user} documents={documents} onChanged={refreshData}/>}
      {page==='Policy sandbox' && <PolicySandbox/>}
      {page==='Decisions' && <Decisions decisions={decisions}/>}
      {page==='Audit history' && <AuditHistory audit={audit}/>}
      <footer><span>TRUSTLENS · FULL-STACK VERTICAL SLICE</span><span>Session user · {user.username}</span></footer>
    </section>
  </main>;
}
