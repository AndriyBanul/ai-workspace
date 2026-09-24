import React, { useEffect, useState } from 'react';
import { Badge, Button, Group, Paper, Text, Textarea, Title } from '@mantine/core';
import { IconDownload, IconSparkles } from '@tabler/icons-react';
import { Notice, useAction } from '../components.jsx';
import s from '../design/workspace.module.css';

export default function GenerateTool({ api, config }) {
  const [prompt, setPrompt] = useState('');
  const [blob, setBlob] = useState(null);
  const [url, setUrl] = useState(null);
  const action = useAction();

  useEffect(() => {
    if (!blob) return;
    const value = URL.createObjectURL(blob);
    setUrl(value);
    return () => URL.revokeObjectURL(value);
  }, [blob]);

  async function generate(event) {
    event.preventDefault();
    await action.run(async () => setBlob(await api(config.endpoint, {
      method: 'POST',
      json: { [config.field]: prompt.trim() },
      binary: true,
    })));
  }

  return <div className={s.studioGrid}>
    <Paper p="xl"><Badge variant="light" color="workspace">CREATE</Badge><Title order={2} mt="lg">{config.title}</Title><Text c="dimmed" mt="sm" mb="xl">{config.description}</Text>
      <form onSubmit={generate}><Textarea label={config.field === 'text' ? 'Your text' : 'Your prompt'} minRows={7} autosize maxRows={10} value={prompt} onChange={event => setPrompt(event.target.value)} placeholder={config.placeholder} required disabled={action.busy}/><Button type="submit" fullWidth mt="lg" leftSection={<IconSparkles size={17}/>} loading={action.busy} disabled={!prompt.trim()}>Create {config.mime}</Button><Notice error={action.error}/></form>
      <Text size="xs" c="dimmed" mt="md" lh={1.7}>{config.dependency} Generated media is available to download. Upload it to Library to make it searchable.</Text>
    </Paper>
    <Paper className={s.studioPreview}><div className={s.panelHeading}><Title order={3}>Your creation</Title><Badge color="gray" variant="light">{url ? 'Ready' : action.busy ? 'Working' : 'Waiting'}</Badge></div>
      {url ? <><div className={s.generatedPreview}>{config.mime === 'image' ? <img src={url} alt="Generated from your prompt"/> : config.mime === 'video' ? <video src={url} controls/> : <audio src={url} controls/>}</div><Group justify="flex-end" p="lg"><Button component="a" href={url} download={config.filename} leftSection={<IconDownload size={16}/>}>Download {config.mime}</Button></Group></> : <div className={s.previewPlaceholder}><span className={s.previewOrb}><IconSparkles size={39} stroke={1.3}/></span><Title order={3} mt="xl">{action.busy ? 'Working on your idea…' : 'Something new starts here.'}</Title><Text c="dimmed" mt="sm" ta="center" maw={330}>{action.busy ? 'Your request is processing. Keep this view open until it finishes.' : 'Your generated media will appear here.'}</Text></div>}
    </Paper>
  </div>;
}
