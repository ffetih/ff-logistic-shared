import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/problem";
import { useSession } from "../auth/SessionContext";
import { Button, CopyButton, Field, Notice } from "../ui";
import {
  createAsn,
  createBin,
  createWarehouse,
  listAsns,
  listBins,
  listPallets,
  listWarehouses,
  receivePallet,
  type Asn,
  type BinLocation,
  type Pallet,
  type Warehouse,
} from "./api";

export function WarehousePage() {
  const { allowed } = useSession();
  const navigate = useNavigate();
  const canPlanShipment = allowed("LOGISTICS_PLANNER");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [bins, setBins] = useState<BinLocation[]>([]);
  const [asns, setAsns] = useState<Asn[]>([]);
  const [pallets, setPallets] = useState<Pallet[]>([]);
  const [asnId, setAsnId] = useState("");
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [sscc, setSscc] = useState("");

  const selected = warehouses.find((warehouse) => warehouse.id === selectedId) ?? warehouses[0];

  async function reload(warehouseId = selected?.id ?? "") {
    const [nextWarehouses, nextAsns] = await Promise.all([listWarehouses(), listAsns()]);
    setWarehouses(nextWarehouses);
    setAsns(nextAsns);
    const current = nextWarehouses.find((warehouse) => warehouse.id === warehouseId) ?? nextWarehouses[0];
    if (current) {
      setSelectedId(current.id);
      setBins(await listBins(current.id));
    } else {
      setBins([]);
    }
  }

  useEffect(() => {
    reload().catch((caught: unknown) => setError(message(caught)));
  }, []);

  async function chooseWarehouse(id: string) {
    setSelectedId(id);
    setError("");
    try {
      setBins(await listBins(id));
    } catch (caught) {
      setError(message(caught));
    }
  }

  async function showPallets(id: string) {
    setAsnId(id);
    setError("");
    try {
      setPallets(await listPallets(id));
    } catch (caught) {
      setError(message(caught));
    }
  }

  return (
    <main className="page">
      <h1>Depo</h1>
      <Notice>{error}</Notice>
      {note && <p className="note">{note}</p>}
      {sscc && (
        <section className="panel result">
          <div className="spread">
            <div>
              <p className="kicker">Palet numarası (GS1 SSCC-18)</p>
              <p className="sscc">{sscc}</p>
            </div>
            <div className="actions">
              <CopyButton value={sscc} label="SSCC kopyala" />
              {canPlanShipment && (
                <Button type="button" tone="quiet" onClick={() => navigate(`/shipments?sscc=${sscc}`)}>
                  Bu paletle sevkiyat aç →
                </Button>
              )}
            </div>
          </div>
        </section>
      )}
      <div className="split">
        <section className="panel">
          <h2>Depolar</h2>
          <ul className="items">
            {warehouses.map((warehouse) => (
              <li key={warehouse.id}>
                <div className="spread">
                  <strong>{warehouse.code}</strong>
                  <button type="button" className="button quiet small" onClick={() => void chooseWarehouse(warehouse.id)}>
                    Raflar
                  </button>
                </div>
                <span>{warehouse.name}, {warehouse.city} ({warehouse.countryCode})</span>
                <span>
                  GLN {warehouse.gln} · Önek {warehouse.gs1CompanyPrefix} · Kapasite {warehouse.palletCapacity.toLocaleString("tr-TR")} palet
                </span>
              </li>
            ))}
            {warehouses.length === 0 && <li className="empty">Henüz depo yok.</li>}
          </ul>
          <WarehouseForm
            onCreated={async () => {
              setError("");
              setNote("Depo kaydedildi.");
              await reload();
            }}
            onError={setError}
          />
          {selected && (
            <>
              <h2>{selected.code} rafları</h2>
              <ul className="items">
                {bins.map((bin) => (
                  <li key={bin.id}>
                    <strong>{bin.code}</strong>
                    <span>{bin.kind} · {bin.palletCapacity} palet</span>
                  </li>
                ))}
                {bins.length === 0 && <li className="empty">Raf yok.</li>}
              </ul>
              <BinForm
                warehouseId={selected.id}
                onCreated={async () => {
                  setNote("Raf kaydedildi.");
                  setBins(await listBins(selected.id));
                }}
                onError={setError}
              />
            </>
          )}
        </section>
        <section className="panel">
          <h2>ASN ve palet</h2>
          <AsnForm
            warehouses={warehouses}
            onCreated={async (asn) => {
              setNote(`${asn.asnNumber} açıldı.`);
              setAsns(await listAsns());
              setAsnId(asn.id);
              setPallets([]);
            }}
            onError={setError}
          />
          <ul className="items">
            {asns.map((asn) => (
              <li key={asn.id}>
                <div className="spread">
                  <strong>{asn.asnNumber}</strong>
                  <span className="actions">
                    <span className="badge">{asn.status}</span>
                    <button type="button" className="button quiet small" onClick={() => void showPallets(asn.id)}>
                      Paletler
                    </button>
                  </span>
                </div>
                <span>{asn.supplierName} · {asn.expectedArrival}</span>
                {asn.lines.map((line) => (
                  <span key={line.lineNumber}>
                    {line.skuCode} (GTIN {line.gtin}) · {line.receivedQuantity}/{line.expectedQuantity}
                  </span>
                ))}
              </li>
            ))}
            {asns.length === 0 && <li className="empty">ASN yok.</li>}
          </ul>
          {asnId && (
            <ReceiveForm
              asnId={asnId}
              onReceived={async (pallet) => {
                setSscc(pallet.sscc);
                setNote(`${pallet.sscc} rafa kondu.`);
                setPallets(await listPallets(asnId));
                setAsns(await listAsns());
              }}
              onError={setError}
            />
          )}
          {pallets.length > 0 && (
            <ul className="items">
              {pallets.map((pallet) => (
                <li key={pallet.id}>
                  <div className="spread">
                    <strong>{pallet.sscc}</strong>
                    <CopyButton value={pallet.sscc} />
                  </div>
                  <span>{pallet.skuCode} · {pallet.quantity} adet · {pallet.grossWeightKg} kg · raf {pallet.binCode}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  );
}

function WarehouseForm({ onCreated, onError }: { onCreated: () => Promise<void>; onError: (message: string) => void }) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    try {
      await createWarehouse({
        code: String(data.get("code")),
        name: String(data.get("name")),
        gln: String(data.get("gln")),
        gs1CompanyPrefix: String(data.get("gs1CompanyPrefix")),
        countryCode: String(data.get("countryCode")),
        city: String(data.get("city")),
        latitude: Number(data.get("latitude")),
        longitude: Number(data.get("longitude")),
        palletCapacity: Number(data.get("palletCapacity")),
      });
      setOpen(false);
      await onCreated();
    } catch (caught) {
      onError(message(caught));
    } finally {
      setPending(false);
    }
  }

  if (!open) {
    return <Button type="button" tone="quiet" onClick={() => setOpen(true)}>Yeni depo</Button>;
  }

  return (
    <form className="stack" onSubmit={submit}>
      <Field label="Kod"><input name="code" required defaultValue="IST-HDK" /></Field>
      <Field label="Ad"><input name="name" required defaultValue="Istanbul Hadimkoy Hub" /></Field>
      <div className="row">
        <Field label="GLN"><input name="gln" required inputMode="numeric" defaultValue="8690001000011" /></Field>
        <Field label="GS1 önek"><input name="gs1CompanyPrefix" required defaultValue="8690001" /></Field>
      </div>
      <div className="row">
        <Field label="Ülke"><input name="countryCode" required maxLength={2} defaultValue="TR" /></Field>
        <Field label="Şehir"><input name="city" required defaultValue="Istanbul" /></Field>
      </div>
      <div className="row">
        <Field label="Enlem"><input name="latitude" required inputMode="decimal" defaultValue="41.1025" /></Field>
        <Field label="Boylam"><input name="longitude" required inputMode="decimal" defaultValue="28.6512" /></Field>
      </div>
      <Field label="Palet kapasitesi"><input name="palletCapacity" required type="number" min={1} defaultValue={12000} /></Field>
      <div className="actions">
        <Button type="submit" disabled={pending}>Kaydet</Button>
        <Button type="button" tone="quiet" onClick={() => setOpen(false)}>Vazgeç</Button>
      </div>
    </form>
  );
}

function BinForm({
  warehouseId,
  onCreated,
  onError,
}: {
  warehouseId: string;
  onCreated: () => Promise<void>;
  onError: (message: string) => void;
}) {
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    try {
      await createBin(warehouseId, {
        code: String(data.get("code")),
        kind: "RACK",
        palletCapacity: Number(data.get("palletCapacity")),
      });
      await onCreated();
    } catch (caught) {
      onError(message(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <div className="row">
        <Field label="Raf kodu"><input name="code" required defaultValue="A-01-01" /></Field>
        <Field label="Kapasite"><input name="palletCapacity" required type="number" min={1} defaultValue={4} /></Field>
      </div>
      <Button type="submit" disabled={pending}>Raf ekle</Button>
    </form>
  );
}

function AsnForm({
  warehouses,
  onCreated,
  onError,
}: {
  warehouses: Warehouse[];
  onCreated: (asn: Asn) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    try {
      const asn = await createAsn({
        asnNumber: String(data.get("asnNumber")),
        warehouseId: String(data.get("warehouseId")),
        supplierName: String(data.get("supplierName")),
        expectedArrival: String(data.get("expectedArrival")),
        lines: [{
          gtin: String(data.get("gtin")),
          skuCode: String(data.get("skuCode")),
          quantity: Number(data.get("quantity")),
        }],
      });
      await onCreated(asn);
    } catch (caught) {
      onError(message(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <Field label="Depo">
        <select name="warehouseId" required key={warehouses[0]?.id ?? "empty"} defaultValue={warehouses[0]?.id ?? ""}>
          {warehouses.length === 0 && <option value="">Önce depo kaydet</option>}
          {warehouses.map((warehouse) => (
            <option key={warehouse.id} value={warehouse.id}>{warehouse.code}</option>
          ))}
        </select>
      </Field>
      <Field label="ASN numarası"><input name="asnNumber" required defaultValue={`ASN-2026-${Date.now()}`} /></Field>
      <Field label="Tedarikçi"><input name="supplierName" required defaultValue="Anatolia Textiles" /></Field>
      <Field label="Beklenen varış"><input name="expectedArrival" required type="date" defaultValue="2026-11-25" /></Field>
      <div className="row">
        <Field label="GTIN"><input name="gtin" required defaultValue="8690001100018" /></Field>
        <Field label="SKU"><input name="skuCode" required defaultValue="TSHIRT-M-BLUE" /></Field>
      </div>
      <Field label="Adet"><input name="quantity" required type="number" min={1} defaultValue={120} /></Field>
      <Button type="submit" disabled={pending || warehouses.length === 0}>ASN aç</Button>
    </form>
  );
}

function ReceiveForm({
  asnId,
  onReceived,
  onError,
}: {
  asnId: string;
  onReceived: (pallet: Pallet) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    try {
      const pallet = await receivePallet(asnId, {
        gtin: String(data.get("gtin")),
        quantity: Number(data.get("quantity")),
        grossWeightKg: Number(data.get("grossWeightKg")),
        binCode: String(data.get("binCode")),
      });
      await onReceived(pallet);
    } catch (caught) {
      onError(message(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <h2>Palet kabul</h2>
      <div className="row">
        <Field label="GTIN"><input name="gtin" required defaultValue="8690001100018" /></Field>
        <Field label="Raf"><input name="binCode" required defaultValue="A-01-01" /></Field>
      </div>
      <div className="row">
        <Field label="Adet"><input name="quantity" required type="number" min={1} defaultValue={60} /></Field>
        <Field label="Brüt kg"><input name="grossWeightKg" required type="number" min={0.1} step="0.1" defaultValue={180.5} /></Field>
      </div>
      <Button type="submit" disabled={pending}>Paleti kabul et</Button>
    </form>
  );
}

function message(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : "Depo listesi alınamadı.";
}
