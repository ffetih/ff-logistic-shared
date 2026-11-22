import { useEffect, useState } from "react";
import { NavLink, Navigate, Outlet } from "react-router-dom";
import { jaegerHomeUrl, jaegerTraceUrl, readLastTrace, subscribeTrace, type TraceRecord } from "../api/client";
import { useSession } from "../auth/SessionContext";

// The same rules as the edge's @PreAuthorize. TENANT_ADMIN opens every screen.
// The report screen is the reporting API's callers: planner and report viewer.
type Screen = { to: string; label: string; roles: string[]; exact?: boolean };

const LINKS: Screen[] = [
  { to: "/warehouse", label: "Depo", roles: ["WAREHOUSE_OPERATOR"] },
  { to: "/shipments", label: "Sevkiyat", roles: ["LOGISTICS_PLANNER"] },
  { to: "/routes", label: "Rota", roles: ["DISPATCHER"] },
  { to: "/reports", label: "Rapor", roles: ["LOGISTICS_PLANNER", "REPORT_VIEWER"] },
  { to: "/admin", label: "Yönetim", roles: ["TENANT_ADMIN"] },
];

function useCanOpen(): (screen: Screen) => boolean {
  const { allowed, holdsRole } = useSession();
  return (screen) => (screen.exact ? holdsRole(...screen.roles) : allowed(...screen.roles));
}

/** Opens its screen only for the roles in LINKS; anyone else goes to their first screen. */
export function RequireScreen({ path }: { path: string }) {
  const canOpen = useCanOpen();
  const screen = LINKS.find((item) => item.to === path);
  if (!screen || !canOpen(screen)) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}

export function NoScreen() {
  return (
    <main className="page">
      <p className="empty">Bu hesabın açabileceği bir ekran yok. Kiracı yöneticinizden bir rol isteyin.</p>
    </main>
  );
}

export function RequireAuth() {
  const { session } = useSession();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}

export function HomeRedirect() {
  const canOpen = useCanOpen();
  const link = LINKS.find(canOpen);
  return link ? <Navigate to={link.to} replace /> : <NoScreen />;
}

export function Shell() {
  const { session, signOut } = useSession();
  const canOpen = useCanOpen();
  const [trace, setTrace] = useState<TraceRecord | null>(() => readLastTrace());

  useEffect(() => subscribeTrace(() => setTrace(readLastTrace())), []);

  if (!session) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="shell">
      <header className="top">
        <strong>FF Logistics</strong>
        <nav>
          {LINKS.filter(canOpen).map((link) => (
            <NavLink key={link.to} to={link.to}>{link.label}</NavLink>
          ))}
        </nav>
        <div className="who">
          {trace ? (
            <a
              className="badge trace-link"
              href={jaegerTraceUrl(trace.traceId)}
              target="_blank"
              rel="noreferrer"
              title={`Son W3C izi: ${trace.method} ${trace.path} (${trace.traceId})`}
            >
              İz: {trace.traceId.slice(0, 8)} ↗
            </a>
          ) : (
            <a
              className="badge trace-link"
              href={jaegerHomeUrl()}
              target="_blank"
              rel="noreferrer"
              title="Jaeger dağıtık izleme ekranını aç (:16686)"
            >
              Jaeger ↗
            </a>
          )}
          {session.tenantId && <span className="badge">{session.tenantId}</span>}
          <span>{session.username}</span>
          <button type="button" className="text" onClick={() => void signOut()}>Çıkış</button>
        </div>
      </header>
      <Outlet />
    </div>
  );
}
