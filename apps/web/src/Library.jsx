import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Group, Paper, Text, Title } from '@mantine/core';
import { IconArrowUpRight, IconCheck, IconPlus } from '@tabler/icons-react';
import { useAction } from './components.jsx';
import { PageTitle, SourceIcon } from './design/shared.jsx';
import s from './design/workspace.module.css';
import AddSource from './library/AddSource.jsx';
import DeleteSourceConfirmation from './library/DeleteSourceConfirmation.jsx';
import SourceDetails from './library/SourceDetails.jsx';
import SourceList from './library/SourceList.jsx';

export default function Library({ api, workspace, addJob, onNavigate }) {
  const [files, setFiles] = useState([]);
  const [showUpload, setShowUpload] = useState(false);
  const [result, setResult] = useState(null);
  const [detail, setDetail] = useState(null);
  const [recovery, setRecovery] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const load = useAction();
  const sourceAction = useAction();

  const refresh = () => load.run(async () => setFiles(await api('/workspaces/' + workspace.id + '/sources')));

  useEffect(() => {
    const controller = new AbortController();
    api('/workspaces/' + workspace.id + '/sources', { signal: controller.signal })
      .then(setFiles)
      .catch(error => {
        if (error.name !== 'AbortError') load.run(() => { throw error; });
      });
    return () => controller.abort();
  }, [api, workspace.id]);

  async function processed(response) {
    setResult(response);
    await refresh();
  }

  function openSource(file) {
    return sourceAction.run(async () => {
      const base = '/workspaces/' + workspace.id + '/sources/' + file.id;
      const sourceDetail = await api(base);
      setDetail(sourceDetail);
      try { setRecovery(await api(base + '/recovery')); }
      catch (error) { if (error.status !== 404) throw error; setRecovery(null); }
    });
  }

  function reprocessSource(file) {
    return sourceAction.run(async () => {
      const job = await api('/workspaces/' + workspace.id + '/sources/' + file.id + '/reprocess', { method: 'POST' });
      addJob(job);
      await refresh();
      if (detail?.id === file.id) setDetail(current => ({ ...current, status: 'PROCESSING' }));
    });
  }

  function deleteSource() {
    return sourceAction.run(async () => {
      await api('/workspaces/' + workspace.id + '/sources/' + confirm.id, { method: 'DELETE' });
      setConfirm(null);
      setDetail(null);
      setRecovery(null);
      await refresh();
    });
  }

  if (detail) return <>
    <SourceDetails detail={detail} recovery={recovery} sourceAction={sourceAction} onNavigate={onNavigate} reprocessSource={reprocessSource} onDeleteRequested={setConfirm} onClose={() => { setDetail(null); setRecovery(null); }}/>
    <DeleteSourceConfirmation source={confirm} busy={sourceAction.busy} onClose={() => setConfirm(null)} onConfirm={deleteSource}/>
  </>;

  return <>
    <PageTitle eyebrow="THE STARTING POINT" title="Your knowledge, together." description="A home for the sources behind your next good idea."><Button leftSection={<IconPlus size={18}/>} onClick={() => setShowUpload(true)}>Add sources</Button></PageTitle>
    <section className={s.libraryIntro} aria-label="Workspace summary"><div><Badge variant="light" color="workspace" mb={18}>{workspace.name.toUpperCase()} / RESEARCH SPACE</Badge><Title order={2}>From scattered files<br/>to a shared understanding.</Title><Text c="dimmed" mt={12} maw={380}>Ask across your documents, visuals, and conversations. Every answer starts with your sources.</Text><Button mt={22} variant="white" color="workspace" rightSection={<IconArrowUpRight size={17}/>} onClick={() => onNavigate('ask')}>Ask your workspace</Button></div><div className={s.knowledgeArt} aria-hidden="true"><div className={s.artOrbit}/><div className={s.artCard + ' ' + s.artBack}><SourceIcon type="Audio"/><span>Conversations</span><div className={s.waveform}>{Array.from({length: 18}, (_, i) => <i key={i} style={{height: 12 + (i * 17 % 30) + 'px'}}/>)}</div></div><div className={s.artCard + ' ' + s.artFront}><SourceIcon type="Document"/><span>Ideas, connected.</span><div className={s.artLines}><i/><i/><i/></div><Badge variant="light" color="workspace" leftSection={<IconCheck size={12}/>}>Ready to explore</Badge></div><span className={s.artSpark}>✦</span></div></section>
    <div className={s.stats}>{[
      [files.length, 'Total sources', 'Across six formats'],
      [files.filter(file => file.status === 'PROCESSED').length, 'Ready to ask', 'Indexed and searchable'],
      [files.filter(file => file.status === 'PROCESSING').length, 'Processing', 'Being understood'],
      [files.filter(file => file.status === 'FAILED').length, 'Needs attention', 'Review recovery'],
    ].map(([number, label, description]) => <Paper key={label} className={s.stat}><Text size="sm" c="dimmed">{label}</Text><Group align="baseline" gap={10} mt={7}><Text className={s.statNumber} fz={28} fw={600}>{String(number).padStart(2, '0')}</Text><Text size="xs" c="dimmed">{description}</Text></Group></Paper>)}</div>
    <SourceList files={files} busy={load.busy || sourceAction.busy} error={load.error || sourceAction.error} onRefresh={refresh} onOpen={openSource} onReprocess={reprocessSource} onDelete={setConfirm}/>
    <AddSource opened={showUpload} onClose={() => setShowUpload(false)} api={api} workspace={workspace} addJob={addJob} onProcessed={processed}/>
    {result && <Alert mt="lg" color="workspace" withCloseButton onClose={() => setResult(null)} title="Source submitted">{result.jobId ? 'Background job ' + result.jobId + ' is now available in Activity.' : 'The source has been processed and added to your workspace.'}</Alert>}
    <DeleteSourceConfirmation source={confirm} busy={sourceAction.busy} onClose={() => setConfirm(null)} onConfirm={deleteSource}/>
  </>;
}
