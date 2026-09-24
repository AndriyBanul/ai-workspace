import React from 'react';
import { ActionIcon, Menu } from '@mantine/core';
import { IconDots, IconRefresh, IconTrash } from '@tabler/icons-react';
import { sourceName } from './sourcePresentation.js';

export default function SourceMenu({ file, busy, onOpen, onReprocess, onDelete }) {
  return <Menu position="bottom-end"><Menu.Target><ActionIcon variant="subtle" color="ink" aria-label={'Actions for ' + sourceName(file)}><IconDots size={18}/></ActionIcon></Menu.Target><Menu.Dropdown><Menu.Item onClick={() => onOpen(file)}>View details</Menu.Item><Menu.Item leftSection={<IconRefresh size={15}/>} disabled={busy || file.status === 'PROCESSING'} onClick={() => onReprocess(file)}>Reprocess</Menu.Item><Menu.Item color="red" leftSection={<IconTrash size={15}/>} disabled={busy || file.status === 'PROCESSING'} onClick={() => onDelete(file)}>Delete</Menu.Item></Menu.Dropdown></Menu>;
}
