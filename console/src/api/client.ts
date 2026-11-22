import { ApiError, problemMessage } from "./problem";
import { clearSession, readSession, refreshSession } from "../auth/session";

export type TraceRecord = {
  traceId: string;
  method: string;
  path: string;
};

const JAEGER_BASE_URL = "http://localhost:16686";
let currentTrace: TraceRecord | null = null;
const traceListeners = new Set<() => void>();

function randomHex(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function recordTrace(trace: TraceRecord) {
  currentTrace = trace;
  traceListeners.forEach((listener) => listener());
}

export function readLastTrace(): TraceRecord | null {
  return currentTrace;
}

export function subscribeTrace(listener: () => void): () => void {
  traceListeners.add(listener);
  return () => {
    traceListeners.delete(listener);
  };
}

export function jaegerTraceUrl(traceId: string): string {
  return `${JAEGER_BASE_URL}/trace/${encodeURIComponent(traceId)}`;
}

export function jaegerServiceUrl(service: string): string {
  return `${JAEGER_BASE_URL}/search?service=${encodeURIComponent(service)}`;
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}, attempt = 0): Promise<T> {
  const method = init.method ?? "GET";
  const traceId = randomHex(16);
  const spanId = randomHex(8);
  const headers: Record<string, string> = {
    traceparent: `00-${traceId}-${spanId}-01`,
  };
  const session = readSession();
  if (session) {
    headers.Authorization = `Bearer ${session.accessToken}`;
  }
  if (init.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  recordTrace({ traceId, method, path });

  const response = await fetch(path, {
    method,
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });

  if (response.status === 401 && attempt === 0 && session) {
    const next = await refreshSession();
    if (next) {
      return api(path, init, 1);
    }
    clearSession();
    window.location.assign("/login");
    throw new ApiError("Oturum sona erdi.", 401);
  }

  if (!response.ok) {
    throw new ApiError(await problemMessage(response), response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
