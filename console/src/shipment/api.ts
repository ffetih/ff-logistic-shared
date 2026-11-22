import { api } from "../api/client";

export const MODES = ["ROAD", "RAIL", "SEA_RORO", "INLAND_WATERWAY", "AIR"] as const;
export type Mode = (typeof MODES)[number];

export const INCOTERMS = ["EXW", "FCA", "CPT", "CIP", "DAP", "DPU", "DDP", "FAS", "FOB", "CFR", "CIF"] as const;
export type Incoterm = (typeof INCOTERMS)[number];

export const LIFECYCLE = ["PLANNED", "PICKED_UP", "DEPARTED", "DELIVERED"] as const;
export type Status = (typeof LIFECYCLE)[number];

export const STATUS_LABEL: Record<Status, string> = {
  PLANNED: "Planlandı",
  PICKED_UP: "Teslim alındı",
  DEPARTED: "Yola çıktı",
  DELIVERED: "Teslim edildi",
};

export type Leg = {
  sequenceNo: number;
  mode: Mode;
  origin: string;
  destination: string;
  distanceKm: number;
  carrierReference: string;
};

export type Footprint = {
  totalDistanceKm: number;
  co2eKg: number;
  roadOnlyCo2eKg: number;
  savedCo2eKg: number;
  reductionPercent: number;
};

export type Shipment = {
  id: string;
  shipmentNumber: string;
  customerOrderReference: string | null;
  ecmrNumber: string;
  consignorName: string;
  consigneeName: string;
  equipmentCode: string;
  sscc: string;
  incoterm: Incoterm;
  origin: string;
  destination: string;
  grossWeightKg: number;
  status: Status;
  pickedUpAt: string | null;
  departedAt: string | null;
  deliveredAt: string | null;
  receivedBy: string | null;
  footprint: Footprint;
  legs: Leg[];
};

export function before(shipment: Shipment, status: Status): boolean {
  return LIFECYCLE.indexOf(shipment.status) < LIFECYCLE.indexOf(status);
}

export function listShipments(): Promise<Shipment[]> {
  return api("/api/v1/tms/shipments");
}

export function createShipment(body: {
  customerOrderReference?: string;
  consignorName: string;
  consigneeName: string;
  equipmentCode: string;
  sscc: string;
  incoterm: Incoterm;
  origin: string;
  destination: string;
  destinationGln?: string;
  grossWeightKg: number;
  legs: {
    mode: Mode;
    origin: string;
    destination: string;
    distanceKm: number;
    carrierReference: string;
  }[];
}): Promise<Shipment> {
  return api("/api/v1/tms/shipments", { method: "POST", body });
}

export function confirmPickup(id: string): Promise<Shipment> {
  return api(`/api/v1/tms/shipments/${id}/pickup`, { method: "POST" });
}

export function confirmDeparture(id: string): Promise<Shipment> {
  return api(`/api/v1/tms/shipments/${id}/departure`, { method: "POST" });
}

export function confirmDelivery(id: string, receivedBy: string): Promise<Shipment> {
  return api(`/api/v1/tms/shipments/${id}/delivery`, { method: "POST", body: { receivedBy } });
}

export function ecmrUploadPath(id: string): string {
  return `/api/v1/tms/shipments/${id}/ecmr/upload-link`;
}

export function ecmrDownloadPath(id: string): string {
  return `/api/v1/tms/shipments/${id}/ecmr/download-link`;
}
