import React, { useState } from 'react';
import Markdown from 'react-markdown';
import { safeUrl } from './api.js';
import { Empty, Icon, Notice, Submit, useAction } from './components.jsx';

function mediaTime(milliseconds) {
  if (milliseconds === null || milliseconds === undefined) return null;
  const totalSeconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function mediaRange(source) {
  const start = mediaTime(source.startMilliseconds);
  if (!start) return null;
  const end = mediaTime(source.endMilliseconds);
  return end ? `${start}–${end}` : start;
}

export default function Ask({ api, workspace }) {
  const [question, setQuestion] = useState(''), [answers, setAnswers] = useState([]), [memory, setMemory] = useState(null);
  const action = useAction(), memoryAction = useAction();
  async function ask(event) { event.preventDefault(); const text = question.trim(); if (!text) return; await action.run(async () => { const answer = await api(`/knowledge/workspaces/${workspace.id}/answers`, { method: 'POST', json: { question: text } }); setAnswers(list => [...list, answer]); setQuestion(''); }); }
  return <div className="ask-layout"><section><div className="card"><div className="section-heading"><span className="tag">GROUNDED IN YOUR SOURCES</span><Icon name="studio"/></div><h2>What would you like to know?</h2><p>Ask a specific question. Explore the evidence behind each answer.</p><form onSubmit={ask}><label className="sr-only" htmlFor="question">Question</label><textarea id="question" rows="4" value={question} onChange={e => setQuestion(e.target.value)} placeholder="What does the document say about…" required disabled={action.busy}/><div className="composer-footer"><small>Each question searches your workspace independently.</small><Submit busy={action.busy}>Ask workspace<Icon name="arrow" size={18}/></Submit></div></form><Notice error={action.error}/></div>{answers.length === 0 ? <Empty title="A question can open a new door">Your answers and supporting sources will appear here.</Empty> : <div className="answers" aria-live="polite">{answers.map((answer, index) => <article key={index} className="card answer"><p className="question-label">YOU ASKED</p><h3>{answer.question}</h3><div className="markdown"><Markdown>{answer.answer}</Markdown></div><details><summary>Explore sources · {answer.sources?.length || 0} passages</summary><div className="source-grid">{answer.sources?.map(source => <div className="source" key={source.id}><strong>{source.sourceName || source.type}</strong><div className="source-location">{[source.pageNumber && `Page ${source.pageNumber}`, source.slideNumber && `Slide ${source.slideNumber}`, source.sheetName && `Sheet: ${source.sheetName}`, mediaRange(source) && `Time ${mediaRange(source)}`, source.speaker && `Speaker: ${source.speaker}`, source.heading, source.chunkSequence && `Chunk ${source.chunkSequence}`].filter(Boolean).join(' · ')}</div><p>{source.snippet}</p>{safeUrl(source.sourceUrl) && <a href={safeUrl(source.sourceUrl)} target="_blank" rel="noreferrer">Open source ↗</a>}</div>)}</div></details></article>)}</div>}</section><aside className="card knowledge-panel"><Icon name="library" size={28}/><h3>Workspace knowledge</h3><p>Review the extracted text that powers your answers.</p><button disabled={memoryAction.busy} onClick={() => memoryAction.run(async () => { try { setMemory(await api(`/knowledge/workspaces/${workspace.id}`)); } catch (e) { if (e.status === 404) setMemory({}); else throw e; } })}>{memoryAction.busy ? 'Loading…' : 'Load knowledge'}</button><Notice error={memoryAction.error}/>{memory && Object.entries({ Documents: memory.documentsInfo, Audio: memory.audioInfo, Images: memory.imagesInfo, Video: memory.videoInfo }).map(([title, content]) => <details key={title}><summary>{title}</summary><pre>{content || 'No knowledge yet.'}</pre></details>)}<small className="muted">AI can make mistakes. Check source passages for important decisions.</small></aside></div>;
}
