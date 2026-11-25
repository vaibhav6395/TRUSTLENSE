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
  status: string; created_at: string; owner: string; ownerUsername?: string;
  description?: string; tags?: string[]; required_clearance?: number; attributes?: Record<string,string>; retention_until?: string|null;
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
  expiresAt: string | null; revokedAt: string | null; purpose?: string;
  maxDownloads?: number | null; downloadCount?: number; startsAt?: string; createdBy?: string; status?: string;
};


export async function apiUpload<T>(path: string, form: FormData): Promise<T> {
  await ensureCsrf();
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: form,
    credentials: 'include',
    headers: { Accept: 'application/json', [csrfHeader]: csrfToken ?? '' },
  });
  const raw = await response.text();
  let data: unknown = undefined;
  if (raw) { try { data = JSON.parse(raw); } catch { data = raw; } }
  if (!response.ok) {
    const body = data as { message?: string; detail?: string } | undefined;
    throw new ApiError(response.status, body?.detail ?? body?.message ?? `Upload failed (${response.status})`);
  }
  return data as T;
}

export async function apiDownload(path: string, filename: string): Promise<void> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'GET', credentials: 'include', cache: 'no-store',
    headers: { Accept: 'application/octet-stream' },
  });
  if (!response.ok) {
    const raw = await response.text();
    let message = `Download failed (${response.status})`;
    try {
      const body = JSON.parse(raw) as { message?: string; detail?: string };
      message = body.detail ?? body.message ?? message;
    } catch { if (raw) message = raw; }
    throw new ApiError(response.status, message);
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}
