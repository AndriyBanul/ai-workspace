import React from 'react';
import { Icon, Submit } from './components.jsx';

export default function WorkspaceSidebar({
  session,
  workspaces,
  selected,
  view,
  views,
  creating,
  workspaceName,
  busy,
  onSelectWorkspace,
  onSelectView,
  onToggleCreate,
  onWorkspaceNameChange,
  onCreateWorkspace,
  onLogout,
}) {
  const displayName = session.user.displayName || session.user.email;

  return <aside className="sidebar">
    <a className="brand" href="#" onClick={() => onSelectView('library')}>
      <span className="brand-symbol"><Icon name="studio"/></span>AI Workspace
    </a>
    <div className="workspace-picker">
      <label>WORKSPACE
        <select aria-label="Select workspace" value={selected} onChange={event => onSelectWorkspace(event.target.value)}>
          <option value="">Select a workspace</option>
          {workspaces.map(workspace => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
        </select>
      </label>
      <button className="new-workspace" onClick={onToggleCreate}><Icon name="plus" size={16}/>New workspace</button>
      {creating && <form onSubmit={onCreateWorkspace}>
        <label className="sr-only" htmlFor="workspace-name">Workspace name</label>
        <input id="workspace-name" autoFocus required value={workspaceName} onChange={event => onWorkspaceNameChange(event.target.value)} placeholder="Name your workspace"/>
        <Submit busy={busy}>Create</Submit>
      </form>}
    </div>
    <nav aria-label="Main navigation">
      {Object.entries(views).map(([key, [title]]) => <button key={key} className={view === key ? 'active' : ''} onClick={() => onSelectView(key)}>
        <Icon name={key}/>{key === 'ask' ? 'Ask workspace' : title}{view === key && <span className="nav-dot"/>}
      </button>)}
    </nav>
    <div className="sidebar-note"><Icon name="studio"/><strong>A little more possibility.</strong><p>Add a source. Ask a question.<br/>See where it takes you.</p></div>
    <div className="account">
      <span className="avatar">{displayName[0].toUpperCase()}</span>
      <div><strong>{session.user.displayName || 'My account'}</strong><small title={session.user.email}>{session.user.email}</small></div>
      <button className="text-button" onClick={onLogout}>Sign out</button>
    </div>
  </aside>;
}
