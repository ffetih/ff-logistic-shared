import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { can, closeSession, holds, openSession, readSession, type Session } from "./session";

type SessionContextValue = {
  session: Session | null;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  allowed: (...roles: string[]) => boolean;
  /** Without the TENANT_ADMIN shortcut of allowed(): for screens only their own role opens (reports). */
  holdsRole: (...roles: string[]) => boolean;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => readSession());

  const value = useMemo<SessionContextValue>(() => ({
    session,
    async signIn(username, password) {
      setSession(await openSession(username, password));
    },
    async signOut() {
      await closeSession();
      setSession(null);
    },
    allowed(...roles) {
      return session !== null && can(session, ...roles);
    },
    holdsRole(...roles) {
      return session !== null && holds(session, ...roles);
    },
  }), [session]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error("useSession outside SessionProvider");
  }
  return value;
}
