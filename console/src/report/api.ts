import { api } from "../api/client";

/** The monthly dashboard pinned by provision (S8). */
export const DASHBOARD_ID = "d18e4b70-9a25-4c63-b0f1-7e2a6c5d8349";

/** The browser reaches Superset on the host port, not through this origin. */
export const SUPERSET_URL = "http://localhost:8088";

export type SupersetGuestToken = {
  biEngine: string;
  tenantId: string;
  supersetInstanceUrl: string;
  embeddedDashboardIds: string[];
  embeddedDashboards: { id: string; title: string }[];
  rowLevelSecurityRules: { dataset: string; clause: string }[];
  guestToken: string;
  expiresAt: string;
};

type GuestTokenResponse = {
  token: string;
  tenantId: string;
  dashboardId: string;
  expiresAt: string;
};

export function issueSupersetGuestToken(): Promise<SupersetGuestToken> {
  return api<GuestTokenResponse>("/identity/api/v1/guest-tokens", {
    method: "POST",
    body: { dashboardId: DASHBOARD_ID },
  }).then((body) => ({
    biEngine: "Superset",
    tenantId: body.tenantId,
    supersetInstanceUrl: SUPERSET_URL,
    embeddedDashboardIds: [body.dashboardId],
    embeddedDashboards: [{ id: body.dashboardId, title: "Deliveries per month" }],
    rowLevelSecurityRules: [],
    guestToken: body.token,
    expiresAt: body.expiresAt,
  }));
}
