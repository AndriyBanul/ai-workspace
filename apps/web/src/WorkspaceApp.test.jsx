import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import WorkspaceApp from './WorkspaceApp.jsx';

const session = {
  authorization: 'Basic test',
  user: { id: 'user-1', email: 'owner@example.com', displayName: 'Owner' },
};

function workspaceApi() {
  let workspaces = [{ id: 'workspace-1', ownerId: 'user-1', name: 'Alpha' }];
  return vi.fn(async (path, options = {}) => {
    if (path === '/workspaces' && options.method === 'POST') {
      const created = { id: 'workspace-2', ownerId: 'user-1', name: options.json.name };
      workspaces = [created, ...workspaces];
      return created;
    }
    if (path === '/workspaces' && !options.method) return workspaces;
    if (path === '/workspaces/workspace-2' && options.method === 'DELETE') {
      workspaces = workspaces.filter(workspace => workspace.id !== 'workspace-2');
      return null;
    }
    if (path.endsWith('/sources') && !options.method) return [];
    throw new Error(`Unexpected API call: ${options.method || 'GET'} ${path}`);
  });
}

describe('Workspace shell workflow', () => {
  it('creates, navigates within, and deletes a workspace', async () => {
    const api = workspaceApi();
    render(<WorkspaceApp session={session} logout={vi.fn()} api={api}/>);

    await waitFor(() => expect(screen.getByLabelText('Select workspace')).toHaveValue('workspace-1'));
    fireEvent.click(screen.getByRole('button', { name: /new workspace/i }));
    fireEvent.change(screen.getByLabelText('Workspace name'), { target: { value: 'Beta' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(screen.getByLabelText('Select workspace')).toHaveValue('workspace-2'));
    expect(api).toHaveBeenCalledWith('/workspaces', { method: 'POST', json: { name: 'Beta' } });

    fireEvent.click(screen.getByRole('button', { name: 'Ask workspace' }));
    expect(screen.getByRole('heading', { name: 'Ask your workspace' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Delete workspace' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete permanently' }));

    await waitFor(() => expect(screen.getByText('A fresh start')).toBeInTheDocument());
    expect(api).toHaveBeenCalledWith('/workspaces/workspace-2', { method: 'DELETE' });
  });
});
