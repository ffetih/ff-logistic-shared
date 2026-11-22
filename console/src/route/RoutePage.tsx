import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../api/problem";
import { kg, km, minutes, percent } from "../format";
import { Button, DocumentActions, Field, Metric, Notice } from "../ui";
import {
  completeStop,
  createTerritory,
  epodDownloadPath,
  epodUploadPath,
  listTerritories,
  planRoute,
  type PlannedRoute,
  type Territory,
} from "./api";
import { RouteMap } from "./RouteMap";

type StopDraft = {
  stopCode: string;
  customerName: string;
  latitude: string;
  longitude: string;
  demandWeightKg: string;
  serviceMinutes: string;
  shipmentNumber: string;
};

const DEMO_STOPS: StopDraft[] = [
  { stopCode: "IST-ESN", customerName: "Esenyurt Depot Shop", latitude: "41.03", longitude: "28.67", demandWeightKg: "200", serviceMinutes: "15", shipmentNumber: "" },
  { stopCode: "IST-KDK", customerName: "Kadikoy Market", latitude: "40.99", longitude: "29.03", demandWeightKg: "400", serviceMinutes: "15", shipmentNumber: "" },
];

export function RoutePage() {
  const [territories, setTerritories] = useState<Territory[]>([]);
  const [territoryCode, setTerritoryCode] = useState("IST-EU");
  const [stops, setStops] = useState<StopDraft[]>(DEMO_STOPS);
  const [plan, setPlan] = useState<PlannedRoute | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [pending, setPending] = useState(false);
  const [receivedBy, setReceivedBy] = useState("Dock 2, Route Driver");

  async function reload() {
    const next = await listTerritories();
    setTerritories(next);
    if (next.length > 0 && !next.some((item) => item.territoryCode === territoryCode)) {
      setTerritoryCode(next[0].territoryCode);
    }
  }

  useEffect(() => {
    reload().catch((caught: unknown) => setError(text(caught)));
  }, []);

  async function submitPlan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    setError("");
    try {
      const next = await planRoute({
        territoryCode,
        vehiclePlate: String(data.get("vehiclePlate")),
        depotLatitude: Number(data.get("depotLatitude")),
        depotLongitude: Number(data.get("depotLongitude")),
        vehicleCapacityKg: Number(data.get("vehicleCapacityKg")),
        averageSpeedKmh: Number(data.get("averageSpeedKmh")),
        stops: stops.map((stop) => ({
          stopCode: stop.stopCode,
          customerName: stop.customerName,
          latitude: Number(stop.latitude),
          longitude: Number(stop.longitude),
          demandWeightKg: Number(stop.demandWeightKg),
          serviceMinutes: Number(stop.serviceMinutes),
          shipmentNumber: stop.shipmentNumber.trim() || undefined,
        })),
      });
      setPlan(next);
      setNote(`${next.routeNumber} sıralandı.`);
    } catch (caught) {
      setError(text(caught));
    } finally {
      setPending(false);
    }
  }

  async function finishStop(stopCode: string) {
    if (!plan) {
      return;
    }
    setError("");
    try {
      const next = await completeStop(plan.id, stopCode, receivedBy.trim());
      setPlan(next);
      setNote(`${stopCode} tamamlandı.`);
    } catch (caught) {
      setError(text(caught));
    }
  }

  function updateStop(index: number, field: keyof StopDraft, value: string) {
    setStops((current) => current.map((stop, i) => (i === index ? { ...stop, [field]: value } : stop)));
  }

  return (
    <main className="page">
      <h1>Rota</h1>
      <Notice>{error}</Notice>
      {note && <p className="note">{note}</p>}
      <div className="split">
        <section className="panel">
          <h2>Bölge</h2>
          <ul className="items">
            {territories.map((territory) => (
              <li key={territory.id}>
                <div className="spread">
                  <strong>{territory.territoryCode}</strong>
                  <button type="button" className="button quiet small" onClick={() => setTerritoryCode(territory.territoryCode)}>
                    Seç
                  </button>
                </div>
                <span>{territory.name} · GLN {territory.hubGln} · {km(territory.coverageRadiusKm)}</span>
              </li>
            ))}
            {territories.length === 0 && <li className="empty">Bölge yok.</li>}
          </ul>
          <TerritoryForm
            onCreated={async () => {
              setNote("Bölge kaydedildi.");
              await reload();
            }}
            onError={setError}
          />
          <h2>Plan</h2>
          <form className="stack" onSubmit={submitPlan}>
            <Field label="Bölge kodu"><input value={territoryCode} onChange={(event) => setTerritoryCode(event.target.value)} required /></Field>
            <Field label="Plaka"><input name="vehiclePlate" required defaultValue="34 FF 0418" /></Field>
            <div className="row">
              <Field label="Depo enlem"><input name="depotLatitude" required inputMode="decimal" defaultValue="41.10" /></Field>
              <Field label="Depo boylam"><input name="depotLongitude" required inputMode="decimal" defaultValue="28.65" /></Field>
            </div>
            <div className="row">
              <Field label="Kapasite kg"><input name="vehicleCapacityKg" required type="number" min={1} defaultValue={3500} /></Field>
              <Field label="Hız km/s"><input name="averageSpeedKmh" required type="number" min={10} max={130} defaultValue={40} /></Field>
            </div>
            {stops.map((stop, index) => (
              <div className="stack" key={index}>
                <div className="row">
                  <Field label="Durak kodu"><input value={stop.stopCode} onChange={(event) => updateStop(index, "stopCode", event.target.value)} required /></Field>
                  <Field label="Müşteri"><input value={stop.customerName} onChange={(event) => updateStop(index, "customerName", event.target.value)} required /></Field>
                </div>
                <div className="row">
                  <Field label="Enlem"><input value={stop.latitude} onChange={(event) => updateStop(index, "latitude", event.target.value)} required /></Field>
                  <Field label="Boylam"><input value={stop.longitude} onChange={(event) => updateStop(index, "longitude", event.target.value)} required /></Field>
                </div>
                <div className="row">
                  <Field label="Yük kg"><input value={stop.demandWeightKg} onChange={(event) => updateStop(index, "demandWeightKg", event.target.value)} required /></Field>
                  <Field label="Hizmet dk"><input value={stop.serviceMinutes} onChange={(event) => updateStop(index, "serviceMinutes", event.target.value)} required /></Field>
                </div>
                <Field label="Sevkiyat numarası">
                  <input value={stop.shipmentNumber} onChange={(event) => updateStop(index, "shipmentNumber", event.target.value)} />
                </Field>
              </div>
            ))}
            <Button type="submit" disabled={pending}>Sırala</Button>
          </form>
        </section>
        <section className="panel">
          <h2>Plan</h2>
          {!plan && <p className="empty">Henüz rota yok.</p>}
          {plan && (
            <>
              <div className="metrics">
                <Metric value={plan.routeNumber} label="Rota" />
                <Metric value={km(plan.totalDistanceKm)} label="Mesafe" />
                <Metric value={minutes(plan.totalMinutes)} label="Süre" />
                <Metric value={percent(plan.capacityUtilizationPct)} label="Doluluk" />
                <Metric value={kg(plan.loadedWeightKg)} label="Yük" />
              </div>
              <RouteMap plan={plan} territories={territories} />
              <Field label="Teslim alan">
                <input value={receivedBy} onChange={(event) => setReceivedBy(event.target.value)} />
              </Field>
              <ul className="items">
                {plan.stops.map((stop) => (
                  <li key={stop.id}>
                    <div className="spread">
                      <strong>{stop.sequenceNo}. {stop.customerName}</strong>
                      <span className="badge">{stop.status}</span>
                    </div>
                    <span>{stop.stopCode} · {kg(stop.demandWeightKg)} · {km(stop.legDistanceKm)} · varış {minutes(stop.arrivalMinutes)}</span>
                    {stop.status !== "COMPLETED" && (
                      <Button type="button" tone="quiet" disabled={!receivedBy.trim()} onClick={() => void finishStop(stop.stopCode)}>
                        Durağı tamamla
                      </Button>
                    )}
                    <DocumentActions
                      label="e-POD"
                      accept="image/png,image/jpeg"
                      uploadUrlPath={epodUploadPath(plan.id, stop.stopCode)}
                      downloadUrlPath={epodDownloadPath(plan.id, stop.stopCode)}
                      onNote={(msg) => { setError(""); setNote(msg); }}
                      onError={setError}
                    />
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      </div>
    </main>
  );
}

function TerritoryForm({ onCreated, onError }: { onCreated: () => Promise<void>; onError: (message: string) => void }) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    try {
      await createTerritory({
        territoryCode: String(data.get("territoryCode")),
        name: String(data.get("name")),
        hubGln: String(data.get("hubGln")),
        centerLatitude: Number(data.get("centerLatitude")),
        centerLongitude: Number(data.get("centerLongitude")),
        coverageRadiusKm: Number(data.get("coverageRadiusKm")),
        activePointCount: Number(data.get("activePointCount")),
        weeklyVisitTarget: Number(data.get("weeklyVisitTarget")),
      });
      setOpen(false);
      await onCreated();
    } catch (caught) {
      onError(text(caught));
    } finally {
      setPending(false);
    }
  }

  if (!open) {
    return <Button type="button" tone="quiet" onClick={() => setOpen(true)}>Yeni bölge</Button>;
  }

  return (
    <form className="stack" onSubmit={submit}>
      <Field label="Kod"><input name="territoryCode" required defaultValue="IST-EU" /></Field>
      <Field label="Ad"><input name="name" required defaultValue="Istanbul Europe" /></Field>
      <Field label="Hub GLN"><input name="hubGln" required defaultValue="8690001000011" /></Field>
      <div className="row">
        <Field label="Enlem"><input name="centerLatitude" required inputMode="decimal" defaultValue="41.05" /></Field>
        <Field label="Boylam"><input name="centerLongitude" required inputMode="decimal" defaultValue="28.85" /></Field>
      </div>
      <div className="row">
        <Field label="Yarıçap km"><input name="coverageRadiusKm" required type="number" min={1} defaultValue={35} /></Field>
        <Field label="Nokta"><input name="activePointCount" required type="number" min={1} defaultValue={420} /></Field>
      </div>
      <Field label="Haftalık ziyaret"><input name="weeklyVisitTarget" required type="number" min={1} defaultValue={840} /></Field>
      <div className="actions">
        <Button type="submit" disabled={pending}>Kaydet</Button>
        <Button type="button" tone="quiet" onClick={() => setOpen(false)}>Vazgeç</Button>
      </div>
    </form>
  );
}

function text(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : "Rota alınamadı.";
}
