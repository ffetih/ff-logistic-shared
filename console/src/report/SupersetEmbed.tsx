import { useEffect, useRef, useState } from "react";
import { ApiError } from "../api/problem";
import { Button, CopyButton } from "../ui";
import { issueSupersetGuestToken, type SupersetGuestToken } from "./api";

export function SupersetEmbed({
  initialToken,
  onClose,
  onError,
}: {
  initialToken: SupersetGuestToken;
  onClose: () => void;
  onError: (message: string) => void;
}) {
  const [tokenInfo, setTokenInfo] = useState<SupersetGuestToken>(initialToken);
  const [frameHeight, setFrameHeight] = useState(720);
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const portRef = useRef<MessagePort | null>(null);

  // Every dashboard the token opens; an older server sends ids only, the first being FF ControlTower.
  const dashboards =
    tokenInfo.embeddedDashboards && tokenInfo.embeddedDashboards.length > 0
      ? tokenInfo.embeddedDashboards
      : tokenInfo.embeddedDashboardIds.map((id, i) => ({ id, title: i === 0 ? "FF ControlTower" : `Pano ${i + 1}` }));
  const [selectedId, setSelectedId] = useState(dashboards[0]?.id ?? "");
  const selected = dashboards.find((d) => d.id === selectedId) ?? dashboards[0];
  const dashboardId = selected?.id ?? "";
  const baseUrl = tokenInfo.supersetInstanceUrl.replace(/\/+$/, "");
  const embedUrl = dashboardId ? `${baseUrl}/embedded/${dashboardId}?uiConfig=1` : "";

  function sendGuestToken(port: MessagePort, guestToken: string) {
    port.postMessage({
      switchboardAction: "emit",
      method: "guestToken",
      args: { guestToken },
    });
  }

  function requestScrollSize(port: MessagePort) {
    port.postMessage({
      switchboardAction: "get",
      method: "getScrollSize",
      messageId: `size_${Date.now()}`,
    });
  }

  function handleIframeLoad() {
    const win = iframeRef.current?.contentWindow;
    if (!win || !baseUrl) {
      return;
    }
    portRef.current?.close();
    const channel = new MessageChannel();
    const ourPort = channel.port1;
    portRef.current = ourPort;

    ourPort.onmessage = (event: MessageEvent) => {
      const data = event.data as Record<string, unknown> | null;
      if (!data) {
        return;
      }
      if (
        (data.switchboardAction === "reply" || data.switchboardAction === "response") &&
        data.result &&
        typeof data.result === "object"
      ) {
        const height = (data.result as { height?: unknown }).height;
        if (typeof height === "number" && height > 400) {
          setFrameHeight(Math.min(Math.max(height + 24, 680), 1600));
        }
      }
    };
    ourPort.start();
    win.postMessage(
      { type: "__embedded_comms__", handshake: "port transfer" },
      baseUrl,
      [channel.port2],
    );
    sendGuestToken(ourPort, tokenInfo.guestToken);
    window.setTimeout(() => requestScrollSize(ourPort), 1500);
    window.setTimeout(() => requestScrollSize(ourPort), 3500);
  }

  useEffect(() => {
    const expiresMs = Date.parse(tokenInfo.expiresAt);
    const delayMs = Number.isFinite(expiresMs)
      ? Math.max(expiresMs - Date.now() - 45_000, 30_000)
      : 240_000;

    const timer = window.setTimeout(() => {
      issueSupersetGuestToken()
        .then((fresh) => {
          setTokenInfo(fresh);
          if (portRef.current) {
            sendGuestToken(portRef.current, fresh.guestToken);
          }
        })
        .catch((caught: unknown) => {
          onError(caught instanceof ApiError ? caught.message : "Superset erişim anahtarı yenilenemedi.");
        });
    }, delayMs);

    return () => window.clearTimeout(timer);
  }, [tokenInfo.expiresAt, onError]);

  useEffect(() => {
    return () => {
      portRef.current?.close();
    };
  }, []);

  return (
    <section className="panel result stack">
      <div className="spread">
        <div>
          <p className="kicker">{tokenInfo.biEngine} · Gömülü Pano</p>
          <h2>{selected?.title ?? "Pano"} ({tokenInfo.tenantId})</h2>
        </div>
        <div className="actions">
          {/* Development aids only: a working token on the clipboard, and table names and SQL. */}
          {import.meta.env.DEV && <CopyButton value={tokenInfo.guestToken} label="Guest token kopyala" />}
          <Button type="button" tone="quiet" onClick={onClose}>Kapat</Button>
        </div>
      </div>
      {import.meta.env.DEV && (
        <p className="hint">
          RLS filtreleri: {tokenInfo.rowLevelSecurityRules.map((r) => `${r.dataset}: ${r.clause}`).join(" · ")}
        </p>
      )}
      {dashboards.length > 1 && (
        <div className="tabs" role="tablist" aria-label="Gömülü panolar">
          {dashboards.map((d) => (
            <button
              key={d.id}
              type="button"
              role="tab"
              aria-selected={d.id === dashboardId}
              className={d.id === dashboardId ? "tab active" : "tab"}
              onClick={() => setSelectedId(d.id)}
            >
              {d.title}
            </button>
          ))}
        </div>
      )}
      {embedUrl ? (
        <iframe
          // A new dashboard is a new embedded page: mount it afresh, so the handshake runs again.
          key={dashboardId}
          ref={iframeRef}
          className="superset-frame"
          style={{ height: `${frameHeight}px` }}
          src={embedUrl}
          // The console sends no Referer (nginx.conf); Superset needs this one's origin to check that
          // the console may embed the dashboard (allowed domains, infra/local/superset/provision.py).
          referrerPolicy="origin"
          title={`${selected?.title ?? "Superset"} Superset Dashboard`}
          onLoad={handleIframeLoad}
        />
      ) : (
        <p className="empty">Gömülü pano kimliği yapılandırılmamış.</p>
      )}
    </section>
  );
}
