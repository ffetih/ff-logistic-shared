import { api } from "../api/client";

export type Warehouse = {
  id: string;
  code: string;
  name: string;
  gln: string;
  gs1CompanyPrefix: string;
  countryCode: string;
  city: string;
  latitude: number;
  longitude: number;
  palletCapacity: number;
};

export type BinLocation = {
  id: string;
  warehouseId: string;
  code: string;
  kind: "RACK" | "FLOOR" | "STAGING";
  palletCapacity: number;
};

export type AsnLine = {
  lineNumber: number;
  gtin: string;
  skuCode: string;
  expectedQuantity: number;
  receivedQuantity: number;
};

export type Asn = {
  id: string;
  asnNumber: string;
  warehouseId: string;
  supplierName: string;
  expectedArrival: string;
  status: string;
  lines: AsnLine[];
};

export type Pallet = {
  id: string;
  sscc: string;
  asnNumber: string;
  asnStatus: string;
  gtin: string;
  skuCode: string;
  quantity: number;
  grossWeightKg: number;
  warehouseId: string;
  binCode: string;
};

export function listWarehouses(): Promise<Warehouse[]> {
  return api("/api/v1/wms/warehouses");
}

export function createWarehouse(body: {
  code: string;
  name: string;
  gln: string;
  gs1CompanyPrefix: string;
  countryCode: string;
  city: string;
  latitude: number;
  longitude: number;
  palletCapacity: number;
}): Promise<Warehouse> {
  return api("/api/v1/wms/warehouses", { method: "POST", body });
}

export function listBins(warehouseId: string): Promise<BinLocation[]> {
  return api(`/api/v1/wms/warehouses/${warehouseId}/bins`);
}

export function createBin(warehouseId: string, body: {
  code: string;
  kind: BinLocation["kind"];
  palletCapacity: number;
}): Promise<BinLocation> {
  return api(`/api/v1/wms/warehouses/${warehouseId}/bins`, { method: "POST", body });
}

export function listAsns(): Promise<Asn[]> {
  return api("/api/v1/wms/asns");
}

export function createAsn(body: {
  asnNumber: string;
  warehouseId: string;
  supplierName: string;
  expectedArrival: string;
  lines: { gtin: string; skuCode: string; quantity: number }[];
}): Promise<Asn> {
  return api("/api/v1/wms/asns", { method: "POST", body });
}

export function listPallets(asnId: string): Promise<Pallet[]> {
  return api(`/api/v1/wms/asns/${asnId}/pallets`);
}

export function receivePallet(asnId: string, body: {
  gtin: string;
  quantity: number;
  grossWeightKg: number;
  binCode: string;
}): Promise<Pallet> {
  return api(`/api/v1/wms/asns/${asnId}/pallets`, { method: "POST", body });
}
