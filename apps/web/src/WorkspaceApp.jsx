import React, { useEffect, useMemo, useState } from 'react';
import { ActionIcon, AppShell, Burger, Button, Drawer, Group, Menu, Modal, Stack, Text, TextInput } from '@mantine/core';
import { IconDots, IconPlus, IconTrash } from '@tabler/icons-react';
import { createApi } from './api.js';
import { Empty, Notice, useAction } from './components.jsx';
import WorkspaceContent from './WorkspaceContent.jsx';
import WorkspaceSidebar from './WorkspaceSidebar.jsx';
import s from './design/workspace.module.css';
import { readWorkspaceRoute, useWorkspaceRoute } from './useWorkspaceRoute.js';

export const workspaceViews = {
  library: ['Library', 'Your sources, connected.'],
  ask: ['Ask workspace', 'Find answers in what you know.'],
  studio: ['Creative studio', 'Turn your next idea into something tangible.'],
  activity: ['Activity', 'Follow your files from upload to understanding.'],
};

export default function WorkspaceApp({ session, logout, api: providedApi }) {
  const authenticatedApi = useMemo(() => createApi(session.authorization, { onUnauthorized: logout }),
    [session.authorization, logout]);
  const api = providedApi || authenticatedApi;
  const [workspaces, setWorkspaces] = useState([]);
  const { workspaceId: selected, view, navigate: setRoute } = useWorkspaceRoute();
  const [mobile, setMobile] = useState(false);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [deleting, setDeleting] = useState(false);
  const action = useAction();
  const workspace = workspaces.find(candidate => candidate.id === selected);

  const refresh = () => action.run(async () => {
    const list = await api('/workspaces');
    setWorkspaces(list);
    setRoute(list.some(candidate => candidate.id === selected) ? selected : list[0]?.id || '', view, true);
  });

  useEffect(() => {
    const controller = new AbortController();
    api('/workspaces', { signal: controller.signal })
      .then(list => {
        setWorkspaces(list);
        const linked = readWorkspaceRoute();
        setRoute(list.some(candidate => candidate.id === linked.workspaceId)
          ? linked.workspaceId : list[0]?.id || '', linked.view, true);
      })
      .catch(error => {
        if (error.name !== 'AbortError') action.run(() => { throw error; });
      });
    return () => controller.abort();
  }, [api, setRoute]);

  async function createWorkspace(event) {
    event.preventDefault();
    await action.run(async () => {
      const created = await api('/workspaces', { method: 'POST', json: { name: name.trim() } });
      setWorkspaces(current => [created, ...current]);
      setRoute(created.id, 'library');
      setName('');
      setCreating(false);
    });
  }

  async function deleteWorkspace() {
    if (!workspace) return;
    await action.run(async () => {
      await api('/workspaces/' + workspace.id, { method: 'DELETE' });
      setWorkspaces(current => current.filter(candidate => candidate.id !== workspace.id));
      setRoute('', 'library');
      setDeleting(false);
    });
  }

  function navigate(next) {
    setRoute(selected, next);
    setMobile(false);
  }

  const sidebar = <WorkspaceSidebar session={session} workspaces={workspaces} selected={selected}
    view={view} onSelectWorkspace={id => setRoute(id, view)} onSelectView={navigate}
    onCreateWorkspace={() => setCreating(true)} onLogout={logout}/>;

  return <>
    <AppShell layout="alt" header={{ height: 72 }} navbar={{ width: 244, breakpoint: 'md', collapsed: { mobile: true } }} padding={0}>
      <AppShell.Navbar className={s.sidebar}>{sidebar}</AppShell.Navbar>
      <AppShell.Header className={s.header}>
        <Group gap="sm" wrap="nowrap">
          <Burger opened={mobile} onClick={() => setMobile(current => !current)} hiddenFrom="md" size="sm" aria-label="Open navigation"/>
          <Text c="dimmed" size="sm" className={s.breadcrumb}>Workspace <span>/</span></Text>
          <Text fw={600} size="sm" truncate>{workspace?.name || 'Getting started'}</Text>
        </Group>
        <Menu position="bottom-end">
          <Menu.Target><ActionIcon variant="subtle" color="ink" aria-label="Workspace options"><IconDots size={21}/></ActionIcon></Menu.Target>
          <Menu.Dropdown>
            <Menu.Item leftSection={<IconPlus size={16}/>} onClick={() => setCreating(true)}>New workspace</Menu.Item>
            <Menu.Item onClick={refresh} disabled={action.busy}>Refresh workspaces</Menu.Item>
            <Menu.Item component="a" href="/swagger-ui.html" target="_blank" rel="noreferrer">API reference</Menu.Item>
            {workspace && <><Menu.Divider/><Menu.Item color="red" leftSection={<IconTrash size={16}/>} onClick={() => setDeleting(true)}>Delete workspace</Menu.Item></>}
          </Menu.Dropdown>
        </Menu>
      </AppShell.Header>
      <AppShell.Main className={s.main}>
        <main id="main-content" className={s.page}>
          <Notice error={action.error}/>
          {!workspace && view !== 'studio'
            ? <Empty title="A fresh start">Create or select a workspace to add your first sources.</Empty>
            : <WorkspaceContent key={selected || 'no-workspace'} api={api} workspace={workspace} view={view} onNavigate={navigate}/>}
        </main>
        <footer className={s.footer}><span>AI Workspace</span><span>Your sources. A clearer picture.</span></footer>
      </AppShell.Main>
    </AppShell>
    <Drawer opened={mobile} onClose={() => setMobile(false)} title="Navigation" size={288} classNames={{ body: s.mobileDrawer }}>{sidebar}</Drawer>
    <Modal opened={creating} onClose={() => setCreating(false)} title="Create workspace">
      <form onSubmit={createWorkspace}><Stack>
        <Text size="sm" c="dimmed">Keep related sources and questions together.</Text>
        <TextInput label="Workspace name" value={name} onChange={event => setName(event.target.value)} placeholder="e.g. Customer research" required autoFocus/>
        <Button type="submit" loading={action.busy} disabled={!name.trim()}>Create</Button>
      </Stack></form>
    </Modal>
    <Modal opened={deleting} onClose={() => setDeleting(false)} title={'Delete ' + (workspace?.name || 'workspace') + '?'}>
      <Text size="sm" mb="lg">This permanently removes the workspace, its sources, and indexed knowledge.</Text>
      <Group justify="flex-end"><Button variant="default" onClick={() => setDeleting(false)}>Keep workspace</Button><Button color="red" loading={action.busy} onClick={deleteWorkspace}>Delete permanently</Button></Group>
    </Modal>
  </>;
}
