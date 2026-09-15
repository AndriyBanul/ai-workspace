import React, { useState } from 'react';
import { formats, validateFiles } from '../api.js';
import { Icon, Notice, Submit, useAction } from '../components.jsx';
import AnalysisResult from './AnalysisResult.jsx';
import YouTubeImport from './YouTubeImport.jsx';

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
    setKind(nextKind);
    setFile(null);
    setResult(null);
  }

  return <div className="studio-layout">
    <section className="card">
      <h2>Listen. Look. Understand.</h2>
      <p>Transcribe audio or analyze images and video. Timed speech results are added to the selected workspace.</p>
      {!workspace
        ? <p>Select a workspace to analyze media.</p>
        : <form onSubmit={submit}><fieldset disabled={action.busy}>
          <label>Media type<select value={kind} onChange={event => changeKind(event.target.value)}><option value="audio">Audio transcription</option><option value="image">Image description</option><option value="video">Video analysis</option></select></label>
          <label className="dropzone" key={kind}><Icon name="upload"/><strong>{config.hint}</strong><input aria-label={`${kind} file`} type="file" required accept={config.accept} onChange={event => setFile(event.target.files[0])}/></label>
          <Submit busy={action.busy}>Analyze {kind}</Submit>
        </fieldset><Notice error={action.error}/></form>}
      {result && <AnalysisResult result={result}/>}
    </section>
    <YouTubeImport api={api} workspace={workspace}/>
  </div>;
}
