import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Ask from './Ask.jsx';

const workspace = { id: 'workspace-1', name: 'Research' };

describe('Ask workspace workflow', () => {
  it('submits a question and exposes grounded source evidence', async () => {
    const api = vi.fn(async () => ({
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
    }));
    render(<Ask api={api} workspace={workspace}/>);

    fireEvent.change(screen.getByLabelText('Question'), { target: { value: 'When is the launch?' } });
    fireEvent.click(screen.getByRole('button', { name: /ask workspace/i }));

    expect(await screen.findByText('Friday')).toBeInTheDocument();
    fireEvent.click(screen.getByText(/Explore sources/));
    expect(screen.getByText('meeting.mp3')).toBeInTheDocument();
    expect(screen.getByText(/Time 01:02–01:08/)).toBeInTheDocument();
    expect(screen.getByText(/Speaker: Alex/)).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith(`/knowledge/workspaces/${workspace.id}/answers`, {
      method: 'POST',
      json: { question: 'When is the launch?' },
    });
  });
});
