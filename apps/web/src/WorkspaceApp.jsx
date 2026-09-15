import React, { useEffect, useMemo, useState } from 'react';
import { createApi } from './api.js';
import { Empty, Notice, useAction } from './components.jsx';
import WorkspaceContent from './WorkspaceContent.jsx';
import WorkspaceSidebar from './WorkspaceSidebar.jsx';

export const workspaceViews = {
  library: ['Library', 'Your sources, connected.'],
  ask: ['Ask your workspace', 'Find answers in what you know.'],
  studio: ['Creative studio', 'Turn your next idea into something tangible.'],
  activity: ['Activity', 'Follow your files from upload to understanding.'],
};

export default function WorkspaceApp({ session, logout, api: providedApi }) {
  const authenticatedApi = useMemo(() => createApi(session.authorization), [session.authorization]);
  const api = providedApi || authenticatedApi;
  const [workspaces, setWorkspaces] = useState([]);
  const [selected, setSelected] = useState('');
  const [view, setView] = useState('library');
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [deleting, setDeleting] = useState(false);
  const action = useAction();
  const workspace = workspaces.find(candidate => candidate.id === selected);

  const refresh = () => action.run(async () => {
    const list = await api('/workspaces');
    setWorkspaces(list);
    setSelected(current => list.some(candidate => candidate.id === current) ? current : list[0]?.id || '');
  });

  useEffect(() => {
    const controller = new AbortController();
    api('/workspaces', { signal: controller.signal })
      .then(list => {
        setWorkspaces(list);
        setSelected(list[0]?.id || '');
      })
      .catch(error => {
        if (error.name !== 'AbortError') action.run(() => { throw error; });
      });
    return () => controller.abort();
  }, [api]);

  async function createWorkspace(event) {
    event.preventDefault();
    await action.run(async () => {
      const created = await api('/workspaces', { method: 'POST', json: { name: name.trim() } });
      setWorkspaces(current => [created, ...current]);
      setSelected(created.id);
      setName('');
      setCreating(false);
    });
  }

  async function deleteWorkspace() {
    await action.run(async () => {
      await api(`/workspaces/${workspace.id}`, { method: 'DELETE' });
      setWorkspaces(current => current.filter(candidate => candidate.id !== workspace.id));
      setSelected('');
      setDeleting(false);
    });
  }

  function selectWorkspace(workspaceId) {
    setSelected(workspaceId);
    setDeleting(false);
  }

  return <div className="app-shell">
    <WorkspaceSidebar
      session={session}
      workspaces={workspaces}
      selected={selected}
      view={view}
      views={workspaceViews}
      creating={creating}
      workspaceName={name}
      busy={action.busy}
      onSelectWorkspace={selectWorkspace}
      onSelectView={setView}
      onToggleCreate={() => setCreating(current => !current)}
      onWorkspaceNameChange={setName}
      onCreateWorkspace={createWorkspace}
      onLogout={logout}
    />
    <main className="main">
      <header className="topbar">
        <span><span className="muted">Workspace / </span>{workspace?.name || 'Getting started'}</span>
        <div className="top-actions">
          <a href="/swagger-ui.html" target="_blank" rel="noreferrer">API reference ↗</a>
          <button className="text-button" onClick={refresh} disabled={action.busy}>Refresh</button>
        </div>
      </header>
      <div className="page">
        <Notice error={action.error}/>
        <div className="page-heading">
          <div><p className="eyebrow">YOUR WORK, WITH CONTEXT</p><h1>{workspaceViews[view][0]}</h1><p>{workspaceViews[view][1]}</p></div>
          {workspace && <button className="text-button danger" onClick={() => setDeleting(current => !current)}>Delete workspace</button>}
        </div>
        {deleting && workspace && <div className="confirmation" role="alert">
          <strong>Delete “{workspace.name}”?</strong><p>This permanently removes its files and knowledge.</p>
          <button onClick={() => setDeleting(false)}>Keep workspace</button>
          <button className="danger" disabled={action.busy} onClick={deleteWorkspace}>Delete permanently</button>
        </div>}
        {!workspace && view !== 'studio'
          ? <Empty title="A fresh start">Create or select a workspace to add your first sources.</Empty>
          : <WorkspaceContent key={selected || 'no-workspace'} api={api} workspace={workspace} view={view} userId={session.user.id}/>}
      </div>
      <footer>AI Workspace <span>One place. Many possibilities.</span></footer>
    </main>
  </div>;
}
