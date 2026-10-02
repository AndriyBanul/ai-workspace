import React from 'react';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { render } from './test-utils.jsx';
import { describe, expect, it, vi } from 'vitest';
import Library from './Library.jsx';

const workspace = { id: 'workspace-1', name: 'Research' };
const source = {
  id: 'source-1',
  workspaceId: workspace.id,
  originalFilename: 'report.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  sourceType: 'DOCUMENT',
  status: 'PROCESSED',
  checksumSha256: 'a'.repeat(64),
  createdAt: '2026-09-16T10:00:00Z',
  updatedAt: '2026-09-16T10:01:00Z',
};

function libraryApi() {
  let files = [source];
  return vi.fn(async (path, options = {}) => {
    if (path === `/workspaces/${workspace.id}/sources` && !options.method) return files;
    if (path === `/workspaces/${workspace.id}/sources/${source.id}/recovery`) {
      return {
        sourceId: source.id,
        operationType: 'PROCESS',
        status: 'DEAD_LETTER',
        attemptCount: 5,
        lastErrorMessage: 'OpenSearch unavailable',
      };
    }
    if (path === `/workspaces/${workspace.id}/sources/${source.id}/reprocess`) {
      return { jobId: 'job-1', workspaceId: workspace.id, submitted: ['documents'] };
    }
    if (path === '/orchestrator/ingestions' && options.method === 'POST') {
      return { jobId: 'job-upload', workspaceId: workspace.id, submitted: ['documents'] };
    }
    if (path === '/documents/web-page' && options.method === 'POST') {
      files = [...files, {
        ...source,
        id: 'source-web',
        originalFilename: 'Example article',
        sourceType: 'WEB_PAGE',
        sourceUrl: options.json.url,
        sizeBytes: 0,
      }];
      return { sourceId: 'source-web', characterCount: 1200 };
    }
    if (path === '/videos/youtube' && options.method === 'POST') {
      files = [...files, {
        ...source,
        id: 'source-youtube',
        originalFilename: 'YouTube video',
        sourceType: 'YOUTUBE',
        sourceUrl: options.json.url,
        sizeBytes: 0,
      }];
      return { sourceId: 'source-youtube', videoId: 'dQw4w9WgXcQ' };
    }
    if (path === '/documents/text' && options.method === 'POST') {
      files = [...files, { ...source, id: 'source-direct', originalFilename: 'direct.txt' }];
      return { sourceId: 'source-direct', filename: 'direct.txt' };
    }
    if (path === `/workspaces/${workspace.id}/sources/${source.id}` && options.method === 'DELETE') {
      files = [];
      return null;
    }
    if (path === `/workspaces/${workspace.id}/sources/${source.id}`) return source;
    throw new Error(`Unexpected API call: ${options.method || 'GET'} ${path}`);
  });
}

describe('Library source workflow', () => {
  it('shows recovery details, submits reprocessing, and deletes the source', async () => {
    const api = libraryApi();
    const addJob = vi.fn();
    render(<Library api={api} workspace={workspace} addJob={addJob}/>);

    await screen.findAllByRole('button', { name: /report\.pdf/i });
    fireEvent.click(screen.getAllByRole('button', { name: /report\.pdf/i })[0]);

    fireEvent.click(await screen.findByText('Technical and recovery details'));
    expect(await screen.findByText('DEAD_LETTER')).toBeInTheDocument();
    expect(screen.getByText('OpenSearch unavailable')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith(`/workspaces/${workspace.id}/sources/${source.id}/recovery`);

    fireEvent.click(screen.getByRole('button', { name: 'Reprocess source' }));
    await waitFor(() => expect(addJob).toHaveBeenCalledWith(expect.objectContaining({ jobId: 'job-1' })));
    expect(api).toHaveBeenCalledWith(
      `/workspaces/${workspace.id}/sources/${source.id}/reprocess`,
      { method: 'POST' },
    );

    fireEvent.click(screen.getByRole('button', { name: 'Back to library' }));
    fireEvent.click(screen.getAllByRole('button', { name: /report\.pdf/i })[0]);
    await screen.findByRole('button', { name: 'Delete source' });
    fireEvent.click(screen.getByRole('button', { name: 'Delete source' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Delete report.pdf?');
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete source' }));

    await waitFor(() => expect(screen.queryAllByRole('button', { name: /report\.pdf/i })).toHaveLength(0));
    expect(api).toHaveBeenCalledWith(
      `/workspaces/${workspace.id}/sources/${source.id}`,
      { method: 'DELETE' },
    );
  });

  it('filters sources by name without another API request', async () => {
    const api = libraryApi();
    render(<Library api={api} workspace={workspace} addJob={vi.fn()}/>);
    await screen.findAllByRole('button', { name: /report\.pdf/i });
    const callsBeforeFiltering = api.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Search sources'), { target: { value: 'missing' } });

    expect(screen.getByText('No matching sources')).toBeInTheDocument();
    expect(api).toHaveBeenCalledTimes(callsBeforeFiltering);
  });

  it('submits file and web-page ingestion through their user workflows', async () => {
    const api = libraryApi();
    const addJob = vi.fn();
    const { container } = render(<Library api={api} workspace={workspace} addJob={addJob}/>);
    await screen.findAllByRole('button', { name: /report\.pdf/i });

    fireEvent.click(screen.getByRole('button', { name: /add sources/i }));
    const document = new File(['workspace knowledge'], 'notes.txt', { type: 'text/plain' });
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [document] } });
    fireEvent.click(screen.getByRole('button', { name: /upload & process/i }));

    await waitFor(() => expect(addJob).toHaveBeenCalledWith(expect.objectContaining({ jobId: 'job-upload' })));
    expect(api).toHaveBeenCalledWith('/orchestrator/ingestions', expect.objectContaining({
      method: 'POST',
      body: expect.any(FormData),
    }));

    fireEvent.click(screen.getByRole('button', { name: /Add sources/i }));
    fireEvent.click(screen.getByRole('tab', { name: /Web page/i }));
    fireEvent.change(screen.getByRole('textbox', { name: /Web page URL/i }), { target: { value: 'https://example.com/article' } });
    fireEvent.click(screen.getByRole('button', { name: 'Import' }));

    expect((await screen.findAllByRole('button', { name: /Example article/i })).length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledWith('/documents/web-page', {
      method: 'POST',
      json: { workspaceId: workspace.id, url: 'https://example.com/article' },
    });
  });

  it('imports a YouTube URL from the unified add-source dialog', async () => {
    const api = libraryApi();
    render(<Library api={api} workspace={workspace} addJob={vi.fn()}/>);
    await screen.findAllByRole('button', { name: /report\.pdf/i });

    fireEvent.click(screen.getByRole('button', { name: /Add sources/i }));
    fireEvent.click(screen.getByRole('tab', { name: /YouTube/i }));
    fireEvent.change(screen.getByRole('textbox', { name: /YouTube URL/i }), {
      target: { value: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Import video' }));

    expect((await screen.findAllByRole('button', { name: /YouTube video/i })).length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledWith('/videos/youtube', {
      method: 'POST',
      json: { workspaceId: workspace.id, url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' },
    });
  });

  it('processes one document directly when requested', async () => {
    const api = libraryApi();
    const { container } = render(<Library api={api} workspace={workspace} addJob={vi.fn()}/>);
    await screen.findAllByRole('button', { name: /report\.pdf/i });

    fireEvent.click(screen.getByRole('button', { name: /Add sources/i }));
    fireEvent.click(screen.getByRole('combobox', { name: /Processing/i }));
    fireEvent.click(screen.getByRole('option', { name: /Process one file now/i }));
    const document = new File(['direct knowledge'], 'direct.txt', { type: 'text/plain' });
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [document] } });
    fireEvent.click(screen.getByRole('button', { name: /Upload & process/i }));

    expect((await screen.findAllByRole('button', { name: /direct\.txt/i })).length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledWith('/documents/text', expect.objectContaining({ method: 'POST', body: expect.any(FormData) }));
  });
});
