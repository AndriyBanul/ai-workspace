import React, { useState } from 'react';
import { Badge, Button, FileInput, Paper, Select, Text, Title } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';
import { formats, validateFiles } from '../api.js';
import { Notice, useAction } from '../components.jsx';
import AnalysisResult from './AnalysisResult.jsx';
import YouTubeImport from './YouTubeImport.jsx';
import s from '../design/workspace.module.css';

export default function AnalyzeMedia({ api, workspace }) {
  const [kind, setKind] = useState('audio');
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const action = useAction();
  const config = formats[kind];

  async function submit(event) {
    event.preventDefault();
    await action.run(async () => {
      validateFiles({ [kind]: file });
      const data = new FormData();
      data.append('workspaceId', workspace.id);
      data.append('file', file);
      setResult(await api(config.endpoint, { method: 'POST', body: data }));
    });
  }

  function changeKind(nextKind) {
    setKind(nextKind || 'audio');
    setFile(null);
    setResult(null);
  }

  return <div className={s.studioGrid}>
    <Paper p="xl"><Badge variant="light" color="workspace">UNDERSTAND</Badge><Title order={2} mt="lg">Look closer. Hear more.</Title><Text c="dimmed" mt="sm" mb="xl">Analyze visuals or conversations and add what you learn to your workspace.</Text>
      {workspace ? <form onSubmit={submit}><Select label="Media type" value={kind} onChange={changeKind} data={[{value:'audio',label:'Audio transcription'},{value:'image',label:'Image description'},{value:'video',label:'Video analysis'}]} allowDeselect={false}/><FileInput mt="md" key={kind} label={kind + ' file'} aria-label={kind + ' file'} placeholder={config.hint} accept={config.accept} value={file} onChange={setFile} required/><Button type="submit" fullWidth mt="lg" leftSection={<IconSearch size={17}/>} loading={action.busy} disabled={!file}>Analyze {kind}</Button><Notice error={action.error}/></form> : <Text>Select a workspace to analyze media.</Text>}
      <Text size="xs" c="dimmed" mt="md">Visual summaries and timed conversations become searchable when processing completes.</Text>
      <YouTubeImport api={api} workspace={workspace} onResult={setResult}/>
    </Paper>
    <Paper className={s.studioPreview}><div className={s.panelHeading}><Title order={3}>Analysis</Title><Badge color="gray" variant="light">{result ? 'Ready' : action.busy ? 'Working' : 'Waiting'}</Badge></div>{result ? <AnalysisResult result={result}/> : <div className={s.previewPlaceholder}><span className={s.previewOrb}><IconSearch size={39} stroke={1.3}/></span><Title order={3} mt="xl">{action.busy ? 'Understanding your source…' : 'A clearer picture starts here.'}</Title><Text c="dimmed" mt="sm" ta="center" maw={330}>Choose a media file or public YouTube URL. The analysis will appear here.</Text></div>}</Paper>
  </div>;
}
