import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createApi } from './api.js';

afterEach(() => vi.unstubAllGlobals());

describe('API client lifecycle', () => {
  it('preserves caller headers and adds authenticated JSON headers', async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true, status: 200, json: async () => ({ accepted: true }),
    }));
    vi.stubGlobal('fetch', fetchMock);

    await createApi('Basic example')('/workspaces', {
      method: 'POST', headers: { 'X-Test': 'present' }, json: { name: 'Research' },
    });

    const [, request] = fetchMock.mock.calls[0];
    expect(request.headers.get('X-Test')).toBe('present');
    expect(request.headers.get('Authorization')).toBe('Basic example');
    expect(request.headers.get('Content-Type')).toBe('application/json');
    expect(request.body).toBe('{"name":"Research"}');
  });

  it('ends the in-memory session when an authenticated request is rejected', async () => {
    const onUnauthorized = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: false, status: 401, headers: new Headers(), json: async () => ({ detail: 'Unauthorized' }),
    })));
    const api = createApi('Basic example', { onUnauthorized });

    await expect(api('/workspaces')).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('preserves retry timing for rate limits', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: false, status: 429, headers: new Headers({ 'Retry-After': '45' }),
      json: async () => ({ code: 'USER_QUOTA_EXCEEDED' }),
    })));

    await expect(createApi('Basic example')('/workspaces')).rejects.toMatchObject({
      status: 429, code: 'USER_QUOTA_EXCEEDED', retryAfterSeconds: 45,
    });
  });
});
