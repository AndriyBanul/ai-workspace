import React from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Login from './Login.jsx';
import { render } from './test-utils.jsx';

afterEach(() => vi.unstubAllGlobals());

describe('Authentication screen', () => {
  it('signs in with the existing account endpoint', async () => {
    const user = { id: 'user-1', email: 'owner@example.com', displayName: 'Owner' };
    const fetchMock = vi.fn(async () => ({ ok: true, status: 200, json: async () => user }));
    vi.stubGlobal('fetch', fetchMock);
    const onLogin = vi.fn();
    render(<Login onLogin={onLogin}/>);

    fireEvent.change(screen.getByRole('textbox', { name: /Email address/i }), { target: { value: user.email } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'passwordpassword' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith(expect.objectContaining({ user, authorization: expect.stringMatching(/^Basic /) })));
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/me', expect.any(Object));
    expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toMatch(/^Basic /);
  });

  it('registers before requesting the current account', async () => {
    const user = { id: 'user-2', email: 'new@example.com', displayName: 'New User' };
    const fetchMock = vi.fn(async () => ({ ok: true, status: 200, json: async () => user }));
    vi.stubGlobal('fetch', fetchMock);
    const onLogin = vi.fn();
    render(<Login onLogin={onLogin}/>);

    fireEvent.click(screen.getByRole('button', { name: 'Create an account' }));
    fireEvent.change(screen.getByRole('textbox', { name: /Your name/i }), { target: { value: user.displayName } });
    fireEvent.change(screen.getByRole('textbox', { name: /Email address/i }), { target: { value: user.email } });
    fireEvent.change(screen.getByLabelText(/^Password/i), { target: { value: 'passwordpassword' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith(expect.objectContaining({ user })));
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: user.email, password: 'passwordpassword', displayName: user.displayName }),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/me', expect.any(Object));
  });
});
