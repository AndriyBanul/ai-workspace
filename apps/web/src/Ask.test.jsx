import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { render } from './test-utils.jsx';
import { describe, expect, it, vi } from 'vitest';
import Ask from './Ask.jsx';

const workspace = { id: 'workspace-1', name: 'Research' };

describe('Ask workspace workflow', () => {
  it('submits a question and exposes grounded source evidence', async () => {
    const api = vi.fn(async (path, options = {}) => options.method === 'POST' ? ({
      question: 'When is the launch?',
      answer: 'The launch is **Friday**.',
      sources: [{
        id: 'chunk-1',
        type: 'AUDIO',
        sourceName: 'meeting.mp3',
        snippet: 'We agreed that the launch is Friday.',
        startMilliseconds: 62000,
        endMilliseconds: 68000,
        speaker: 'Alex',
      }],
    }) : []);
    render(<Ask api={api} workspace={workspace}/>);

    fireEvent.change(screen.getByRole('textbox', { name: /Question/i }), { target: { value: 'When is the launch?' } });
    await waitFor(() => expect(screen.getByRole('button', { name: /ask workspace/i })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: /ask workspace/i }));

    expect(await screen.findByText('Friday')).toBeInTheDocument();
    expect(screen.getByText('meeting.mp3')).toBeInTheDocument();
    expect(screen.getAllByText(/Time 01:02–01:08/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Speaker: Alex/).length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledWith(`/knowledge/workspaces/${workspace.id}/answers`, {
      method: 'POST',
      json: { question: 'When is the launch?' },
    });
  });

  it('restores saved answers and evidence after opening the view again', async () => {
    const saved = { question: 'What was decided?', answer: 'Launch Friday.', sources: [] };
    const api = vi.fn(async () => [{ id: 'answer-1', createdAt: '2026-09-23T10:00:00Z', answer: saved }]);
    render(<Ask api={api} workspace={workspace}/>);

    expect(await screen.findByText('Launch Friday.')).toBeInTheDocument();
    expect(screen.getAllByText('What was decided?').length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledWith('/knowledge/workspaces/workspace-1/answers?limit=50',
      expect.objectContaining({ signal: expect.any(AbortSignal) }));
  });
});
