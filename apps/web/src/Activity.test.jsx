import React from 'react';
import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Activity from './Activity.jsx';
import { render } from './test-utils.jsx';

const workspace = { id: 'workspace-1', name: 'Research' };

describe('Activity workflow', () => {
  it('shows real job steps and looks up a job by ID', async () => {
    const api = vi.fn(async path => ({
      jobId: path.endsWith('job-2') ? 'job-2' : 'job-1',
      workspaceId: workspace.id,
      status: 'COMPLETED',
      createdAt: '2026-09-23T10:00:00Z',
      completedAt: '2026-09-23T10:02:00Z',
      steps: [
        { type: 'DOCUMENT', status: 'COMPLETED', startedAt: '2026-09-23T10:00:00Z', completedAt: '2026-09-23T10:02:00Z' },
        { type: 'AUDIO', status: 'SKIPPED' },
      ],
    }));
    const addJob = vi.fn();
    render(<Activity api={api} workspace={workspace} jobs={[{ jobId: 'job-1', submitted: ['document'] }]} addJob={addJob}/>);

    expect(await screen.findByText('Job details')).toBeInTheDocument();
    expect((await screen.findAllByText('Not submitted')).length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledWith('/orchestrator/jobs/job-1', expect.objectContaining({ signal: expect.any(AbortSignal) }));

    fireEvent.change(screen.getByRole('textbox', { name: 'Job ID' }), { target: { value: 'job-2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Find job' }));

    expect(await screen.findByText('job-2')).toBeInTheDocument();
    expect(addJob).toHaveBeenCalledWith(expect.objectContaining({ jobId: 'job-2' }));
  });
});
