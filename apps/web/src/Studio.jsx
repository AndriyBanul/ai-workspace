import React, { useEffect, useState } from 'react';
import { formats, validateFiles } from './api.js';
import { Icon, Notice, Submit, useAction } from './components.jsx';

const tools = {
  image: { title: 'Generate an image', description: 'Describe a scene and bring it to life.', endpoint: '/images/generations', field: 'description', mime: 'image', filename: 'generated-image.png', placeholder: 'A sunlit reading room, warm oak shelves, editorial photography…', dependency: 'Uses the configured image generation provider.' },
  video: { title: 'Generate a video', description: 'Create a moving story from a prompt.', endpoint: '/videos/generations', field: 'description', mime: 'video', filename: 'generated-video.mp4', placeholder: 'A slow camera move through a quiet forest at sunrise…', dependency: 'Video generation may take several minutes and requires a configured provider.' },
  speech: { title: 'Text to speech', description: 'Give your words a voice.', endpoint: '/audio/speech', field: 'text', mime: 'audio', filename: 'speech.wav', placeholder: 'Enter the text you want to hear…', dependency: 'Requires the configured speech synthesis service.' },
};

export default function Studio({ api, workspace }) {
  const [tool, setTool] = useState('image');
  return <><div className="studio-tabs" role="tablist" aria-label="Studio tools">{Object.entries(tools).map(([key, value]) => <button role="tab" aria-selected={tool === key} key={key} className={tool === key ? 'active' : ''} onClick={() => setTool(key)}><Icon name="studio"/>{value.title}</button>)}<button role="tab" aria-selected={tool === 'analyze'} className={tool === 'analyze' ? 'active' : ''} onClick={() => setTool('analyze')}><Icon name="search"/>Analyze media</button></div>{tool === 'analyze' ? <Analyze key={workspace?.id} api={api} workspace={workspace}/> : <Generate key={tool} api={api} config={tools[tool]}/>}</>;
}

function Generate({ api, config }) {
  const [prompt, setPrompt] = useState(''), [blob, setBlob] = useState(null), [url, setUrl] = useState(null), action = useAction();
  useEffect(() => { if (!blob) return; const value = URL.createObjectURL(blob); setUrl(value); return () => URL.revokeObjectURL(value); }, [blob]);
  async function generate(event) { event.preventDefault(); await action.run(async () => setBlob(await api(config.endpoint, { method: 'POST', json: { [config.field]: prompt.trim() }, binary: true }))); }
  return <div className="studio-layout"><section className="card"><span className="tag">MAKE ROOM FOR IMAGINATION</span><h2>{config.title}</h2><p>{config.description}</p><form onSubmit={generate}><label htmlFor="generation-prompt">{config.field === 'text' ? 'Your text' : 'Your prompt'}</label><textarea id="generation-prompt" rows="8" value={prompt} onChange={e => setPrompt(e.target.value)} placeholder={config.placeholder} required disabled={action.busy}/><Notice error={action.error}/><Submit busy={action.busy}>Create<Icon name="studio" size={18}/></Submit></form><p className="muted">{config.dependency}</p><small>Generated media is available to download here. Upload it to your Library to add it to workspace knowledge.</small></section><section className="card preview"><p className="eyebrow">YOUR CREATION</p>{url ? <>{config.mime === 'image' ? <img src={url} alt="Generated from your prompt"/> : config.mime === 'video' ? <video src={url} controls/> : <audio src={url} controls/>}<a className="button primary" href={url} download={config.filename}>Download {config.mime}</a></> : <div className="preview-empty"><Icon name="studio" size={52}/><h3>{action.busy ? 'Making something new…' : 'The next idea is yours.'}</h3><p>{action.busy ? 'You can leave this tab open while the provider works.' : 'Your generated media will appear here.'}</p></div>}</section></div>;
}

function Analyze({ api, workspace }) {
  const [kind, setKind] = useState('audio'), [file, setFile] = useState(null), [result, setResult] = useState(null), action = useAction();
  const config = formats[kind];
  async function submit(event) { event.preventDefault(); await action.run(async () => { validateFiles({ [kind]: file }); const data = new FormData(); data.append('workspaceId', workspace.id); data.append('file', file); setResult(await api(config.endpoint, { method: 'POST', body: data })); }); }
  return <div className="studio-layout"><section className="card"><h2>Listen. Look. Understand.</h2><p>Transcribe audio or analyze images and video. Timed speech results are added to the selected workspace.</p>{!workspace ? <p>Select a workspace to analyze media.</p> : <form onSubmit={submit}><fieldset disabled={action.busy}><label>Media type<select value={kind} onChange={e => { setKind(e.target.value); setFile(null); setResult(null); }}><option value="audio">Audio transcription</option><option value="image">Image description</option><option value="video">Video analysis</option></select></label><label className="dropzone" key={kind}><Icon name="upload"/><strong>{config.hint}</strong><input type="file" required accept={config.accept} onChange={e => setFile(e.target.files[0])}/></label><Submit busy={action.busy}>Analyze {kind}</Submit></fieldset><Notice error={action.error}/></form>}{result && <AnalysisResult result={result}/>}</section><YouTubeImport api={api} workspace={workspace}/></div>;
}

function YouTubeImport({ api, workspace }) {
  const [result, setResult] = useState(null), action = useAction();
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget, url = new FormData(form).get('youtubeUrl').trim();
    await action.run(async () => { setResult(await api('/videos/youtube', { method: 'POST', json: { workspaceId: workspace.id, url } })); form.reset(); });
  }
  return <section className="card"><span className="tag">PUBLIC YOUTUBE VIDEO</span><h2>Import from YouTube</h2><p>Add a public video by URL. The video stays on YouTube; its visual summary and timed spoken transcript become workspace knowledge.</p>{!workspace ? <p>Select a workspace to import a video.</p> : <form onSubmit={submit}><fieldset disabled={action.busy}><label>YouTube URL<input name="youtubeUrl" type="url" pattern="https://(www\.|m\.|music\.)?youtube\.com/.*|https://youtu\.be/.*" placeholder="https://www.youtube.com/watch?v=…" required/></label><Notice error={action.error}/><Submit busy={action.busy}>Import video<Icon name="arrow" size={18}/></Submit></fieldset></form>}{result && <AnalysisResult result={{ ...result, filename: `YouTube video ${result.videoId}` }}/>}</section>;
}

function AnalysisResult({ result }) {
  return <div className="result" role="status"><h3>{result.filename}</h3>{result.language && <p>Language: {result.language}</p>}{result.description && <><h4>Visual summary</h4><p className="preserve">{result.description}</p></>}{result.text && !result.segments?.length && <><h4>Transcript</h4><p className="preserve">{result.text}</p></>}{result.transcript && !result.segments?.length && <><h4>Transcript</h4><p className="preserve">{result.transcript}</p></>}{result.segments?.length > 0 && <><h4>Timed transcript</h4><ol className="transcript-segments">{result.segments.map((segment, index) => <li key={`${segment.startMilliseconds}-${index}`}><span>{formatTimestamp(segment.startMilliseconds)}–{formatTimestamp(segment.endMilliseconds)}</span><p>{segment.speaker && <strong>{segment.speaker}: </strong>}{segment.text}</p></li>)}</ol></>}</div>;
}

function formatTimestamp(milliseconds) {
  const totalSeconds = Math.floor(milliseconds / 1000), hours = Math.floor(totalSeconds / 3600), minutes = Math.floor(totalSeconds % 3600 / 60), seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
}
