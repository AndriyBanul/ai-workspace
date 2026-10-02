import React, { useState } from 'react';
import { Alert, Button, FileInput, Group, Modal, Select, Stack, Tabs, Text, TextInput, Title } from '@mantine/core';
import { IconBrandYoutube, IconFileText, IconInfoCircle, IconLink, IconUpload } from '@tabler/icons-react';
import { formats, validateFiles } from '../api.js';
import { Notice, useAction } from '../components.jsx';
import s from '../design/workspace.module.css';

export default function AddSource({ opened, onClose, api, workspace, addJob, onProcessed }) {
  const [tab, setTab] = useState('file');
  const [mode, setMode] = useState('async');
  const [kind, setKind] = useState('document');
  const [selectedFiles, setSelectedFiles] = useState({});
  const [url, setUrl] = useState('');
  const action = useAction();

  async function submit(event) {
    event.preventDefault();
    await action.run(async () => {
      let response;
      if (tab === 'file') {
        const entries = validateFiles(mode === 'async' ? selectedFiles : { [kind]: selectedFiles[kind] });
        const data = new FormData();
        data.append('workspaceId', workspace.id);
        for (const [type, file] of entries) data.append(mode === 'async' ? type : 'file', file);
        response = await api(mode === 'async' ? '/orchestrator/ingestions' : formats[kind].endpoint, { method: 'POST', body: data });
        if (mode === 'async') addJob(response);
      } else {
        const endpoint = tab === 'web' ? '/documents/web-page' : '/videos/youtube';
        response = await api(endpoint, { method: 'POST', json: { workspaceId: workspace.id, url: url.trim() } });
      }
      await onProcessed(response);
      setSelectedFiles({});
      setUrl('');
      onClose();
    });
  }

  return <Modal opened={opened} onClose={onClose} size={640} title={<Text fw={650} size="lg">Add a source</Text>}><Text c="dimmed" mb="lg">Bring something useful into {workspace.name}.</Text><Tabs value={tab} onChange={value => setTab(value || 'file')}><Tabs.List grow><Tabs.Tab value="file" leftSection={<IconUpload size={17}/>}>Upload file</Tabs.Tab><Tabs.Tab value="web" leftSection={<IconLink size={17}/>}>Web page</Tabs.Tab><Tabs.Tab value="youtube" leftSection={<IconBrandYoutube size={17}/>}>YouTube</Tabs.Tab></Tabs.List></Tabs>
    <form onSubmit={submit}><Stack mt="lg" gap="lg">{tab === 'file' ? <><div className={s.uploadWell}><span className={s.uploadCircle}><IconUpload size={29} stroke={1.5}/></span><Title order={3}>A little context goes a long way.</Title><Text size="sm" c="dimmed" maw={360} mx="auto" mt={8}>Choose documents, images, audio recordings, or videos from your device.</Text></div><Select label="Processing" data={[{value:'async',label:'Background job · multiple media types'},{value:'direct',label:'Process one file now'}]} value={mode} onChange={value => setMode(value || 'async')} allowDeselect={false}/>
      {mode === 'direct' && <Select label="Source type" data={Object.entries(formats).map(([value, item]) => ({value,label:item.title}))} value={kind} onChange={value => setKind(value || 'document')} allowDeselect={false}/>}
      {(mode === 'async' ? Object.entries(formats) : [[kind, formats[kind]]]).map(([type, format]) => <FileInput key={type} label={format.title + ' file'} aria-label={format.title + ' file'} placeholder={format.hint} accept={format.accept} value={selectedFiles[type] || null} onChange={file => setSelectedFiles(current => ({...current,[type]:file}))} clearable leftSection={<IconFileText size={17}/>}/>)}
      <Text size="xs" c="dimmed">Up to one file per type in a background job. Total submission must be less than 25 MB. Scanned PDF OCR depends on server configuration.</Text></> : <><TextInput label={tab === 'youtube' ? 'YouTube URL' : 'Web page URL'} type="url" pattern={tab === 'youtube' ? 'https://.*' : 'https?://.*'} placeholder={tab === 'youtube' ? 'https://www.youtube.com/watch?v=…' : 'https://example.com/article'} value={url} onChange={event => setUrl(event.target.value)} required/><Alert icon={<IconInfoCircle size={18}/>} color="workspace">{tab === 'youtube' ? 'The public video stays on YouTube; its visuals and spoken transcript become searchable.' : 'Readable content from a public HTML or text page is added to your workspace.'}</Alert></>}
      <Notice error={action.error}/><Group justify="space-between" className={s.modalFooter}><Text size="xs" c="dimmed">Destination: {workspace.name}</Text><Group><Button variant="default" onClick={onClose}>Cancel</Button><Button type="submit" loading={action.busy}>{tab === 'file' ? 'Upload & process' : tab === 'youtube' ? 'Import video' : 'Import'}</Button></Group></Group></Stack></form>
  </Modal>;
}
