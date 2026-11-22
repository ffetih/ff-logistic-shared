import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { ApiError } from "../api/problem";
import { kg, km, percent } from "../format";
import { Button, DocumentActions, Field, Notice } from "../ui";
import {
  before,
  confirmDelivery,
  confirmDeparture,
  confirmPickup,
  createShipment,
  ecmrDownloadPath,
  ecmrUploadPath,
  INCOTERMS,
  LIFECYCLE,
  listShipments,
  MODES,
  STATUS_LABEL,
  type Incoterm,
  type Mode,
  type Shipment,
  type Status,
} from "./api";

export function ShipmentPage() {
  const [searchParams] = useSearchParams();
  const initialSscc = searchParams.get("sscc") ?? "106141411234567897";
  const [shipments, setShipments] = useState<Shipment[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<Status | "ALL">("ALL");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  async function reload() {
    setShipments(await listShipments());
  }

  useEffect(() => {
    reload().catch((caught: unknown) => setError(text(caught)));
  }, []);

  function step(shipment: Shipment, call: () => Promise<Shipment>, done: string) {
    setError("");
    setBusyId(shipment.id);
    call()
      .then(async (next) => {
        setNote(`${next.shipmentNumber} ${done}.`);
        await reload();
      })
      .catch((caught: unknown) => setError(text(caught)))
      .finally(() => setBusyId(null));
  }

  const visible = shipments.filter((shipment) => {
    const matchesStatus = statusFilter === "ALL" || shipment.status === statusFilter;
    const needle = query.trim().toLowerCase();
    const matchesQuery = !needle
      || shipment.shipmentNumber.toLowerCase().includes(needle)
      || (shipment.customerOrderReference ?? "").toLowerCase().includes(needle)
      || shipment.sscc.includes(needle);
    return matchesStatus && matchesQuery;
  });

  return (
    <main className="page">
      <h1>Sevkiyat</h1>
      <Notice>{error}</Notice>
      {note && <p className="note">{note}</p>}
      <div className="split">
        <section className="panel">
          <h2>Yeni sevkiyat</h2>
          <CreateForm
            initialSscc={initialSscc}
            onCreated={async (shipment) => {
              setNote(`${shipment.shipmentNumber} planlandı.`);
              await reload();
            }}
            onError={setError}
          />
        </section>
        <section className="panel">
          <h2>Sevkiyatlar</h2>
          <form className="row" onSubmit={(event) => event.preventDefault()}>
            <Field label="Ara">
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="numara, sipariş, SSCC" />
            </Field>
            <Field label="Durum">
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as Status | "ALL")}>
                <option value="ALL">Hepsi</option>
                {LIFECYCLE.map((status) => (
                  <option key={status} value={status}>{STATUS_LABEL[status]}</option>
                ))}
              </select>
            </Field>
          </form>
          <ul className="items">
            {visible.map((shipment) => (
              <li key={shipment.id}>
                <div className="spread">
                  <strong>{shipment.shipmentNumber}</strong>
                  <span className="badge">{STATUS_LABEL[shipment.status]}</span>
                </div>
                <span>{shipment.origin} → {shipment.destination} · {shipment.incoterm} · SSCC {shipment.sscc}</span>
                <span>
                  {kg(shipment.grossWeightKg)} · {km(shipment.footprint.totalDistanceKm)} · CO₂e tasarruf {kg(shipment.footprint.savedCo2eKg)} ({percent(shipment.footprint.reductionPercent)})
                </span>
                <div className="actions">
                  {before(shipment, "PICKED_UP") && (
                    <Button type="button" tone="quiet" disabled={busyId === shipment.id} onClick={() => step(shipment, () => confirmPickup(shipment.id), "teslim alındı")}>
                      Teslim al
                    </Button>
                  )}
                  {before(shipment, "DEPARTED") && (
                    <Button type="button" tone="quiet" disabled={busyId === shipment.id} onClick={() => step(shipment, () => confirmDeparture(shipment.id), "yola çıktı")}>
                      Yola çık
                    </Button>
                  )}
                  {before(shipment, "DELIVERED") && (
                    <DeliverButton
                      disabled={busyId === shipment.id}
                      onDeliver={(receivedBy) => step(shipment, () => confirmDelivery(shipment.id, receivedBy), "teslim edildi")}
                    />
                  )}
                </div>
                <DocumentActions
                  label="e-CMR"
                  accept="application/pdf"
                  uploadUrlPath={ecmrUploadPath(shipment.id)}
                  downloadUrlPath={ecmrDownloadPath(shipment.id)}
                  onNote={(msg) => { setError(""); setNote(msg); }}
                  onError={setError}
                />
              </li>
            ))}
            {visible.length === 0 && <li className="empty">Sevkiyat yok.</li>}
          </ul>
        </section>
      </div>
    </main>
  );
}

function DeliverButton({ disabled, onDeliver }: { disabled: boolean; onDeliver: (receivedBy: string) => void }) {
  const [open, setOpen] = useState(false);
  const [receivedBy, setReceivedBy] = useState("Dock 2, A. Yilmaz");

  if (!open) {
    return <Button type="button" tone="quiet" disabled={disabled} onClick={() => setOpen(true)}>Teslim et</Button>;
  }

  return (
    <span className="actions">
      <input value={receivedBy} onChange={(event) => setReceivedBy(event.target.value)} aria-label="Teslim alan" />
      <Button type="button" disabled={disabled || !receivedBy.trim()} onClick={() => onDeliver(receivedBy.trim())}>Kaydet</Button>
    </span>
  );
}

function CreateForm({
  initialSscc,
  onCreated,
  onError,
}: {
  initialSscc: string;
  onCreated: (shipment: Shipment) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    try {
      const shipment = await createShipment({
        customerOrderReference: String(data.get("customerOrderReference") || "") || undefined,
        consignorName: String(data.get("consignorName")),
        consigneeName: String(data.get("consigneeName")),
        equipmentCode: String(data.get("equipmentCode")),
        sscc: String(data.get("sscc")),
        incoterm: String(data.get("incoterm")) as Incoterm,
        origin: String(data.get("origin")),
        destination: String(data.get("destination")),
        destinationGln: String(data.get("destinationGln") || "") || undefined,
        grossWeightKg: Number(data.get("grossWeightKg")),
        legs: [{
          mode: String(data.get("mode")) as Mode,
          origin: String(data.get("legOrigin")),
          destination: String(data.get("legDestination")),
          distanceKm: Number(data.get("distanceKm")),
          carrierReference: String(data.get("carrierReference")),
        }],
      });
      await onCreated(shipment);
    } catch (caught) {
      onError(text(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <Field label="Sipariş referansı"><input name="customerOrderReference" /></Field>
      <div className="row">
        <Field label="Gönderen"><input name="consignorName" required defaultValue="Rhein Parts GmbH" /></Field>
        <Field label="Alıcı"><input name="consigneeName" required defaultValue="Istanbul Hadimkoy Hub" /></Field>
      </div>
      <div className="row">
        <Field label="Çıkış"><input name="origin" required defaultValue="Cologne Eifeltor" /></Field>
        <Field label="Varış"><input name="destination" required defaultValue="Istanbul Hadimkoy" /></Field>
      </div>
      <div className="row">
        <Field label="Ekipman"><input name="equipmentCode" required defaultValue="CSQU3054383" /></Field>
        <Field label="SSCC"><input name="sscc" required defaultValue={initialSscc} /></Field>
      </div>
      <div className="row">
        <Field label="Incoterm">
          <select name="incoterm" defaultValue="DAP">
            {INCOTERMS.map((term) => <option key={term} value={term}>{term}</option>)}
          </select>
        </Field>
        <Field label="Brüt kg"><input name="grossWeightKg" required type="number" min={0.1} step="0.1" defaultValue={18000} /></Field>
      </div>
      <Field label="Varış GLN"><input name="destinationGln" defaultValue="8690001000011" /></Field>
      <h3>Bacak</h3>
      <div className="row">
        <Field label="Mod">
          <select name="mode" defaultValue="RAIL">
            {MODES.map((mode) => <option key={mode} value={mode}>{mode}</option>)}
          </select>
        </Field>
        <Field label="Mesafe km"><input name="distanceKm" required type="number" min={0.1} step="0.1" defaultValue={1050} /></Field>
      </div>
      <div className="row">
        <Field label="Bacak çıkış"><input name="legOrigin" required defaultValue="Cologne Eifeltor Terminal" /></Field>
        <Field label="Bacak varış"><input name="legDestination" required defaultValue="Trieste" /></Field>
      </div>
      <Field label="Taşıyıcı referansı"><input name="carrierReference" required defaultValue="TXL-41877" /></Field>
      <Button type="submit" disabled={pending}>Planla</Button>
    </form>
  );
}

function text(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : "Sevkiyat alınamadı.";
}
