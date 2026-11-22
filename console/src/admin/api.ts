import { api } from "../api/client";

export const ASSIGNABLE_ROLES = [
  { id: "WAREHOUSE_OPERATOR", label: "Depo operatörü (WAREHOUSE_OPERATOR)" },
  { id: "LOGISTICS_PLANNER", label: "Sevkiyat planlayıcı (LOGISTICS_PLANNER)" },
  { id: "DISPATCHER", label: "Rota planlayıcı (DISPATCHER)" },
  { id: "REPORT_VIEWER", label: "Rapor okuyucu (REPORT_VIEWER)" },
  { id: "TENANT_ADMIN", label: "Kiracı yöneticisi (TENANT_ADMIN)" },
] as const;

export type CreatedUser = {
  id: string;
  username: string;
  tenantId: string;
  roles: string[];
};

export type CreatedIntegrationClient = {
  clientId: string;
  clientSecret: string;
  tenantId: string;
  roles: string[];
};

export function createUser(body: {
  username: string;
  email: string;
  password: string;
  roles: string[];
  firstName?: string;
  lastName?: string;
}): Promise<CreatedUser> {
  return api("/identity/api/v1/users", { method: "POST", body });
}

export function createIntegrationClient(name: string): Promise<CreatedIntegrationClient> {
  return api("/identity/api/v1/integration-clients", { method: "POST", body: { name } });
}
