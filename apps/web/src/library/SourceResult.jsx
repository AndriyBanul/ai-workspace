import React from 'react';

export default function SourceResult({ result, onDismiss }) {
  if (!result) return null;

  return <section className="card result" role="status">
    <div className="section-heading"><h3>{result.jobId ? 'Upload submitted' : 'Source processed'}</h3><button className="text-button" onClick={onDismiss}>Dismiss</button></div>
    {result.jobId
      ? <p>Follow this job in Activity: <code>{result.jobId}</code></p>
      : <><p>{result.text || result.description || `${result.characterCount ?? ''} characters extracted${result.blockCount ? ` into ${result.blockCount} blocks` : ''}.`}</p><details><summary>Extraction details</summary><pre>{JSON.stringify(result, null, 2)}</pre></details></>}
  </section>;
}
