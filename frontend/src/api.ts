const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
let csrfToken: string | null = null;
let csrfHeader = 'X-XSRF-TOKEN';

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function ensureCsrf(): Promise<void> {
  if (csrfToken) return;
  const response = await fetch(`${API_BASE}/api/v1/auth/csrf`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new ApiError(response.status, 'Could not initialize secure session');
  const data = await response.json() as { token: string; headerName: string };
  csrfToken = data.token;
  csrfHeader = data.headerName;
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) await ensureCsrf();
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && csrfToken) headers.set(csrfHeader, csrfToken);
  const response = await fetch(`${API_BASE}${path}`, {
    ...options, method, headers, credentials: 'include',
  });
  if (response.status === 204) return undefined as T;
  const raw = await response.text();
  let data: unknown = undefined;
  if (raw) {
    try { data = JSON.parse(raw); } catch { data = raw; }
  }
  if (!response.ok) {
    const body = data as { message?: string; detail?: string; error?: string } | undefined;
    throw new ApiError(response.status, body?.detail ?? body?.message ?? body?.error ?? `Request failed (${response.status})`);
  }
  return data as T;
}

export function apiGet<T>(path: string): Promise<T> {
  return apiRequest<T>(path);
}
export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiRequest<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
}
export function clearCsrf(): void { csrfToken = null; }

export type Health = { status: string; service: string; timestamp: string };
export type CurrentUser = { authenticated: boolean; username: string; authorities: string[] };
export type DocumentItem = {
  id: string; title: string; department: string; classification: string;
  status: string; created_at: string; owner: string;
};
export type DecisionItem = {
  id: string; action: string; outcome: 'ALLOW' | 'DENY'; reasonCode: string;
  explanation: string; decidedAt: string; document: string; actor: string;
};
export type AuditItem = {
  id: string; sequence: number; eventType: string; payload: unknown;
  previousHash: string | null; eventHash: string; occurredAt: string; actor: string;
};

export type GrantItem = {
  id: string; document: string; subject: string; permission: string;
  expiresAt: string | null; revokedAt: string | null;
};
