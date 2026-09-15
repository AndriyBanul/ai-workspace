import React, { useState } from 'react';
import Activity from './Activity.jsx';
import Ask from './Ask.jsx';
import Library from './Library.jsx';
import Studio from './Studio.jsx';

export default function WorkspaceContent({ api, workspace, view, userId }) {
  const jobKey = `ai-workspace.jobs.${userId}.${workspace?.id}`;
  const [jobs, setJobs] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(jobKey) || '[]');
    } catch {
      return [];
    }
  });
  const addJob = job => setJobs(current => {
    const next = [
      { ...job, createdAt: new Date().toISOString() },
      ...current.filter(existing => existing.jobId !== job.jobId),
    ].slice(0, 50);
    localStorage.setItem(jobKey, JSON.stringify(next));
    return next;
  });

  if (view === 'library') return <Library api={api} workspace={workspace} addJob={addJob}/>;
  if (view === 'ask') return <Ask api={api} workspace={workspace}/>;
  if (view === 'studio') return <Studio api={api} workspace={workspace}/>;
  return <Activity api={api} workspace={workspace} jobs={jobs} addJob={addJob}/>;
}
