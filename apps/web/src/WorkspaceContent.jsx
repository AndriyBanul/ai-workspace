import React, { Suspense, lazy, useState } from 'react';

const Activity = lazy(() => import('./Activity.jsx'));
const Ask = lazy(() => import('./Ask.jsx'));
const Library = lazy(() => import('./Library.jsx'));
const Studio = lazy(() => import('./Studio.jsx'));

export default function WorkspaceContent({ api, workspace, view, onNavigate }) {
  const [jobs, setJobs] = useState([]);
  const addJob = job => setJobs(current => {
    const next = [
      { ...job, createdAt: new Date().toISOString() },
      ...current.filter(existing => existing.jobId !== job.jobId),
    ].slice(0, 50);
    return next;
  });

  let content;
  if (view === 'library') content = <Library api={api} workspace={workspace} addJob={addJob} onNavigate={onNavigate}/>;
  else if (view === 'ask') content = <Ask api={api} workspace={workspace}/>;
  else if (view === 'studio') content = <Studio api={api} workspace={workspace}/>;
  else content = <Activity api={api} workspace={workspace} jobs={jobs} setJobs={setJobs} addJob={addJob}/>;
  return <Suspense fallback={<p role="status">Loading workspace view…</p>}>{content}</Suspense>;
}
