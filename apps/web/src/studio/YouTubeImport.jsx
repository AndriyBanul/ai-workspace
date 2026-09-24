import React, { useState } from 'react';
import { Button, Divider, Text, TextInput } from '@mantine/core';
import { IconBrandYoutube } from '@tabler/icons-react';
import { Notice, useAction } from '../components.jsx';

export default function YouTubeImport({ api, workspace, onResult }) {
  const [url, setUrl] = useState('');
  const action = useAction();

  async function submit(event) {
    event.preventDefault();
    await action.run(async () => {
      const result = await api('/videos/youtube', { method: 'POST', json: { workspaceId: workspace.id, url: url.trim() } });
      onResult({ ...result, filename: 'YouTube video ' + result.videoId });
      setUrl('');
    });
  }

  return <><Divider my="xl" label="or import a public video"/><form onSubmit={submit}><TextInput label="YouTube URL" type="url" placeholder="https://www.youtube.com/watch?v=…" value={url} onChange={event => setUrl(event.target.value)} required disabled={!workspace || action.busy}/><Text size="xs" c="dimmed" mt="sm">The video stays on YouTube. The visual summary and transcript become workspace knowledge.</Text><Button mt="md" type="submit" variant="default" leftSection={<IconBrandYoutube size={17}/>} loading={action.busy} disabled={!workspace || !url.trim()}>Import video</Button><Notice error={action.error}/></form></>;
}
