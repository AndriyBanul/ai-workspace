import React, { useState } from 'react';
import { ActionIcon, Badge, Group, Paper, Select, Table, Text, TextInput, Title } from '@mantine/core';
import { IconInfoCircle, IconRefresh, IconSearch } from '@tabler/icons-react';
import { Notice, date, size } from '../components.jsx';
import { SourceIcon, Status } from '../design/shared.jsx';
import SourceMenu from './SourceMenu.jsx';
import { typeLabel, sourceName } from './sourcePresentation.js';
import s from '../design/workspace.module.css';

export default function SourceList({ files, busy, error, onRefresh, onOpen, onReprocess, onDelete }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('ALL');
  const shown = files.filter(file => (filter === 'ALL' || file.sourceType === filter) && sourceName(file).toLowerCase().includes(query.toLowerCase()));

  return <>
    <Paper className={s.sourcePanel}><div className={s.panelHeading}><Group><Title order={3}>Source library</Title><Badge color="gray" variant="light">{files.length}</Badge></Group><ActionIcon variant="subtle" color="ink" aria-label="Refresh sources" onClick={onRefresh} disabled={busy}><IconRefresh size={18}/></ActionIcon></div>
      <div className={s.sourceToolbar}><TextInput aria-label="Search sources" placeholder="Find a source by name…" leftSection={<IconSearch size={17}/>} value={query} onChange={event => setQuery(event.target.value)} className={s.sourceSearch}/><Select aria-label="Filter sources" data={[{value:'ALL',label:'All sources'}, ...['DOCUMENT','AUDIO','IMAGE','VIDEO','WEB_PAGE','YOUTUBE'].map(value => ({value,label:typeLabel(value)}))]} value={filter} onChange={value => setFilter(value || 'ALL')} w={166} allowDeselect={false}/><Text size="xs" c="dimmed" className={s.resultCount}>{shown.length} sources</Text></div>
      <Notice error={error}/>
      {shown.length ? <><Table.ScrollContainer minWidth={680}><Table verticalSpacing="md" horizontalSpacing="lg" className={s.sourceTable}><Table.Thead><Table.Tr><Table.Th>NAME</Table.Th><Table.Th>STATUS</Table.Th><Table.Th>ADDED</Table.Th><Table.Th><span className={s.srOnly}>Actions</span></Table.Th></Table.Tr></Table.Thead><Table.Tbody>{shown.map(file => <Table.Tr key={file.id}><Table.Td><button className={s.sourceName} onClick={() => onOpen(file)}><SourceIcon type={typeLabel(file.sourceType)}/><span><strong>{sourceName(file)}</strong><small>{typeLabel(file.sourceType)} · {file.sourceUrl ? 'Remote' : size(file.sizeBytes || 0)}</small></span></button></Table.Td><Table.Td><Status value={file.status}/></Table.Td><Table.Td><Text size="sm" c="dimmed">{date(file.createdAt)}</Text></Table.Td><Table.Td><SourceMenu file={file} busy={busy} onOpen={onOpen} onReprocess={onReprocess} onDelete={onDelete}/></Table.Td></Table.Tr>)}</Table.Tbody></Table></Table.ScrollContainer><div className={s.mobileSourceList}>{shown.map(file => <div key={file.id} className={s.mobileSource}><button className={s.sourceName} onClick={() => onOpen(file)}><SourceIcon type={typeLabel(file.sourceType)}/><span><strong>{sourceName(file)}</strong><small>{typeLabel(file.sourceType)} · {date(file.createdAt)}</small></span></button><Group justify="space-between" mt="md"><Status value={file.status}/><SourceMenu file={file} busy={busy} onOpen={onOpen} onReprocess={onReprocess} onDelete={onDelete}/></Group></div>)}</div></> : <div className={s.empty}><IconSearch size={32}/><Title order={3}>{files.length ? 'No matching sources' : 'Your library is ready for its first source'}</Title><Text c="dimmed">{files.length ? 'Try another name or type.' : 'Upload a file, import a web page, or add a YouTube video.'}</Text></div>}
      <div className={s.panelFoot}><IconInfoCircle size={15}/><span>Only ready sources are available for questions. Processing status updates appear in Activity.</span></div>
    </Paper>
  </>;
}
