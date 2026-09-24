import React from 'react';
import { Accordion, Alert, Badge, Button, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { IconArrowLeft, IconArrowUpRight, IconInfoCircle, IconRefresh } from '@tabler/icons-react';
import { safeUrl } from '../api.js';
import { Notice, date, size } from '../components.jsx';
import { SourceIcon, Status } from '../design/shared.jsx';
import { typeLabel, sourceName } from './sourcePresentation.js';
import s from '../design/workspace.module.css';

export default function SourceDetails({ detail, recovery, sourceAction, onNavigate, reprocessSource, onDeleteRequested, onClose }) {
    const fields = [
      ['Type', typeLabel(detail.sourceType)],
      ['Status', detail.status],
      ['Created', date(detail.createdAt)],
      ['Updated', date(detail.updatedAt)],
      ['Content type', detail.contentType],
      ['Size', detail.sourceUrl ? 'Remote' : size(detail.sizeBytes || 0)],
    ];
    const diagnostics = [
      ['Source ID', detail.id],
      ['SHA-256', detail.checksumSha256],
      ['Recovery', recovery?.status],
      ['Recovery operation', recovery?.operationType],
      ['Attempts', recovery?.attemptCount],
      ['Next recovery check', recovery?.nextAttemptAt && date(recovery.nextAttemptAt)],
      ['Last recovery error', recovery?.lastErrorMessage],
    ].filter(([, value]) => value !== null && value !== undefined && value !== '');
    return <>
      <Button variant="subtle" leftSection={<IconArrowLeft size={16}/>} px={0} mb="lg" onClick={onClose}>Back to library</Button>
      <div className={s.detailHeading}><Group wrap="nowrap"><SourceIcon type={typeLabel(detail.sourceType)} large/><div><Text className={s.eyebrow}>SOURCE DETAILS</Text><Title order={1}>{sourceName(detail)}</Title><Text c="dimmed" mt={7}>Added {date(detail.createdAt)}</Text></div></Group><Status value={detail.status}/></div>
      <Notice error={sourceAction.error}/>
      {detail.status === 'FAILED' && <Alert color="orange" title="This source needs attention" mb="lg">Your source is saved. Check recovery details, then try reprocessing if needed.</Alert>}
      <div className={s.detailGrid}>
        <Paper className={s.readingPanel}><div className={s.panelHeading}><Title order={3}>Source overview</Title><Badge variant="light" color="gray">{typeLabel(detail.sourceType)}</Badge></div><div className={s.transcriptIntro}><Text c="dimmed" lh={1.8}>Metadata for this source is available here. Full extracted content and transcripts are not yet available through the source-details API.</Text>{safeUrl(detail.sourceUrl) && <Button component="a" href={safeUrl(detail.sourceUrl)} target="_blank" rel="noreferrer" variant="light" mt="lg" rightSection={<IconArrowUpRight size={16}/>}>Open original URL</Button>}</div><div className={s.panelFoot}><IconInfoCircle size={16}/><span>Ask workspace to explore indexed content with supporting passages.</span></div></Paper>
        <Stack><Paper p="lg"><Text className={s.eyebrow}>WORK WITH THIS SOURCE</Text><Title order={3} mt="sm">Make the next connection.</Title><Text size="sm" c="dimmed" mt="sm" mb="lg">Questions search all ready sources in this workspace.</Text><Button fullWidth onClick={() => onNavigate('ask')}>Ask workspace</Button><Button fullWidth mt="sm" variant="default" disabled={sourceAction.busy || detail.status === 'PROCESSING'} onClick={() => reprocessSource(detail)} leftSection={<IconRefresh size={16}/>}>Reprocess source</Button><Button fullWidth mt="sm" variant="subtle" color="red" disabled={sourceAction.busy || detail.status === 'PROCESSING'} onClick={() => onDeleteRequested(detail)}>Delete source</Button></Paper>
          <Paper p="lg"><Text fw={600} mb="md">Source information</Text><div className={s.metadata}>{fields.map(([label, value]) => <React.Fragment key={label}><span>{label}</span><strong>{value || '—'}</strong></React.Fragment>)}</div><Accordion mt="lg" variant="contained"><Accordion.Item value="technical"><Accordion.Control>Technical and recovery details</Accordion.Control><Accordion.Panel><div className={s.metadata}>{diagnostics.map(([label, value]) => <React.Fragment key={label}><span>{label}</span><strong style={{overflowWrap:'anywhere'}}>{value}</strong></React.Fragment>)}</div>{!recovery && <Text size="xs" c="dimmed" mt="sm">No recovery record is available.</Text>}</Accordion.Panel></Accordion.Item></Accordion></Paper></Stack>
      </div>
    </>;
}
