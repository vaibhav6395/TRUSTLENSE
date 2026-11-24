import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, apiPost, clearCsrf } from './api';

describe('TRUSTLENS API client', () => {
  beforeEach(() => {
    clearCsrf();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends session cookies for GET requests', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP' }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }));

    const result = await apiGet<{ status: string }>('/api/v1/health');

    expect(result.status).toBe('UP');
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/health',
      expect.objectContaining({ credentials: 'include' }),
    );
  });

  it('fetches a CSRF token before POST and sends it in the configured header', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({
        token: 'csrf-test-token', headerName: 'X-XSRF-TOKEN',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ authenticated: true }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }));

    const result = await apiPost<{ authenticated: boolean }>('/api/v1/auth/login', {
      username: 'demo', password: 'not-a-real-password',
    });

    expect(result.authenticated).toBe(true);
    expect(fetchMock).toHaveBeenNthCalledWith(2,
      'http://localhost:8080/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-test-token' }),
      }),
    );
  });

  it('surfaces non-success HTTP responses as ApiError', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ message: 'denied' }), {
      status: 403, headers: { 'Content-Type': 'application/json' },
    }));

    await expect(apiGet('/api/v1/private')).rejects.toMatchObject({
      status: 403, message: 'denied',
    });
  });
});
