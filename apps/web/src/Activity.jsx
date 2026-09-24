import React, { useEffect, useState } from 'react';
import { Accordion, Badge, Button, Group, Paper, Text, TextInput, Timeline, Title } from '@mantine/core';
import { IconCheck, IconClock, IconInfoCircle, IconRefresh, IconSearch } from '@tabler/icons-react';
import { terminal } from './api.js';
import { Notice, date, useAction } from './components.jsx';
import { PageTitle, SourceIcon, Status } from './design/shared.jsx';
import s from './design/workspace.module.css';

export default function Activity({ api, workspace, jobs, setJobs, addJob }) {
  const [selected, setSelected] = useState(jobs[0]?.jobId || '');
  const [job, setJob] = useState(null);
  const [error, setError] = useState(null);
  const [listError, setListError] = useState(null);
  const [refresh, setRefresh] = useState(0);
  const [lookupId, setLookupId] = useState('');
  const action = useAction();

  useEffect(() => {
    if (!setJobs) return;
    const controller = new AbortController();
    api('/orchestrator/jobs?workspaceId=' + encodeURIComponent(workspace.id) + '&limit=50',
      { signal: controller.signal })
      .then(list => {
        setJobs(list);
        setSelected(current => current || list[0]?.jobId || '');
        setListError(null);
      })
      .catch(caught => { if (caught.name !== 'AbortError') setListError(caught); });
    return () => controller.abort();
  }, [api, workspace.id, setJobs]);

  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    let timer;
    setJob(null);
    setError(null);
    async function poll() {
      try {
        const value = await api('/orchestrator/jobs/' + encodeURIComponent(selected), { signal: controller.signal });
        if (value.workspaceId !== workspace.id) throw new Error('This job belongs to another workspace.');
        setJob(value);
        if (!terminal.has(value.status)) timer = setTimeout(poll, 2000);
      } catch (caught) {
        if (caught.name !== 'AbortError') setError(caught);
      }
    }
    poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [selected, api, workspace.id, refresh]);

  async function lookup(event) {
    event.preventDefault();
    const id = lookupId.trim();
    if (!id) return;
    await action.run(async () => {
      const value = await api('/orchestrator/jobs/' + encodeURIComponent(id));
      if (value.workspaceId !== workspace.id) throw new Error('This job belongs to another workspace.');
      addJob(value);
      setSelected(id);
      setRefresh(count => count + 1);
      setLookupId('');
    });
  }

  const active = job && !terminal.has(job.status);
  return <>
    <PageTitle eyebrow="NOTHING LOST IN THE PROCESS" title="Keep an eye on the work." description="Follow your sources from arrival to ready for questions."/>
    <div className={s.activitySummary}><div className={s.activityPulse}><IconClock size={23}/></div><div><Text fw={600}>{active ? 'A job is being processed' : 'Your recent activity'}</Text><Text size="sm" c="dimmed" mt={4}>Recent workspace jobs appear below, including those submitted from another browser.</Text></div></div>
    <div className={s.activityGrid}><Paper><div className={s.panelHeading}><Title order={3}>Recent submissions</Title><Badge color="gray" variant="light">This workspace</Badge></div>
      <Notice error={listError}/>
      {jobs.length ? jobs.map(item => <button className={s.jobRow} data-active={selected === item.jobId} key={item.jobId} onClick={() => setSelected(item.jobId)}><Group wrap="nowrap" align="flex-start"><SourceIcon type="Document"/><div><Text fw={600} size="sm">{item.submitted?.join(', ') || item.steps?.filter(step => step.status !== 'SKIPPED').map(step => step.type.toLowerCase()).join(', ') || 'Ingestion job'}</Text><Text c="dimmed" size="xs" mt={4}>{date(item.createdAt)}</Text><Text c="dimmed" size="xs" mt={8} className={s.breakAll}>{item.jobId}</Text></div></Group></button>) : <div className={s.empty}><IconClock size={28}/><Title order={3}>Nothing in the queue</Title><Text c="dimmed">Background uploads will appear here.</Text></div>}
      <form className={s.jobLookup} onSubmit={lookup}><Text size="sm" fw={600} mb="sm">Looking for another job?</Text><TextInput aria-label="Job ID" placeholder="Paste a job ID" value={lookupId} onChange={event => setLookupId(event.target.value)} rightSection={<IconSearch size={16}/>}/><Button type="submit" variant="default" fullWidth mt="sm" loading={action.busy} disabled={!lookupId.trim()}>Find job</Button><Notice error={action.error}/></form>
    </Paper><Paper p="xl"><Group justify="space-between" mb="xl"><Title order={3}>Job details</Title>{selected && <Button variant="subtle" leftSection={<IconRefresh size={16}/>} onClick={() => setRefresh(count => count + 1)}>Refresh status</Button>}</Group>
      <Notice error={error}/>
      {job ? <><Group mb="sm"><Status value={job.status}/><Text size="xs" c="dimmed" className={s.breakAll}>{job.jobId}</Text></Group><Text size="sm" c="dimmed">Created {date(job.createdAt)} · Completed {date(job.completedAt)}</Text><Timeline active={job.steps?.findIndex(step => !terminal.has(step.status)) ?? -1} bulletSize={30} lineWidth={2} color="workspace" mt="xl">
        {job.steps?.map(step => <Timeline.Item key={step.type} bullet={terminal.has(step.status) ? <IconCheck size={15}/> : <IconClock size={15}/>} title={step.type}><Group mt="xs"><Status value={step.status}/></Group>{step.errorMessage && <Text size="sm" c="red" mt="sm">{step.errorMessage}</Text>}{step.errorCode && <Text size="xs" mt="xs">{step.errorCode}</Text>}<Text size="xs" c="dimmed" mt="sm">{date(step.startedAt)} → {date(step.completedAt)}</Text></Timeline.Item>)}
      </Timeline><div className={s.activityHint}><IconInfoCircle size={18}/><Text size="sm" c="dimmed">These are job and step states, not a percentage of processing completed.</Text></div><Accordion variant="contained" mt="xl"><Accordion.Item value="diagnostics"><Accordion.Control>Job details</Accordion.Control><Accordion.Panel><Text size="sm" className={s.breakAll}>Job ID: {job.jobId}<br/>Workspace ID: {job.workspaceId}<br/>Status: {job.status}</Text></Accordion.Panel></Accordion.Item></Accordion></> : <div className={s.empty}><IconInfoCircle size={28}/><Text c="dimmed">{selected && !error ? 'Loading job…' : 'Select a job to inspect processing steps.'}</Text></div>}
    </Paper></div>
  </>;
}
