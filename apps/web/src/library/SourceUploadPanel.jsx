import React, { useState } from 'react';
import { formats, validateFiles } from '../api.js';
import { Icon, Notice, Submit, useAction } from '../components.jsx';

export default function SourceUploadPanel({ api, workspaceId, addJob, onProcessed }) {
  const [selectedFiles, setSelectedFiles] = useState({});
  const [mode, setMode] = useState('async');
  const [kind, setKind] = useState('document');
  const action = useAction();

  async function submit(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    await action.run(async () => {
      const entries = validateFiles(mode === 'async' ? selectedFiles : { [kind]: selectedFiles[kind] });
      const data = new FormData();
      data.append('workspaceId', workspaceId);
      for (const [type, file] of entries) data.append(mode === 'async' ? type : 'file', file);
      const response = await api(mode === 'async' ? '/orchestrator/ingestions' : formats[kind].endpoint, {
        method: 'POST',
        body: data,
      });
      if (mode === 'async') addJob(response);
      setSelectedFiles({});
      formElement.reset();
      await onProcessed(response);
    });
  }

  return <section className="card">
    <h2>Add sources</h2>
    <p>Less than 25 MB per submission. Scanned PDF pages use automatic OCR when enabled on the server.</p>
    <form onSubmit={submit}>
      <fieldset disabled={action.busy}>
        <div className="form-row">
          <label>Processing
            <select value={mode} onChange={event => setMode(event.target.value)}>
              <option value="async">Background job · multiple media types</option>
              <option value="direct">Process one file now</option>
            </select>
          </label>
          {mode === 'direct' && <label>Source type
            <select value={kind} onChange={event => setKind(event.target.value)}>
              {Object.entries(formats).map(([key, value]) => <option key={key} value={key}>{value.title}</option>)}
            </select>
          </label>}
        </div>
        <div className="upload-grid">
          {Object.entries(formats)
            .filter(([key]) => mode === 'async' || kind === key)
            .map(([key, format]) => <label className="dropzone" key={key}>
              <Icon name="upload"/><strong>{format.title}</strong><small>{format.hint}</small>
              <input type="file" aria-label={`${format.title} file`} accept={format.accept} onChange={event => setSelectedFiles(current => ({ ...current, [key]: event.target.files[0] }))}/>
            </label>)}
        </div>
        <Notice error={action.error}/>
        <Submit busy={action.busy}>Upload & process<Icon name="arrow" size={18}/></Submit>
      </fieldset>
    </form>
  </section>;
}
