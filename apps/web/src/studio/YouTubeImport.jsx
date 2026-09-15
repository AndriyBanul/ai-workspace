import React, { useState } from 'react';
import { Icon, Notice, Submit, useAction } from '../components.jsx';
import AnalysisResult from './AnalysisResult.jsx';

export default function YouTubeImport({ api, workspace }) {
  const [result, setResult] = useState(null);
  const action = useAction();

  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const url = new FormData(form).get('youtubeUrl').trim();
    await action.run(async () => {
      setResult(await api('/videos/youtube', { method: 'POST', json: { workspaceId: workspace.id, url } }));
      form.reset();
    });
  }

  return <section className="card">
    <span className="tag">PUBLIC YOUTUBE VIDEO</span><h2>Import from YouTube</h2>
    <p>Add a public video by URL. The video stays on YouTube; its visual summary and timed spoken transcript become workspace knowledge.</p>
    {!workspace
      ? <p>Select a workspace to import a video.</p>
      : <form onSubmit={submit}><fieldset disabled={action.busy}>
        <label>YouTube URL<input name="youtubeUrl" type="url" pattern="https://(www\.|m\.|music\.)?youtube\.com/.*|https://youtu\.be/.*" placeholder="https://www.youtube.com/watch?v=…" required/></label>
        <Notice error={action.error}/><Submit busy={action.busy}>Import video<Icon name="arrow" size={18}/></Submit>
      </fieldset></form>}
    {result && (
      <AnalysisResult result={{ ...result, filename: `YouTube video ${result.videoId}` }}/>
    )}
  </section>;
}
