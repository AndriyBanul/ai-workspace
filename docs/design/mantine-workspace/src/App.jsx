import React, { useEffect, useState } from 'react';
import { AppShell, ActionIcon, Avatar, Badge, Burger, Button, Divider, Drawer, Group, Menu, Modal, Notification, Select, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { IconBooks, IconMessageCircle, IconPalette, IconActivity, IconPlus, IconChevronDown, IconArrowUpRight, IconLogout, IconDots, IconCheck, IconTrash } from '@tabler/icons-react';
import Library, { AddSource } from './Library.jsx';
import Ask from './Ask.jsx';
import Studio from './Studio.jsx';
import Activity from './Activity.jsx';
import Login from './Login.jsx';
import { Brand } from './shared.jsx';
import s from './workspace.module.css';

const navigation = [ ['library', 'Library', IconBooks], ['ask', 'Ask workspace', IconMessageCircle], ['studio', 'Creative studio', IconPalette], ['activity', 'Activity', IconActivity] ];
const getRoute = () => window.location.hash.slice(1) || 'library';

export default function App() {
  const [route, setRoute] = useState(getRoute);
  const [mobile, setMobile] = useState(false);
  const [add, setAdd] = useState(false);
  const [workspaceDialog, setWorkspaceDialog] = useState(false);
  const [deleteDialog, setDeleteDialog] = useState(false);
  const [workspace, setWorkspace] = useState('Atlas launch');
  const [workspaceName, setWorkspaceName] = useState('');
  const [notice, setNotice] = useState('');
  const page = route.split('/')[0];
  useEffect(() => { const update = () => { setRoute(getRoute()); setMobile(false); }; window.addEventListener('hashchange', update); return () => window.removeEventListener('hashchange', update); }, []);
  function navigate(next) { window.location.hash = next; }
  function notify(message) { setNotice(message); }
  if (page === 'login') return <Login onEnter={() => navigate('library')}/>;
  const sidebar = <div className={s.sidebarInner}>
    <div className={s.sidebarBrand}><Brand light/></div>
    <div className={s.workspaceSelect}>
      <Text size="xs" c="#b0c4c4" mb={8}>WORKSPACE</Text>
      <Menu width={220} position="bottom-start"><Menu.Target><UnstyledButton className={s.workspaceButton}><Avatar size={30} radius="md" color="workspace">A</Avatar><Text fw={600} truncate>{workspace}</Text><IconChevronDown size={16}/></UnstyledButton></Menu.Target><Menu.Dropdown>
        {['Atlas launch', 'Personal research'].map(name => <Menu.Item key={name} onClick={() => setWorkspace(name)}>{name}</Menu.Item>)}
        <Menu.Divider/><Menu.Item leftSection={<IconPlus size={16}/>} onClick={() => setWorkspaceDialog(true)}>New workspace</Menu.Item>
      </Menu.Dropdown></Menu>
    </div>
    <Text className={s.navLabel}>YOUR WORKSPACE</Text>
    <nav aria-label="Main navigation" className={s.navigation}>{navigation.map(([id, label, Icon]) => <UnstyledButton component="a" href={`#${id}`} key={id} className={s.navItem} data-active={page === id || (page === 'source' && id === 'library')} aria-current={page === id ? 'page' : undefined}><Icon size={21} stroke={1.6}/><span>{label}</span>{id === 'activity' && <span className={s.navCount}>1</span>}</UnstyledButton>)}</nav>
    <div className={s.sidebarTip}><div className={s.tipDots}><i/><i/><i/></div><Text fw={600} c="#f2f8f6" mb={6}>Your sources. A clearer picture.</Text><Text size="sm" c="#b0c4c4">Bring documents, images, and conversations into one place.</Text><UnstyledButton className={s.tipLink} onClick={() => { setAdd(true); setMobile(false); }}>Add a source <IconPlus size={15}/></UnstyledButton></div>
    <div className={s.account}><Avatar color="workspace" radius="xl">AC</Avatar><div><Text size="sm" fw={600} c="white">Alex Chen</Text><Text size="xs" c="#b0c4c4">Personal workspace</Text></div><ActionIcon variant="subtle" color="gray" aria-label="Sign out" onClick={() => navigate('login')}><IconLogout size={19}/></ActionIcon></div>
  </div>;
  return <>
    <AppShell layout="alt" header={{ height: 72 }} navbar={{ width: 244, breakpoint: 'md', collapsed: { mobile: true } }} padding={0}>
      <AppShell.Navbar className={s.sidebar}>{sidebar}</AppShell.Navbar>
      <AppShell.Header className={s.header}><Group gap="sm" wrap="nowrap"><Burger opened={mobile} onClick={() => setMobile(!mobile)} hiddenFrom="md" size="sm" aria-label="Open navigation"/><Text c="dimmed" size="sm" className={s.breadcrumb}>Workspace <span>/</span></Text><Text fw={600} size="sm" truncate>{workspace}</Text></Group><Group gap="md" wrap="nowrap"><Badge variant="outline" color="gray" className={s.previewBadge}>Design preview</Badge><Menu position="bottom-end"><Menu.Target><ActionIcon variant="subtle" color="ink" aria-label="Workspace options"><IconDots size={21}/></ActionIcon></Menu.Target><Menu.Dropdown><Menu.Item onClick={() => setWorkspaceDialog(true)}>Create workspace</Menu.Item><Menu.Item color="red" leftSection={<IconTrash size={16}/>} onClick={() => setDeleteDialog(true)}>Delete workspace</Menu.Item><Menu.Item onClick={() => notify('Connect this link to the application’s /swagger-ui.html page.')}>API reference</Menu.Item></Menu.Dropdown></Menu></Group></AppShell.Header>
      <AppShell.Main className={s.main}><main id="main-content" className={s.page}>
        {(page === 'library' || page === 'source') && <Library route={route} addSource={() => setAdd(true)} navigate={navigate} notify={notify}/>}
        {page === 'ask' && <Ask notify={notify}/>}
        {page === 'studio' && <Studio notify={notify}/>}
        {page === 'activity' && <Activity notify={notify}/>}
      </main><footer className={s.footer}><span>AI Workspace</span><span>Sample content · Interactive design reference <IconArrowUpRight size={13}/></span></footer></AppShell.Main>
    </AppShell>
    <Drawer opened={mobile} onClose={() => setMobile(false)} title="Navigation" size={288} classNames={{ body: s.mobileDrawer }}>{sidebar}</Drawer>
    <AddSource opened={add} onClose={() => setAdd(false)} notify={notify}/>
    <Modal opened={workspaceDialog} onClose={() => setWorkspaceDialog(false)} title="Create workspace"><Stack><Text size="sm" c="dimmed">Keep related sources and questions together.</Text><TextInput label="Workspace name" value={workspaceName} onChange={e => setWorkspaceName(e.target.value)} placeholder="e.g. Customer research"/><Button disabled={!workspaceName.trim()} onClick={() => { setWorkspace(workspaceName.trim()); setWorkspaceDialog(false); notify('Demo workspace selected. Production creation uses POST /workspaces.'); }}>Create workspace</Button></Stack></Modal>
    <Modal opened={deleteDialog} onClose={() => setDeleteDialog(false)} title={`Delete ${workspace}?`}><Text size="sm" mb="lg">This removes the workspace, its sources, and indexed knowledge. This preview does not delete application data.</Text><Group justify="flex-end"><Button variant="default" onClick={() => setDeleteDialog(false)}>Keep workspace</Button><Button color="red" onClick={() => { setDeleteDialog(false); notify('Deletion confirmation demonstrated. No application data was changed.'); }}>Delete workspace</Button></Group></Modal>
    {notice && <Notification className={s.notification} icon={<IconCheck size={18}/>} title="Design preview" onClose={() => setNotice('')}>{notice}</Notification>}
  </>;
}
