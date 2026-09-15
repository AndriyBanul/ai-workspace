import React from 'react';
import { date } from '../components.jsx';

export default function SourceDetails({ source, recovery, onClose }) {
  if (!source) return null;
  const fields = [
    ['Status', source.status],
    ['Recovery', recovery?.status],
    ['Recovery operation', recovery?.operationType],
    ['Attempts', recovery?.attemptCount],
    ['Next recovery check', recovery?.nextAttemptAt && date(recovery.nextAttemptAt)],
    ['Last recovery error', recovery?.lastErrorMessage],
    ['Created', date(source.createdAt)],
    ['Updated', date(source.updatedAt)],
    ['Content type', source.contentType],
    ['Source URL', source.sourceUrl],
    ['SHA-256', source.checksumSha256],
    ['Source ID', source.id],
  ].filter(([, value]) => value !== null && value !== undefined && value !== '');

  return <section className="card">
    <div className="section-heading"><h3>{source.originalFilename}</h3><button className="text-button" onClick={onClose}>Close</button></div>
    <dl>{fields.map(([key, value]) => <React.Fragment key={key}><dt>{key}</dt><dd>{value}</dd></React.Fragment>)}</dl>
  </section>;
}
