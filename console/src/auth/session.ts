import { ApiError, problemMessage } from "../api/problem";

const STORAGE_KEY = "ff.session";

export type Session = {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  username: string;
  tenantId: string;
  roles: string[];
};

type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

export function readSession(): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as Session;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function can(session: Session, ...roles: string[]): boolean {
  return session.roles.includes("TENANT_ADMIN") || roles.some((role) => session.roles.includes(role));
}

/** Like can(), but TENANT_ADMIN counts only for itself: the role has to be in the token. */
export function holds(session: Session, ...roles: string[]): boolean {
  return roles.some((role) => session.roles.includes(role));
}

export async function openSession(username: string, password: string): Promise<Session> {
  const response = await fetch("/identity/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    throw new ApiError(response.status === 401 ? "Kullanıcı adı veya parola hatalı." : await problemMessage(response), response.status);
  }
  return store(await response.json() as TokenResponse);
}

export async function refreshSession(): Promise<Session | null> {
  const current = readSession();
  if (!current) {
    return null;
  }
  const response = await fetch("/identity/sessions/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  });
  if (!response.ok) {
    clearSession();
    return null;
  }
  return store(await response.json() as TokenResponse);
}

export async function closeSession(): Promise<void> {
  const current = readSession();
  clearSession();
  if (!current) {
    return;
  }
  await fetch("/identity/logout", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: current.refreshToken }),
  }).catch(() => undefined);
}

function store(token: TokenResponse): Session {
  const claims = readClaims(token.accessToken);
  const session: Session = {
    accessToken: token.accessToken,
    refreshToken: token.refreshToken,
    expiresAt: Date.now() + token.expiresIn * 1000,
    username: claims.username,
    tenantId: claims.tenantId,
    roles: claims.roles,
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  return session;
}

function readClaims(accessToken: string): { username: string; tenantId: string; roles: string[] } {
  try {
    const segment = accessToken.split(".")[1];
    if (!segment) {
      return { username: "", tenantId: "", roles: [] };
    }
    const padded = segment.replace(/-/g, "+").replace(/_/g, "/");
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (!payload || typeof payload !== "object") {
      return { username: "", tenantId: "", roles: [] };
    }
    const record = payload as Record<string, unknown>;
    const realm = record.realm_access;
    const roles = realm && typeof realm === "object" && "roles" in realm && Array.isArray(realm.roles)
      ? realm.roles.filter((role): role is string => typeof role === "string")
      : [];
    return {
      username: typeof record.preferred_username === "string" ? record.preferred_username : "",
      tenantId: typeof record.tenant_id === "string" ? record.tenant_id : "",
      roles,
    };
  } catch {
    return { username: "", tenantId: "", roles: [] };
  }
}
