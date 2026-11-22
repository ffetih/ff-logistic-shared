import { useEffect, useState, type FormEvent } from "react";
import { jaegerServiceUrl, jaegerTraceUrl, readLastTrace, subscribeTrace, type TraceRecord } from "../api/client";
import { ApiError } from "../api/problem";
import { useSession } from "../auth/SessionContext";
import { Button, CopyButton, Field, Notice } from "../ui";
import {
  ASSIGNABLE_ROLES,
  createIntegrationClient,
  createUser,
  type CreatedIntegrationClient,
  type CreatedUser,
} from "./api";

const TRACED_SERVICES = [
  { id: "logistics-app", label: "Lojistik uygulaması" },
  { id: "identity-service", label: "Kimlik" },
  { id: "analytics-loader", label: "Analitik yükleyici" },
  { id: "reporting", label: "Raporlama" },
  { id: "warehousing", label: "Depo servisi" },
  { id: "transportation", label: "Sevkiyat servisi" },
  { id: "routing", label: "Rota servisi" },
];

export function AdminPage() {
  const { session } = useSession();
  const [createdUsers, setCreatedUsers] = useState<CreatedUser[]>([]);
  const [issuedClient, setIssuedClient] = useState<CreatedIntegrationClient | null>(null);
  const [trace, setTrace] = useState<TraceRecord | null>(() => readLastTrace());
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  useEffect(() => subscribeTrace(() => setTrace(readLastTrace())), []);

  return (
    <main className="page">
      <h1>Yönetim</h1>
      <p className="lede">
        {session?.tenantId} kiracısına yeni kullanıcı veya B2B entegrasyon istemcisi ekle (BR-SEC-04, BR-SEC-05).
      </p>
      <Notice>{error}</Notice>
      {note && <p className="note">{note}</p>}
      {issuedClient && (
        <section className="panel result stack">
          <div className="spread">
            <div>
              <p className="kicker">B2B Entegrasyon İstemcisi ({issuedClient.tenantId})</p>
              <h2>{issuedClient.clientId}</h2>
            </div>
            <Button type="button" tone="quiet" onClick={() => setIssuedClient(null)}>Kapat</Button>
          </div>
          <p className="hint">
            Gizli anahtar yalnızca bir kez gösterilir. Müşteri sistemi <code>POST /sessions/client</code> ile belirteç alır.
          </p>
          <div className="spread code-row">
            <code>{issuedClient.clientSecret}</code>
            <div className="actions">
              <CopyButton value={issuedClient.clientId} label="Client ID kopyala" />
              <CopyButton value={issuedClient.clientSecret} label="Secret kopyala" />
            </div>
          </div>
        </section>
      )}
      <div className="split">
        <section className="panel">
          <h2>Yeni kullanıcı</h2>
          <CreateUserForm
            onCreated={(user) => {
              setError("");
              setNote(`${user.username} (${user.tenantId}) oluşturuldu.`);
              setCreatedUsers([user, ...createdUsers]);
            }}
            onError={setError}
          />
          {createdUsers.length > 0 && (
            <ul className="items">
              {createdUsers.map((user) => (
                <li key={user.id}>
                  <div className="spread">
                    <strong>{user.username}</strong>
                    <span className="badge">{user.tenantId}</span>
                  </div>
                  <span>Roller: {user.roles.join(", ")}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="panel">
          <h2>B2B entegrasyon istemcisi</h2>
          <p className="hint">
            Müşteri ERP veya partner-gateway bağlantısı için parola gerektirmeyen OAuth2 <code>client_credentials</code> hesabı üretir (yalnızca <code>LOGISTICS_PLANNER</code> yetkisi verilir).
          </p>
          <CreateClientForm
            tenantId={session?.tenantId ?? ""}
            onCreated={(client) => {
              setError("");
              setNote(`${client.clientId} istemcisi oluşturuldu.`);
              setIssuedClient(client);
            }}
            onError={setError}
          />
        </section>
      </div>
      <section className="panel stack">
        <div className="spread">
          <div>
            <h2>Dağıtık izleme (OpenTelemetry · Jaeger)</h2>
            <p className="hint">
              Konsoldan çıkan her API isteği W3C <code>traceparent</code> başlığı taşır; HTTP, Kafka ve outbox adımları aynı <code>traceId</code> altında toplanır.
            </p>
          </div>
          {trace && (
            <div className="actions">
              <CopyButton value={trace.traceId} label="Trace ID kopyala" />
              <a
                className="button quiet"
                href={jaegerTraceUrl(trace.traceId)}
                target="_blank"
                rel="noreferrer"
              >
                Son izi aç ({trace.traceId.slice(0, 8)}) ↗
              </a>
            </div>
          )}
        </div>
        {trace && (
          <p className="hint">
            Son istek: <code>{trace.method} {trace.path}</code> · <code>{trace.traceId}</code>
          </p>
        )}
        <div className="actions">
          {TRACED_SERVICES.map((service) => (
            <a
              key={service.id}
              className="button quiet small"
              href={jaegerServiceUrl(service.id)}
              target="_blank"
              rel="noreferrer"
            >
              {service.label} ↗
            </a>
          ))}
        </div>
      </section>
    </main>
  );
}

function CreateUserForm({
  onCreated,
  onError,
}: {
  onCreated: (user: CreatedUser) => void;
  onError: (message: string) => void;
}) {
  const [roles, setRoles] = useState<string[]>(["WAREHOUSE_OPERATOR", "REPORT_VIEWER"]);
  const [pending, setPending] = useState(false);

  function toggleRole(role: string) {
    setRoles(roles.includes(role) ? roles.filter((r) => r !== role) : [...roles, role]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (roles.length === 0) {
      onError("En az bir rol seçilmelidir.");
      return;
    }
    const form = event.currentTarget;
    const data = new FormData(form);
    const firstName = String(data.get("firstName") ?? "").trim();
    const lastName = String(data.get("lastName") ?? "").trim();
    setPending(true);
    try {
      const created = await createUser({
        username: String(data.get("username")).trim(),
        email: String(data.get("email")).trim(),
        password: String(data.get("password")),
        roles,
        ...(firstName ? { firstName } : {}),
        ...(lastName ? { lastName } : {}),
      });
      form.reset();
      onCreated(created);
    } catch (caught) {
      onError(text(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <div className="row">
        <Field label="Kullanıcı adı"><input name="username" required placeholder="ayse.yilmaz" /></Field>
        <Field label="E-posta"><input name="email" type="email" required placeholder="ayse.yilmaz@example.com" /></Field>
      </div>
      <div className="row">
        <Field label="Ad (isteğe bağlı)"><input name="firstName" placeholder="Ayşe" /></Field>
        <Field label="Soyad (isteğe bağlı)"><input name="lastName" placeholder="Yılmaz" /></Field>
      </div>
      <Field label="Geçici parola"><input name="password" type="password" required minLength={8} /></Field>
      <div className="stack">
        <span className="hint">Roller</span>
        {ASSIGNABLE_ROLES.map((role) => (
          <label className="check" key={role.id}>
            <input
              type="checkbox"
              checked={roles.includes(role.id)}
              onChange={() => toggleRole(role.id)}
            />
            {role.label}
          </label>
        ))}
      </div>
      <Button type="submit" disabled={pending}>Kullanıcıyı kaydet</Button>
    </form>
  );
}

function CreateClientForm({
  tenantId,
  onCreated,
  onError,
}: {
  tenantId: string;
  onCreated: (client: CreatedIntegrationClient) => void;
  onError: (message: string) => void;
}) {
  const [name, setName] = useState("erp");
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    try {
      onCreated(await createIntegrationClient(name.trim()));
    } catch (caught) {
      onError(text(caught));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <Field label="Kısa sistem adı (2–41 küçük harf, rakam veya tire)">
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          required
          pattern="[a-z0-9][a-z0-9\-]{1,40}"
          placeholder="ör. erp veya sap- adapter"
        />
      </Field>
      <p className="hint">
        Oluşacak kimlik: <code>integration-{tenantId || "<tenant>"}.{name || "<ad>"}</code>
      </p>
      <Button type="submit" disabled={pending}>Entegrasyon istemcisi üret</Button>
    </form>
  );
}

function text(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : "İşlem tamamlanamadı.";
}
