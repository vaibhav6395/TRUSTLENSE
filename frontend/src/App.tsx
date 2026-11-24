import { useEffect, useMemo, useState } from 'react';
import {
  Activity, ArrowDownToLine, ArrowUpRight, BookOpen, Check, ChevronRight,
  CircleHelp, Clock3, FileLock2, FileText, Fingerprint, Gauge, History,
  LayoutDashboard, LockKeyhole, LogOut, Plus, Search, Settings2, ShieldAlert,
  ShieldCheck, SlidersHorizontal, Users, X
} from 'lucide-react';
import { apiGet, type Health } from './api';

type Page = 'Overview' | 'Documents' | 'People & roles' | 'Policy sandbox' | 'Decisions' | 'Audit history';
type DocumentItem = { id: string; title: string; owner: string; department: string; classification: string; updated: string; access: string };
const docs: DocumentItem[] = [
  { id: 'DOC-1042', title: 'Campus Network Architecture', owner: 'IT Security', department: 'IT', classification: 'CONFIDENTIAL', updated: 'Today, 09:42', access: 'Restricted group' },
  { id: 'DOC-1038', title: 'Student Event Guidelines', owner: 'Student Affairs', department: 'ADMIN', classification: 'INTERNAL', updated: 'Yesterday', access: 'Department' },
  { id: 'DOC-1021', title: 'Public Research Overview', owner: 'Research Office', department: 'RESEARCH', classification: 'PUBLIC', updated: 'Nov 20, 2026', access: 'Campus-wide' },
  { id: 'DOC-1016', title: 'IT Asset Register', owner: 'IT Operations', department: 'IT', classification: 'RESTRICTED', updated: 'Nov 19, 2026', access: 'Named users' },
];
const people = [
  { name: 'Aarav Mehta', email: 'aarav.mehta@campus.example', role: 'Security Admin', dept: 'IT', state: 'Active' },
  { name: 'Mira Sharma', email: 'mira.sharma@campus.example', role: 'Faculty', dept: 'RESEARCH', state: 'Active' },
  { name: 'Kabir Rao', email: 'kabir.rao@campus.example', role: 'Student', dept: 'CSE', state: 'Active' },
  { name: 'Isha Verma', email: 'isha.verma@campus.example', role: 'Department Admin', dept: 'ADMIN', state: 'Pending' },
];
const events = [
  { time: '10:42:18', actor: 'Aarav Mehta', action: 'Policy version published', target: 'Document access policy v3', result: 'SUCCESS' },
  { time: '10:31:04', actor: 'Mira Sharma', action: 'Document read request', target: 'Public Research Overview', result: 'ALLOWED' },
  { time: '10:18:52', actor: 'Kabir Rao', action: 'Document download request', target: 'Campus Network Architecture', result: 'DENIED' },
  { time: '09:56:11', actor: 'Aarav Mehta', action: 'Access grant revoked', target: 'DOC-1016 · IT Asset Register', result: 'SUCCESS' },
];
const navGroups = [
  { label: 'WORKSPACE', items: [{ name: 'Overview' as Page, icon: LayoutDashboard }, { name: 'Documents' as Page, icon: FileLock2 }, { name: 'People & roles' as Page, icon: Users }] },
  { label: 'SECURITY', items: [{ name: 'Policy sandbox' as Page, icon: SlidersHorizontal }, { name: 'Decisions' as Page, icon: Fingerprint }, { name: 'Audit history' as Page, icon: History }] },
];

function Badge({ children, tone = 'neutral' }: { children: string; tone?: 'neutral' | 'green' | 'amber' | 'red' }) {
  return <span className={`badge ${tone}`}><i />{children}</span>;
}
function SectionHeading({ eyebrow, title, detail }: { eyebrow: string; title: string; detail?: string }) {
  return <div className="section-heading"><div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2></div>{detail && <span className="muted">{detail}</span>}</div>;
}
function Overview({ setPage, health }: { setPage: (page: Page) => void; health: Health | null }) {
  const stats = [
    { label: 'Access decisions', value: '—', note: 'Connect decision service', icon: Fingerprint },
    { label: 'Active grants', value: '—', note: 'Connect grant service', icon: FileLock2 },
    { label: 'Published policies', value: '—', note: 'Connect policy service', icon: BookOpen },
    { label: 'Audit events', value: '—', note: 'Connect audit service', icon: Activity },
  ];
  return <>
    <section className="hero">
      <div className="hero-copy"><span className="hero-tag"><ShieldCheck size={14}/> EXPLAINABLE ACCESS CONTROL</span>
        <h2>Trust every request.<br/><em>Verify every decision.</em></h2>
        <p>Govern campus document access, preview policy impact, and understand the evidence behind each authorization decision.</p>
        <button className="primary" onClick={() => setPage('Policy sandbox')}>Open policy sandbox <ArrowUpRight size={16}/></button>
      </div>
      <div className="hero-art" aria-hidden="true"><div className="ring ring-one"/><div className="ring ring-two"/><div className="shield-core"><ShieldCheck size={54}/></div><div className="orbit orbit-a"><LockKeyhole size={17}/></div><div className="orbit orbit-b"><Fingerprint size={18}/></div><div className="orbit orbit-c"><Activity size={16}/></div></div>
    </section>
    <div className="notice"><CircleHelp size={16}/><span>Development preview: metrics below are intentionally blank until their backend services are implemented.</span></div>
    <SectionHeading eyebrow="SYSTEM SNAPSHOT" title="Governance at a glance" detail="Live metrics will appear when connected"/>
    <section className="metrics">{stats.map(({ label, value, note, icon: Icon }) => <article className="metric" key={label}><div className="metric-top"><span>{label}</span><Icon size={18}/></div><strong>{value}</strong><small>{note}</small></article>)}</section>
    <SectionHeading eyebrow="CONTROL CENTER" title="Workspace modules" detail="Foundation build · v0.2"/>
    <section className="module-grid">
      {[
        { name: 'Document access', detail: 'Browse document metadata and access scope', icon: FileLock2, page: 'Documents' as Page },
        { name: 'Policy sandbox', detail: 'Preview policy changes before publication', icon: Gauge, page: 'Policy sandbox' as Page },
        { name: 'Decision explorer', detail: 'Understand allow and deny outcomes', icon: Fingerprint, page: 'Decisions' as Page },
        { name: 'Audit history', detail: 'Review security-relevant activity', icon: History, page: 'Audit history' as Page },
        { name: 'People & roles', detail: 'Review identities and role assignments', icon: Users, page: 'People & roles' as Page },
        { name: 'API connection', detail: health?.status === 'UP' ? 'Backend health endpoint is responding' : 'Waiting for backend health endpoint', icon: Activity, page: 'Overview' as Page },
      ].map(({ name, detail, icon: Icon, page }) => <button className="module" key={name} onClick={() => setPage(page)}><div className="module-icon"><Icon size={20}/></div><div className="module-title"><h3>{name}</h3><ChevronRight size={16}/></div><p>{detail}</p><div className="module-foot">Explore module <ArrowUpRight size={14}/></div></button>)}
    </section>
  </>;
}
function Documents() {
  const [query, setQuery] = useState('');
  const [classification, setClassification] = useState('ALL');
  const filtered = useMemo(() => docs.filter(d => (classification === 'ALL' || d.classification === classification) && (d.title + d.owner + d.id).toLowerCase().includes(query.toLowerCase())), [query, classification]);
  return <><SectionHeading eyebrow="DOCUMENT GOVERNANCE" title="Documents" detail="Sample metadata · synthetic data only"/>
    <div className="toolbar"><div className="searchbox"><Search size={16}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search documents or owners"/></div><select value={classification} onChange={e => setClassification(e.target.value)} aria-label="Filter classification"><option value="ALL">All classifications</option><option>PUBLIC</option><option>INTERNAL</option><option>CONFIDENTIAL</option><option>RESTRICTED</option></select><button className="secondary" disabled><Plus size={15}/> Upload (backend pending)</button></div>
    <div className="table-wrap"><table><thead><tr><th>DOCUMENT</th><th>OWNER</th><th>CLASSIFICATION</th><th>ACCESS SCOPE</th><th>UPDATED</th><th/></tr></thead><tbody>{filtered.map(d => <tr key={d.id}><td><div className="doc-cell"><div className="file-icon"><FileText size={17}/></div><div><strong>{d.title}</strong><small>{d.id} · {d.department}</small></div></div></td><td>{d.owner}</td><td><Badge tone={d.classification === 'PUBLIC' ? 'green' : d.classification === 'RESTRICTED' ? 'red' : d.classification === 'CONFIDENTIAL' ? 'amber' : 'neutral'}>{d.classification}</Badge></td><td>{d.access}</td><td>{d.updated}</td><td><button className="icon-button" title="Download unavailable in preview" disabled><ArrowDownToLine size={16}/></button></td></tr>)}</tbody></table>{filtered.length === 0 && <div className="empty">No sample documents match your filters.</div>}</div>
    <p className="footnote"><LockKeyhole size={14}/> This screen displays synthetic metadata only. Document upload and download will be enabled after server-side authorization and storage integration.</p>
  </>;
}
function People() {
  return <><SectionHeading eyebrow="IDENTITY GOVERNANCE" title="People & roles" detail="Synthetic directory preview"/><div className="notice"><ShieldAlert size={16}/> Role labels shown here are sample data, not authenticated permissions. Effective privileges must come from the backend.</div><div className="table-wrap"><table><thead><tr><th>PERSON</th><th>ROLE</th><th>DEPARTMENT</th><th>ACCOUNT</th></tr></thead><tbody>{people.map(p => <tr key={p.email}><td><div className="person-cell"><div className="avatar">{p.name.split(' ').map(x => x[0]).join('')}</div><div><strong>{p.name}</strong><small>{p.email}</small></div></div></td><td>{p.role}</td><td>{p.dept}</td><td><Badge tone={p.state === 'Active' ? 'green' : 'amber'}>{p.state}</Badge></td></tr>)}</tbody></table></div></>;
}
function PolicySandbox() {
  const [department, setDepartment] = useState('IT');
  const [classification, setClassification] = useState('CONFIDENTIAL');
  const [action, setAction] = useState('READ');
  const [ran, setRan] = useState(false);
  return <><SectionHeading eyebrow="POLICY INTELLIGENCE" title="Policy sandbox" detail="Local UI preview · no policy is published"/><div className="sandbox-grid"><section className="panel"><div className="panel-heading"><div><span className="eyebrow">SCENARIO INPUT</span><h3>Explore a policy condition</h3></div><SlidersHorizontal size={18}/></div><label>Actor department<select value={department} onChange={e => {setDepartment(e.target.value);setRan(false)}}><option>IT</option><option>ADMIN</option><option>RESEARCH</option><option>CSE</option></select></label><label>Document classification<select value={classification} onChange={e => {setClassification(e.target.value);setRan(false)}}><option>PUBLIC</option><option>INTERNAL</option><option>CONFIDENTIAL</option><option>RESTRICTED</option></select></label><label>Requested action<select value={action} onChange={e => {setAction(e.target.value);setRan(false)}}><option>READ</option><option>DOWNLOAD</option></select></label><button className="primary full" onClick={() => setRan(true)}>Preview scenario <ArrowUpRight size={16}/></button><p className="footnote">This control only previews form state. It does not evaluate actual access, query data, or change a policy.</p></section><section className="panel result-panel"><div className="panel-heading"><div><span className="eyebrow">SIMULATION OUTPUT</span><h3>Scenario summary</h3></div><Gauge size={18}/></div>{ran ? <div className="preview-result"><div className="result-symbol"><Activity size={24}/></div><h4>Scenario captured</h4><p>The UI has captured your inputs. A server-side policy simulator is not connected yet, so no ALLOW/DENY outcome is inferred.</p><dl><dt>Department</dt><dd>{department}</dd><dt>Classification</dt><dd>{classification}</dd><dt>Action</dt><dd>{action}</dd></dl><Badge tone="amber">Evaluation pending</Badge></div> : <div className="placeholder"><div className="placeholder-icon"><SlidersHorizontal size={23}/></div><strong>Ready for a scenario</strong><p>Choose conditions and preview the captured inputs here.</p></div>}</section></div></>;
}
function Decisions() {
  return <><SectionHeading eyebrow="EXPLAINABILITY" title="Decision explorer" detail="Illustrative event samples"/><div className="notice"><CircleHelp size={16}/> These examples are static UI fixtures, not live authorization decisions.</div><div className="table-wrap"><table><thead><tr><th>TIME</th><th>ACTOR</th><th>REQUEST</th><th>RESOURCE</th><th>OUTCOME</th></tr></thead><tbody>{events.slice(1,3).map((e,i) => <tr key={e.time}><td>{e.time}</td><td>{e.actor}</td><td>{e.action}</td><td>{e.target}</td><td><Badge tone={i === 0 ? 'green' : 'red'}>{i === 0 ? 'ALLOW' : 'DENY'}</Badge></td></tr>)}</tbody></table></div><div className="panel explanation"><span className="eyebrow">DECISION RECORD REQUIREMENTS</span><h3>What a real decision will explain</h3><div className="requirement-list"><span><Check size={15}/> Authenticated actor and current account state</span><span><Check size={15}/> Requested resource and action</span><span><Check size={15}/> Matching grant, expiry, and revocation status</span><span><Check size={15}/> Policy version and machine-readable reason code</span><span><Check size={15}/> Evidence snapshot used at decision time</span></div></div></>;
}
function AuditHistory() {
  return <><SectionHeading eyebrow="ACCOUNTABILITY" title="Audit history" detail="Illustrative event samples"/><div className="notice"><ShieldAlert size={16}/> Sample events only. Hash-chain verification is not implemented yet.</div><div className="timeline">{events.map(e => <article className="timeline-item" key={e.time}><div className="timeline-dot"/><div className="timeline-content"><div className="timeline-top"><strong>{e.action}</strong><span>{e.time}</span></div><p>{e.actor} <span>·</span> {e.target}</p><Badge tone={e.result === 'DENIED' ? 'red' : 'green'}>{e.result}</Badge></div></article>)}</div></>;
}
export default function App() {
  const [page, setPage] = useState<Page>('Overview');
  const [health, setHealth] = useState<Health | null>(null);
  const [apiError, setApiError] = useState('');
  useEffect(() => { apiGet<Health>('/api/v1/health').then(setHealth).catch(() => setApiError('API not connected · Start the Spring Boot service to see live health.')); }, []);
  return <main className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><ShieldCheck size={22}/></div><div><strong>TRUSTLENS</strong><small>ACCESS GOVERNANCE</small></div></div>
      {navGroups.map(group => <div key={group.label}><div className="nav-label">{group.label}</div>{group.items.map(item => <button key={item.name} className={`nav ${page === item.name ? 'active' : ''}`} onClick={() => setPage(item.name)}><item.icon size={17}/>{item.name}</button>)}</div>)}
      <div className="sidebar-bottom"><div className="secure-icon"><ShieldCheck size={17}/></div><strong>Zero-trust model</strong><p>Every protected request must be evaluated by the server.</p></div>
    </aside>
    <section className="content">
      <header className="topbar"><div><span className="eyebrow">CAMPUS SECURITY PLATFORM</span><h1>{page}</h1></div><div className="topbar-right"><div className="status-pill"><span className={health?.status === 'UP' ? 'dot online' : 'dot'}/>{health?.status === 'UP' ? 'API connected' : 'API offline'}</div><div className="user-chip"><div className="avatar">VT</div><span>Development</span></div></div></header>
      {apiError && <div className="connection-notice"><Activity size={15}/>{apiError}</div>}
      {page === 'Overview' && <Overview setPage={setPage} health={health}/>}
      {page === 'Documents' && <Documents/>}
      {page === 'People & roles' && <People/>}
      {page === 'Policy sandbox' && <PolicySandbox/>}
      {page === 'Decisions' && <Decisions/>}
      {page === 'Audit history' && <AuditHistory/>}
      <footer><span>TRUSTLENS · DEVELOPMENT BUILD</span><span>{health ? `Health check · ${new Date(health.timestamp).toLocaleTimeString()}` : 'No live service data'}</span></footer>
    </section>
  </main>;
}
