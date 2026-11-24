import { useEffect, useState } from 'react';
import { Activity, ArrowUpRight, BookOpen, FileLock2, Fingerprint, Gauge, History, LockKeyhole, ShieldCheck, ShieldAlert, Users } from 'lucide-react';
import { apiGet, type Health } from './api';

const metrics = [
  { label: 'Access decisions', value: '—', note: 'Decision telemetry not connected', icon: Fingerprint },
  { label: 'Active grants', value: '—', note: 'Grant service pending', icon: FileLock2 },
  { label: 'Policy versions', value: '—', note: 'Version service pending', icon: BookOpen },
  { label: 'Security signals', value: '—', note: 'Advisory monitoring pending', icon: Activity },
];

const modules = [
  { name: 'Document access', detail: 'View documents available to your account', icon: FileLock2, status: 'Backend pending' },
  { name: 'Policy sandbox', detail: 'Preview access impact before publishing', icon: Gauge, status: 'Backend pending' },
  { name: 'Decision explorer', detail: 'Understand allow and deny outcomes', icon: Fingerprint, status: 'Backend pending' },
  { name: 'Audit history', detail: 'Review security-relevant activity', icon: History, status: 'Backend pending' },
  { name: 'People & roles', detail: 'Manage identities and role assignments', icon: Users, status: 'Backend pending' },
  { name: 'Security posture', detail: 'Review configuration and validation signals', icon: ShieldCheck, status: 'Backend pending' },
];

export default function App() {
  const [health, setHealth] = useState<Health | null>(null);
  const [apiError, setApiError] = useState('');

  useEffect(() => {
    apiGet<Health>('/api/v1/health').then(setHealth).catch(() => setApiError('API unavailable — start the backend to connect live health status.'));
  }, []);

  return <main className="shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><ShieldCheck size={22}/></div><div><strong>TRUSTLENS</strong><small>ACCESS GOVERNANCE</small></div></div>
      <div className="nav-label">WORKSPACE</div>
      <a className="nav active"><Gauge size={17}/> Overview</a>
      <a className="nav"><FileLock2 size={17}/> Documents</a>
      <a className="nav"><Users size={17}/> People & roles</a>
      <div className="nav-label">SECURITY</div>
      <a className="nav"><LockKeyhole size={17}/> Policy sandbox</a>
      <a className="nav"><Fingerprint size={17}/> Decisions</a>
      <a className="nav"><History size={17}/> Audit history</a>
      <div className="sidebar-bottom"><div className="secure-icon"><ShieldCheck size={17}/></div><strong>Zero-trust model</strong><p>Every protected request is evaluated by the server.</p></div>
    </aside>
    <section className="content">
      <header className="topbar"><div><span className="eyebrow">CAMPUS SECURITY PLATFORM</span><h1>Overview</h1></div><div className="status-pill"><span className={health?.status === 'UP' ? 'dot online' : 'dot'} />{health?.status === 'UP' ? 'API connected' : 'Connecting to API'}</div></header>
      <section className="hero"><div className="hero-copy"><span className="hero-tag"><ShieldCheck size={14}/> EXPLAINABLE ACCESS CONTROL</span><h2>Trust every request.<br/><em>Verify every decision.</em></h2><p>One place to govern document access, understand policy impact, and inspect the evidence behind each decision.</p><button className="primary" onClick={() => document.getElementById('modules')?.scrollIntoView({ behavior: 'smooth' })}>Explore workspace <ArrowUpRight size={16}/></button></div><div className="hero-art"><div className="ring ring-one"/><div className="ring ring-two"/><div className="shield-core"><ShieldCheck size={54}/></div><div className="orbit orbit-a"><LockKeyhole size={17}/></div><div className="orbit orbit-b"><Fingerprint size={18}/></div><div className="orbit orbit-c"><Activity size={16}/></div></div></section>
      {apiError && <div className="notice"><ShieldAlert size={16}/>{apiError}</div>}
      <div className="section-heading"><div><span className="eyebrow">SYSTEM SNAPSHOT</span><h3>Governance at a glance</h3></div><span className="muted">Live metrics will appear as services are connected</span></div>
      <section className="metrics">{metrics.map(({ label, value, note, icon: Icon }) => <article className="metric" key={label}><div className="metric-top"><span>{label}</span><Icon size={18}/></div><strong>{value}</strong><small>{note}</small></article>)}</section>
      <div className="section-heading" id="modules"><div><span className="eyebrow">CONTROL CENTER</span><h3>Workspace modules</h3></div><span className="muted">Foundation build · v0.1</span></div>
      <section className="module-grid">{modules.map(({ name, detail, icon: Icon, status }) => <article className="module" key={name}><div className="module-icon"><Icon size={20}/></div><div className="module-title"><h4>{name}</h4><span>{status}</span></div><p>{detail}</p><div className="module-foot">Open module <ArrowUpRight size={14}/></div></article>)}</section>
      <footer><span>TRUSTLENS · DEVELOPMENT BUILD</span><span>{health ? `Last health check: ${new Date(health.timestamp).toLocaleTimeString()}` : 'Awaiting backend connection'}</span></footer>
    </section>
  </main>;
}
