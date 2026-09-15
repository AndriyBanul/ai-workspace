import React, { useEffect, useMemo, useState } from 'react';
import { basicAuth, createApi } from './api.js';
import { Empty, Icon, Notice, Submit, useAction } from './components.jsx';
import Library from './Library.jsx';
import Ask from './Ask.jsx';
import Studio from './Studio.jsx';
import Activity from './Activity.jsx';

function Login({ onLogin }) {
  const [register, setRegister] = useState(false), action = useAction();
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget), email = form.get('email').trim(), password = form.get('password');
    await action.run(async () => {
      if (register) await createApi()('/auth/register', { method: 'POST', json: { email, password, displayName: form.get('displayName') } });
      const authorization = basicAuth(email, password);
      const user = await createApi(authorization)('/auth/me');
      onLogin({ user, authorization });
    });
  }
  return <div className="login-shell"><section className="login-story"><a className="brand" href="#"><span className="brand-symbol"><Icon name="studio"/></span>AI Workspace</a><div><p className="eyebrow">A HOME FOR YOUR KNOWLEDGE</p><h1>Bring it all together.<br/><em>Make something new.</em></h1><p>Documents, conversations, images, and ideas.<br/>One thoughtful space to work with them.</p><div className="orbit" aria-hidden="true"><span>DOCUMENTS</span><span>IMAGES</span><span>AUDIO</span><span>VIDEO</span><Icon name="studio" size={58}/></div></div><small>Organize · Explore · Create</small></section><main className="auth"><p className="eyebrow">YOUR NEXT IDEA STARTS HERE</p><h2>{register ? 'Create your account' : 'Welcome back'}</h2><p>{register ? 'Set up your personal workspace.' : 'Sign in to continue your work.'}</p><form onSubmit={submit}><fieldset disabled={action.busy}>{register && <label>Your name<input name="displayName" autoComplete="name" placeholder="Alex Morgan"/></label>}<label>Email address<input name="email" type="email" required autoComplete="username" placeholder="you@example.com"/></label><label>Password<input name="password" type="password" required minLength={register ? 8 : undefined} autoComplete={register ? 'new-password' : 'current-password'}/></label><Notice error={action.error}/><Submit busy={action.busy}>{register ? 'Create account' : 'Sign in'}<Icon name="arrow"/></Submit></fieldset></form><button className="text-button" onClick={() => setRegister(!register)}>{register ? 'Already have an account? Sign in' : 'New here? Create an account'}</button><small className="muted">Credentials stay in memory and are cleared when you sign out or reload.</small></main></div>;
}

export default function App() {
  const [session, setSession] = useState(null);
  return session ? <WorkspaceApp session={session} logout={() => setSession(null)}/> : <Login onLogin={setSession}/>;
}

function WorkspaceApp({ session, logout }) {
  const api = useMemo(() => createApi(session.authorization), [session.authorization]);
  const [workspaces, setWorkspaces] = useState([]), [selected, setSelected] = useState(''), [view, setView] = useState('library');
  const [creating, setCreating] = useState(false), [name, setName] = useState('');
  const [deleting, setDeleting] = useState(false), action = useAction();
  const workspace = workspaces.find(w => w.id === selected);
  const refresh = () => action.run(async () => {
    const list = await api('/workspaces'); setWorkspaces(list);
    setSelected(current => list.some(w => w.id === current) ? current : list[0]?.id || '');
  });
  useEffect(() => { const controller = new AbortController(); api('/workspaces', { signal: controller.signal }).then(list => { setWorkspaces(list); setSelected(list[0]?.id || ''); }).catch(error => { if (error.name !== 'AbortError') action.run(() => { throw error; }); }); return () => controller.abort(); }, [api]);
  async function create(event) { event.preventDefault(); await action.run(async () => { const w = await api('/workspaces', { method: 'POST', json: { name: name.trim() } }); setWorkspaces(list => [w, ...list]); setSelected(w.id); setName(''); setCreating(false); }); }
  async function remove() { await action.run(async () => { await api(`/workspaces/${workspace.id}`, { method: 'DELETE' }); setWorkspaces(list => list.filter(w => w.id !== workspace.id)); setSelected(''); setDeleting(false); }); }
  const views = { library: ['Library', 'Your sources, connected.'], ask: ['Ask your workspace', 'Find answers in what you know.'], studio: ['Creative studio', 'Turn your next idea into something tangible.'], activity: ['Activity', 'Follow your files from upload to understanding.'] };
  return <div className="app-shell"><aside className="sidebar"><a className="brand" href="#" onClick={() => setView('library')}><span className="brand-symbol"><Icon name="studio"/></span>AI Workspace</a><div className="workspace-picker"><label>WORKSPACE<select aria-label="Select workspace" value={selected} onChange={e => { setSelected(e.target.value); setDeleting(false); }}><option value="">Select a workspace</option>{workspaces.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}</select></label><button className="new-workspace" onClick={() => setCreating(!creating)}><Icon name="plus" size={16}/>New workspace</button>{creating && <form onSubmit={create}><label className="sr-only" htmlFor="workspace-name">Workspace name</label><input id="workspace-name" autoFocus required value={name} onChange={e => setName(e.target.value)} placeholder="Name your workspace"/><Submit busy={action.busy}>Create</Submit></form>}</div><nav aria-label="Main navigation">{Object.entries(views).map(([key, [title]]) => <button key={key} className={view === key ? 'active' : ''} onClick={() => setView(key)}><Icon name={key}/>{key === 'ask' ? 'Ask workspace' : title}{view === key && <span className="nav-dot"/>}</button>)}</nav><div className="sidebar-note"><Icon name="studio"/><strong>A little more possibility.</strong><p>Add a source. Ask a question.<br/>See where it takes you.</p></div><div className="account"><span className="avatar">{(session.user.displayName || session.user.email)[0].toUpperCase()}</span><div><strong>{session.user.displayName || 'My account'}</strong><small title={session.user.email}>{session.user.email}</small></div><button className="text-button" onClick={logout}>Sign out</button></div></aside><main className="main"><header className="topbar"><span><span className="muted">Workspace / </span>{workspace?.name || 'Getting started'}</span><div className="top-actions"><a href="/swagger-ui.html" target="_blank" rel="noreferrer">API reference ↗</a><button className="text-button" onClick={refresh} disabled={action.busy}>Refresh</button></div></header><div className="page"><Notice error={action.error}/><div className="page-heading"><div><p className="eyebrow">YOUR WORK, WITH CONTEXT</p><h1>{views[view][0]}</h1><p>{views[view][1]}</p></div>{workspace && <button className="text-button danger" onClick={() => setDeleting(!deleting)}>Delete workspace</button>}</div>{deleting && workspace && <div className="confirmation" role="alert"><strong>Delete “{workspace.name}”?</strong><p>This permanently removes its files and knowledge.</p><button onClick={() => setDeleting(false)}>Keep workspace</button><button className="danger" disabled={action.busy} onClick={remove}>Delete permanently</button></div>}{!workspace && view !== 'studio' ? <Empty title="A fresh start">Create or select a workspace to add your first sources.</Empty> : <WorkspaceContent key={selected || 'no-workspace'} api={api} workspace={workspace} view={view} userId={session.user.id}/>}</div><footer>AI Workspace <span>One place. Many possibilities.</span></footer></main></div>;
}

function WorkspaceContent({ api, workspace, view, userId }) {
  const jobKey = `ai-workspace.jobs.${userId}.${workspace?.id}`;
  const [jobs, setJobs] = useState(() => { try { return JSON.parse(localStorage.getItem(jobKey) || '[]'); } catch { return []; } });
  const addJob = job => setJobs(list => { const next = [{ ...job, createdAt: new Date().toISOString() }, ...list.filter(j => j.jobId !== job.jobId)].slice(0, 50); localStorage.setItem(jobKey, JSON.stringify(next)); return next; });
  return <>{view === 'library' && <Library api={api} workspace={workspace} addJob={addJob}/>} {view === 'ask' && <Ask api={api} workspace={workspace}/>} {view === 'studio' && <Studio api={api} workspace={workspace}/>} {view === 'activity' && <Activity api={api} workspace={workspace} jobs={jobs} addJob={addJob}/>}</>;
}
