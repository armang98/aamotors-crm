import { readCookie } from './utils';

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: FieldError[];
  retryAfterSeconds?: number;
  details?: Record<string, unknown> & { retryAfterSeconds?: number };
  timestamp?: string;
}

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: FieldError[];
  retryAfterSeconds?: number;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors ?? [];
    this.retryAfterSeconds = body.retryAfterSeconds;
  }
}

const BASE = '/api';
const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

type Body = unknown | FormData | undefined;

export interface FullResponse<T> {
  data: T;
  /** Comma separated warning codes from the `X-Warnings` response header. */
  warnings: string[];
}

async function request<T>(method: string, path: string, body?: Body): Promise<T> {
  const full = await requestFull<T>(method, path, body);
  return full.data;
}

async function requestFull<T>(method: string, path: string, body?: Body): Promise<FullResponse<T>> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  let payload: BodyInit | undefined;

  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  if (MUTATING.has(method)) {
    const token = readCookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = token;
  }

  let res: Response;
  try {
    res = await fetch(BASE + path, { method, headers, body: payload, credentials: 'include' });
  } catch {
    throw new ApiError({ status: 0, code: 'network', message: 'Network error' });
  }

  const warnings = (res.headers.get('X-Warnings') ?? '')
    .split(',')
    .map((w) => w.trim())
    .filter(Boolean);

  if (res.status === 204) return { data: undefined as T, warnings };

  const text = await res.text();
  let data: unknown = undefined;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = undefined;
    }
  }

  if (!res.ok) {
    const b = (data ?? {}) as Partial<ApiErrorBody>;
    throw new ApiError({
      status: res.status,
      code: b.code ?? (res.status === 401 ? 'auth.unauthenticated' : res.status === 403 ? 'auth.forbidden' : 'internal'),
      message: b.message ?? res.statusText,
      fieldErrors: b.fieldErrors,
      retryAfterSeconds: b.retryAfterSeconds ?? b.details?.retryAfterSeconds,
    });
  }
  return { data: data as T, warnings };
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: Body) => request<T>('POST', path, body),
  put: <T>(path: string, body?: Body) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: Body) => request<T>('PATCH', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
  postFull: <T>(path: string, body?: Body) => requestFull<T>('POST', path, body),
  putFull: <T>(path: string, body?: Body) => requestFull<T>('PUT', path, body),
};

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}
