import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Studio from './Studio.jsx';

const workspace = { id: 'workspace-1', name: 'Media research' };

describe('Studio media workflows', () => {
  it('imports YouTube and renders timed transcript evidence', async () => {
    const api = vi.fn(async () => ({
      videoId: 'dQw4w9WgXcQ',
      language: 'en',
      description: 'A presenter explains the workspace.',
      segments: [{ startMilliseconds: 1000, endMilliseconds: 3500, speaker: 'Speaker 1', text: 'Welcome to the workspace.' }],
    }));
    render(<Studio api={api} workspace={workspace}/>);

    fireEvent.click(screen.getByRole('tab', { name: /analyze media/i }));
    fireEvent.change(screen.getByLabelText('YouTube URL'), {
      target: { value: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
    });
    fireEvent.click(screen.getByRole('button', { name: /import video/i }));

    expect(await screen.findByText('Welcome to the workspace.')).toBeInTheDocument();
    expect(screen.getByText('00:00:01–00:00:03')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/videos/youtube', {
      method: 'POST',
      json: {
        workspaceId: workspace.id,
        url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
      },
    });
  });

  it('uploads audio for analysis and renders the transcript', async () => {
    const api = vi.fn(async () => ({ filename: 'meeting.mp3', language: 'en', text: 'The launch is Friday.' }));
    render(<Studio api={api} workspace={workspace}/>);
    fireEvent.click(screen.getByRole('tab', { name: /analyze media/i }));
    const audio = new File(['audio'], 'meeting.mp3', { type: 'audio/mpeg' });
    fireEvent.change(screen.getByLabelText('audio file'), { target: { files: [audio] } });
    fireEvent.submit(screen.getByRole('button', { name: 'Analyze audio' }).closest('form'));

    expect(await screen.findByText('The launch is Friday.')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/audio/transcriptions', expect.objectContaining({
      method: 'POST',
      body: expect.any(FormData),
    }));
  });
});
