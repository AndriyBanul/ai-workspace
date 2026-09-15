import React from 'react';

export default function AnalysisResult({ result }) {
  return <div className="result" role="status">
    <h3>{result.filename}</h3>
    {result.language && <p>Language: {result.language}</p>}
    {result.description && <><h4>Visual summary</h4><p className="preserve">{result.description}</p></>}
    {result.text && !result.segments?.length && <><h4>Transcript</h4><p className="preserve">{result.text}</p></>}
    {result.transcript && !result.segments?.length && <><h4>Transcript</h4><p className="preserve">{result.transcript}</p></>}
    {result.segments?.length > 0 && <><h4>Timed transcript</h4><ol className="transcript-segments">
      {result.segments.map((segment, index) => <li key={`${segment.startMilliseconds}-${index}`}>
        <span>{formatTimestamp(segment.startMilliseconds)}–{formatTimestamp(segment.endMilliseconds)}</span>
        <p>{segment.speaker && <strong>{segment.speaker}: </strong>}{segment.text}</p>
      </li>)}
    </ol></>}
  </div>;
}

export function formatTimestamp(milliseconds) {
  const totalSeconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor(totalSeconds % 3600 / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
}
