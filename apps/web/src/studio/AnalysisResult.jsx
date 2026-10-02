import React from 'react';
import { Badge, Text, Title } from '@mantine/core';
import s from '../design/workspace.module.css';

export default function AnalysisResult({ result }) {
  return <div className={s.analysisResult} role="status">
    <Title order={3}>{result.filename || 'Source analysis'}</Title>
    {result.language && <Badge variant="light" mt="sm">{result.language}</Badge>}
    {result.description && <><Title order={4} mt="lg">Visual summary</Title><Text mt="sm" className={s.preserve}>{result.description}</Text></>}
    {result.text && !result.segments?.length && <><Title order={4} mt="lg">Transcript</Title><Text mt="sm" className={s.preserve}>{result.text}</Text></>}
    {result.transcript && !result.segments?.length && <><Title order={4} mt="lg">Transcript</Title><Text mt="sm" className={s.preserve}>{result.transcript}</Text></>}
    {result.segments?.length > 0 && <><Title order={4} mt="lg">Timed transcript</Title><div className={s.transcript}>{result.segments.map((segment, index) => <div className={s.transcriptRow} key={String(segment.startMilliseconds) + '-' + index}><span>{formatTimestamp(segment.startMilliseconds)}–{formatTimestamp(segment.endMilliseconds)}</span><Text>{segment.speaker && <strong>{segment.speaker}: </strong>}{segment.text}</Text></div>)}</div></>}
  </div>;
}

export function formatTimestamp(milliseconds) {
  const totalSeconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor(totalSeconds % 3600 / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
}
