import React from 'react';
import { ActionIcon, Avatar, Menu, Text, UnstyledButton } from '@mantine/core';
import { IconActivity, IconBooks, IconChevronDown, IconLogout, IconMessageCircle, IconPalette, IconPlus } from '@tabler/icons-react';
import { Brand } from './design/shared.jsx';
import s from './design/workspace.module.css';

const navigation = [
  ['library', 'Library', IconBooks],
  ['ask', 'Ask workspace', IconMessageCircle],
  ['studio', 'Creative studio', IconPalette],
  ['activity', 'Activity', IconActivity],
];

export default function WorkspaceSidebar({ session, workspaces, selected, view, onSelectWorkspace, onSelectView, onCreateWorkspace, onLogout }) {
  const displayName = session.user.displayName || session.user.email;
  const workspace = workspaces.find(item => item.id === selected);
  return <div className={s.sidebarInner}>
    <div className={s.sidebarBrand}><Brand light/></div>
    <div className={s.workspaceSelect}>
      <Text size="xs" c="#b0c4c4" mb={8}>WORKSPACE</Text>
      <Menu width={220} position="bottom-start">
        <Menu.Target><UnstyledButton className={s.workspaceButton} aria-label="Select workspace"><Avatar size={30} radius="md" color="workspace">{workspace?.name?.[0]?.toUpperCase() || '?'}</Avatar><Text fw={600} truncate>{workspace?.name || 'Select a workspace'}</Text><IconChevronDown size={16}/></UnstyledButton></Menu.Target>
        <Menu.Dropdown>
          {workspaces.map(item => <Menu.Item key={item.id} onClick={() => onSelectWorkspace(item.id)}>{item.name}</Menu.Item>)}
          <Menu.Divider/><Menu.Item leftSection={<IconPlus size={16}/>} onClick={onCreateWorkspace}>New workspace</Menu.Item>
        </Menu.Dropdown>
      </Menu>
    </div>
    <Text className={s.navLabel}>YOUR WORKSPACE</Text>
    <nav aria-label="Main navigation" className={s.navigation}>
      {navigation.map(([id, label, Icon]) => <UnstyledButton key={id} className={s.navItem} data-active={view === id} aria-current={view === id ? 'page' : undefined} onClick={() => onSelectView(id)}><Icon size={21} stroke={1.6}/><span>{label}</span></UnstyledButton>)}
    </nav>
    <div className={s.sidebarTip}><div className={s.tipDots}><i/><i/><i/></div><Text fw={600} c="#f2f8f6" mb={6}>Your sources. A clearer picture.</Text><Text size="sm" c="#b0c4c4">Bring documents, images, and conversations into one place.</Text><UnstyledButton className={s.tipLink} onClick={() => onSelectView('library')}>Go to library <IconPlus size={15}/></UnstyledButton></div>
    <div className={s.account}><Avatar color="workspace" radius="xl">{displayName[0].toUpperCase()}</Avatar><div><Text size="sm" fw={600} c="white" truncate>{displayName}</Text><Text size="xs" c="#b0c4c4" truncate>{session.user.email}</Text></div><ActionIcon variant="subtle" color="gray" aria-label="Sign out" onClick={onLogout}><IconLogout size={19}/></ActionIcon></div>
  </div>;
}
