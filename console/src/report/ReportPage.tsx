import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../api/problem";
import { kg, km } from "../format";
import { Button, Field, Metric, Notice } from "../ui";
import {
  generateMonthlyReport,
  issueSupersetGuestToken,
  listReports,
  readChannels,
  saveChannels,
  type MonthlyReport,
  type ReportChannels,
  type SupersetGuestToken,
} from "./api";
import { SupersetEmbed } from "./SupersetEmbed";

const MAILPIT_URL = "http://localhost:8092";

export function ReportPage() {
  const [reports, setReports] = useState<MonthlyReport[]>([]);
  const [channels, setChannels] = useState<ReportChannels | null>(null);
  const [month, setMonth] = useState(currentMonth);
  const [pending, setPending] = useState(false);
  const [token, setToken] = useState<SupersetGuestToken | null>(null);
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  useEffect(() => {
    listReports().then(setReports).catch((caught: unknown) => setError(text(caught)));
    readChannels().then(setChannels).catch((caught: unknown) => setError(text(caught)));
  }, []);

  async function generate(event: FormEvent) {
    event.preventDefault();
    setError("");
    setPending(true);
    try {
      const report = await generateMonthlyReport(month);
      setReports(await listReports());
      const mail = channels?.emailEnabled
        ? ` Posta ${channels.emailAddress} adresine gider; kutu ${MAILPIT_URL} adresinde.`
        : "";
      setNote(`${report.month} raporu hazır.${mail}`);
    } catch (caught) {
      setError(text(caught));
    } finally {
      setPending(false);
    }
  }

  async function storeChannels(next: ReportChannels) {
    setError("");
    setPending(true);
    try {
      const stored = await saveChannels(next);
      setChannels(stored);
      setNote("Kanallar kaydedildi.");
    } catch (caught) {
      setError(text(caught));
    } finally {
      setPending(false);
    }
  }

  async function openEmbeddedDashboard() {
    setError("");
    try {
      setToken(await issueSupersetGuestToken());
    } catch (caught) {
      setError(text(caught));
    }
  }

  return (
    <main className="page">
      <div className="spread">
        <div>
          <h1>Rapor</h1>
          <p className="lede">Aylık PDF, kiracının teslimat toplamından. Grafik ayrıca gömülü panoda.</p>
        </div>
        <div className="actions">
          <Button type="button" tone="quiet" onClick={() => void openEmbeddedDashboard()}>
            {token ? "Gömülü panoyu yenile" : "Gömülü BI panosu"}
          </Button>
        </div>
      </div>
      <Notice>{error}</Notice>
      {note && <p className="note">{note}</p>}
      <div className="split">
        <section className="panel">
          <h2>Aylık PDF</h2>
          <form className="stack" onSubmit={(event) => void generate(event)}>
            <Field label="Ay">
              <input
                type="month"
                value={month}
                required
                onChange={(event) => setMonth(event.target.value)}
              />
            </Field>
            <Button type="submit" disabled={pending}>PDF üret</Button>
          </form>
          {reports.length === 0 ? (
            <p className="empty">Bu kiracının raporu yok.</p>
          ) : (
            <ul className="items">
              {reports.map((report) => (
                <li key={report.id}>
                  <div className="spread">
                    <strong>{report.month}</strong>
                    <a className="button quiet small" href={report.downloadUrl} target="_blank" rel="noreferrer">
                      PDF
                    </a>
                  </div>
                  <div className="metrics">
                    <Metric value={String(report.deliveredShipments)} label="Teslimat" />
                    <Metric value={km(report.totalDistanceKm)} label="Mesafe" />
                    <Metric value={kg(report.savedCo2eKg)} label="Kurtarılan CO2e" />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="panel">
          <h2>Teslimat kanalları</h2>
          <p className="hint">
            Kayıtlı kanallar bir sonraki yeni raporda kullanılır. Posta{" "}
            <a href={MAILPIT_URL} target="_blank" rel="noreferrer">Mailpit</a> kutusuna düşer.
          </p>
          {channels && (
            <ChannelForm channels={channels} pending={pending} onSave={(next) => void storeChannels(next)} />
          )}
        </section>
      </div>
      {token && (
        <SupersetEmbed
          initialToken={token}
          onClose={() => setToken(null)}
          onError={setError}
        />
      )}
    </main>
  );
}

function ChannelForm({
  channels,
  pending,
  onSave,
}: {
  channels: ReportChannels;
  pending: boolean;
  onSave: (channels: ReportChannels) => void;
}) {
  const [emailEnabled, setEmailEnabled] = useState(channels.emailEnabled);
  const [emailAddress, setEmailAddress] = useState(channels.emailAddress ?? "");
  const [webhookEnabled, setWebhookEnabled] = useState(channels.webhookEnabled);
  const [webhookUrl, setWebhookUrl] = useState(channels.webhookUrl ?? "");

  function submit(event: FormEvent) {
    event.preventDefault();
    onSave({
      emailEnabled,
      emailAddress: emailAddress.trim() || null,
      webhookEnabled,
      webhookUrl: webhookUrl.trim() || null,
    });
  }

  return (
    <form className="stack" onSubmit={submit}>
      <label className="check">
        <input type="checkbox" checked={emailEnabled} onChange={(event) => setEmailEnabled(event.target.checked)} />
        E-posta
      </label>
      <Field label="Adres">
        <input
          type="email"
          value={emailAddress}
          placeholder="planner@ff-eu-freight.test"
          onChange={(event) => setEmailAddress(event.target.value)}
        />
      </Field>
      <label className="check">
        <input type="checkbox" checked={webhookEnabled} onChange={(event) => setWebhookEnabled(event.target.checked)} />
        Webhook
      </label>
      <Field label="Adres">
        <input
          type="url"
          value={webhookUrl}
          placeholder="https://hooks.example/report"
          onChange={(event) => setWebhookUrl(event.target.value)}
        />
      </Field>
      <Button type="submit" disabled={pending}>Kanalları kaydet</Button>
    </form>
  );
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function text(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : "Rapor alınamadı.";
}
