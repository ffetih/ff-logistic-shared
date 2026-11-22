import { api } from "../api/client";

export type Territory = {
  id: string;
  territoryCode: string;
  name: string;
  hubGln: string;
  centerLatitude: number;
  centerLongitude: number;
  coverageRadiusKm: number;
  activePointCount: number;
  weeklyVisitTarget: number;
  workloadBalanceScore: number;
};

export type Stop = {
  id: string;
  sequenceNo: number;
  stopCode: string;
  customerName: string;
  latitude: number;
  longitude: number;
  demandWeightKg: number;
  serviceMinutes: number;
  legDistanceKm: number;
  arrivalMinutes: number;
  shipmentNumber: string | null;
  status: string;
  receivedBy: string | null;
};

export type PlannedRoute = {
  id: string;
  routeNumber: string;
  territoryCode: string;
  vehiclePlate: string;
  depotLatitude: number;
  depotLongitude: number;
  vehicleCapacityKg: number;
  loadedWeightKg: number;
  capacityUtilizationPct: number;
  totalDistanceKm: number;
  totalMinutes: number;
  stops: Stop[];
  /** Road geometry when a later item has it. Empty: the map draws straight lines. */
  roadLegs?: [number, number][][];
};

export function listTerritories(): Promise<Territory[]> {
  return api("/api/v1/geo/territories");
}

export function createTerritory(body: {
  territoryCode: string;
  name: string;
  hubGln: string;
  centerLatitude: number;
  centerLongitude: number;
  coverageRadiusKm: number;
  activePointCount: number;
  weeklyVisitTarget: number;
}): Promise<Territory> {
  return api("/api/v1/geo/territories", { method: "POST", body });
}

export function planRoute(body: {
  territoryCode: string;
  vehiclePlate: string;
  depotLatitude: number;
  depotLongitude: number;
  vehicleCapacityKg: number;
  averageSpeedKmh: number;
  stops: {
    stopCode: string;
    customerName: string;
    latitude: number;
    longitude: number;
    demandWeightKg: number;
    serviceMinutes: number;
    readyMinutes?: number;
    dueMinutes?: number;
    shipmentNumber?: string;
  }[];
}): Promise<PlannedRoute> {
  return api("/api/v1/geo/routes", { method: "POST", body });
}

export function completeStop(routeId: string, stopCode: string, receivedBy: string): Promise<PlannedRoute> {
  return api(`/api/v1/geo/routes/${routeId}/stops/${encodeURIComponent(stopCode)}/completion`, {
    method: "POST",
    body: { receivedBy },
  });
}

export function epodUploadPath(routeId: string, stopCode: string): string {
  return `/api/v1/geo/routes/${routeId}/stops/${encodeURIComponent(stopCode)}/epod/upload-link`;
}

export function epodDownloadPath(routeId: string, stopCode: string): string {
  return `/api/v1/geo/routes/${routeId}/stops/${encodeURIComponent(stopCode)}/epod/download-link`;
}
