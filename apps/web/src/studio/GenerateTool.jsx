import React, { useEffect, useState } from 'react';
import { Icon, Notice, Submit, useAction } from '../components.jsx';

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

  return <div className="studio-layout">
    <section className="card">
      <span className="tag">MAKE ROOM FOR IMAGINATION</span><h2>{config.title}</h2><p>{config.description}</p>
      <form onSubmit={generate}>
        <label htmlFor="generation-prompt">{config.field === 'text' ? 'Your text' : 'Your prompt'}</label>
        <textarea id="generation-prompt" rows="8" value={prompt} onChange={event => setPrompt(event.target.value)} placeholder={config.placeholder} required disabled={action.busy}/>
        <Notice error={action.error}/><Submit busy={action.busy}>Create<Icon name="studio" size={18}/></Submit>
      </form>
      <p className="muted">{config.dependency}</p>
      <small>Generated media is available to download here. Upload it to your Library to add it to workspace knowledge.</small>
    </section>
    <section className="card preview">
      <p className="eyebrow">YOUR CREATION</p>
      {url
        ? <>{config.mime === 'image' ? <img src={url} alt="Generated from your prompt"/> : config.mime === 'video' ? <video src={url} controls/> : <audio src={url} controls/>}<a className="button primary" href={url} download={config.filename}>Download {config.mime}</a></>
        : <div className="preview-empty"><Icon name="studio" size={52}/><h3>{action.busy ? 'Making something new…' : 'The next idea is yours.'}</h3><p>{action.busy ? 'You can leave this tab open while the provider works.' : 'Your generated media will appear here.'}</p></div>}
    </section>
  </div>;
}
