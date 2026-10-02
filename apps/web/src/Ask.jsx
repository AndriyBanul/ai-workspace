import React, { useEffect, useState } from 'react';
import { Accordion, Badge, Button, Group, Paper, Stack, Text, Textarea, Title } from '@mantine/core';
import { IconArrowUp, IconCopy, IconInfoCircle, IconQuote, IconSparkles } from '@tabler/icons-react';
import Markdown from 'react-markdown';
import { safeUrl } from './api.js';
import { Notice, useAction } from './components.jsx';
import { PageTitle, SourceIcon } from './design/shared.jsx';
import s from './design/workspace.module.css';

function mediaTime(milliseconds) {
  if (milliseconds === null || milliseconds === undefined) return null;
  const totalSeconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return hours > 0
    ? String(hours).padStart(2, '0') + ':' + String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0')
    : String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
}

function sourceLocation(source) {
  const start = mediaTime(source.startMilliseconds);
  const end = mediaTime(source.endMilliseconds);
  return [
    source.pageNumber && 'Page ' + source.pageNumber,
    source.slideNumber && 'Slide ' + source.slideNumber,
    source.sheetName && 'Sheet: ' + source.sheetName,
    start && 'Time ' + (end ? start + '–' + end : start),
    source.speaker && 'Speaker: ' + source.speaker,
    source.heading,
    source.chunkSequence && 'Chunk ' + source.chunkSequence,
  ].filter(Boolean).join(' · ');
}

export default function Ask({ api, workspace }) {
  const [question, setQuestion] = useState('');
  const [answers, setAnswers] = useState([]);
  const [activeAnswerIndex, setActiveAnswerIndex] = useState(0);
  const [selected, setSelected] = useState(0);
  const [memory, setMemory] = useState(null);
  const [copied, setCopied] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [historyError, setHistoryError] = useState(null);
  const action = useAction();
  const memoryAction = useAction();
  const current = answers[activeAnswerIndex];
  const sources = current?.sources || [];
  const evidence = sources[selected];

  useEffect(() => {
    const controller = new AbortController();
    setLoadingHistory(true);
    api('/knowledge/workspaces/' + encodeURIComponent(workspace.id) + '/answers?limit=50',
      { signal: controller.signal })
      .then(entries => {
        setAnswers(entries.map(entry => entry.answer));
        setActiveAnswerIndex(0);
        setHistoryError(null);
      })
      .catch(error => { if (error.name !== 'AbortError') setHistoryError(error); })
      .finally(() => { if (!controller.signal.aborted) setLoadingHistory(false); });
    return () => controller.abort();
  }, [api, workspace.id]);

  async function ask(event) {
    event.preventDefault();
    const text = question.trim();
    if (!text) return;
    await action.run(async () => {
      const answer = await api('/knowledge/workspaces/' + workspace.id + '/answers', { method: 'POST', json: { question: text } });
      setActiveAnswerIndex(0);
      setAnswers(list => [answer, ...list]);
      setSelected(0);
      setQuestion('');
      setCopied(false);
    });
  }

  async function copyAnswer() {
    if (!current?.answer) return;
    try { await navigator.clipboard.writeText(current.answer); setCopied(true); }
    catch { setCopied(false); }
  }

  async function loadKnowledge() {
    await memoryAction.run(async () => {
      try { setMemory(await api('/knowledge/workspaces/' + workspace.id)); }
      catch (error) { if (error.status === 404) setMemory({}); else throw error; }
    });
  }

  return <>
    <PageTitle eyebrow="FOLLOW THE EVIDENCE" title="A good question changes things." description="Explore your workspace, with the sources always close by."/>
    <div className={s.askGrid}><div>
      <Paper className={s.answerPanel}>
        {current ? <><div className={s.questionBubble}><Text className={s.eyebrow}>YOUR QUESTION</Text><Title order={3} mt="sm">{current.question}</Title></div><div className={s.answerBody}><Group gap={9} mb="lg"><span className={s.answerMark}><IconSparkles size={17}/></span><Text fw={600}>Workspace answer</Text></Group><div className={s.markdown}><Markdown>{current.answer || ''}</Markdown></div><Group justify="space-between" mt="lg"><Text size="xs" c="dimmed">{sources.length} supporting passages · AI can make mistakes</Text><Button variant="subtle" size="xs" leftSection={<IconCopy size={14}/>} onClick={copyAnswer}>{copied ? 'Copied' : 'Copy'}</Button></Group></div></> : <div className={s.empty}><IconSparkles size={35}/><Title order={3}>Start with what you want to understand.</Title><Text c="dimmed">Your answer and supporting passages will appear here.</Text></div>}
      </Paper>
      <Paper className={s.composer}><form onSubmit={ask}><Textarea label="Question" placeholder="What would you like to understand?" autosize minRows={2} maxRows={6} value={question} onChange={event => setQuestion(event.target.value)} disabled={action.busy || loadingHistory} required/><Group justify="space-between" mt="md"><Text size="xs" c="dimmed">Each question searches independently.</Text><Button type="submit" rightSection={<IconArrowUp size={16}/>} loading={action.busy || loadingHistory} disabled={!question.trim() || loadingHistory}>Ask workspace</Button></Group><Notice error={action.error}/><Notice error={historyError}/></form></Paper>
      {answers.length > 0 && <Paper p="lg" mt="md"><Text fw={600} mb="sm">Recent questions</Text><Stack gap="xs">{answers.map((answer,index) => <Button variant={activeAnswerIndex === index ? 'light' : 'subtle'} key={index} onClick={() => { setActiveAnswerIndex(index); setSelected(0); setCopied(false); }}>{answer.question}</Button>)}</Stack></Paper>}
    </div><aside className={s.evidenceAside} aria-label="Supporting evidence">
      <Paper className={s.evidencePanel}><div className={s.panelHeading}><div><Text className={s.eyebrow}>SEE FOR YOURSELF</Text><Title order={3} mt={5}>Supporting evidence</Title></div><Badge variant="light" color="gray">{String(sources.length).padStart(2,'0')}</Badge></div>
        {sources.length ? <><Stack gap={0}>{sources.map((source,index) => <button key={source.id || index} className={s.evidenceItem} data-active={selected === index} onClick={() => setSelected(index)}><Group wrap="nowrap" align="flex-start" gap="sm"><SourceIcon type={source.type?.[0] + source.type?.slice(1).toLowerCase()}/><div><Text size="sm" fw={600}>{source.sourceName || source.type}</Text><Text size="xs" c="dimmed" mt={4}>{sourceLocation(source)}</Text></div><span className={s.evidenceNumber}>{index+1}</span></Group></button>)}</Stack><div className={s.quote}><IconQuote size={25} stroke={1.4}/><Text mt="md" lh={1.85}>{evidence?.snippet}</Text><Text mt="lg" size="xs" fw={600} c="dimmed">{sourceLocation(evidence)}</Text>{safeUrl(evidence?.sourceUrl) && <Button component="a" href={safeUrl(evidence.sourceUrl)} target="_blank" rel="noreferrer" variant="subtle" mt="sm" size="xs">Open source</Button>}</div></> : <div className={s.empty}><IconInfoCircle size={26}/><Text c="dimmed">{current ? 'No supporting passages were returned for this answer.' : 'Ask a question to see supporting passages.'}</Text></div>}
        <div className={s.evidenceFoot}><Text size="xs" c="dimmed">Check supporting evidence before making important decisions.</Text></div>
      </Paper>
      <Paper className={s.noteCard}><IconInfoCircle size={19}/><Text size="sm">New to this workspace? Ask about a specific topic, decision, or conversation.</Text></Paper>
      <Paper p="lg" mt="md"><Text fw={600}>Workspace knowledge</Text><Text size="sm" c="dimmed" mt="xs" mb="md">Review the extracted knowledge behind your answers.</Text><Button variant="default" size="sm" onClick={loadKnowledge} loading={memoryAction.busy}>Load knowledge</Button><Notice error={memoryAction.error}/>{memory && <Accordion mt="md">{Object.entries({ Documents: memory.documentsInfo, Audio: memory.audioInfo, Images: memory.imagesInfo, Video: memory.videoInfo }).map(([title, content]) => <Accordion.Item value={title} key={title}><Accordion.Control>{title}</Accordion.Control><Accordion.Panel><pre className={s.knowledgeRaw}>{content || 'No knowledge yet.'}</pre></Accordion.Panel></Accordion.Item>)}</Accordion>}</Paper>
    </aside></div>
  </>;
}
