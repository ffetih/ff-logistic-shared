import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { ApiError } from "../api/problem";
import { Notice, Button, Field } from "../ui";
import { useSession } from "./SessionContext";

// The local test accounts exist only in the developer realm. Vite replaces import.meta.env.DEV with
// false in a production build, so the hint and the prefilled name are removed from the bundle.
const LOCAL_ACCOUNTS = import.meta.env.DEV;

export function LoginPage() {
  const { session, signIn } = useSession();
  const [username, setUsername] = useState(LOCAL_ACCOUNTS ? "nexus.planner" : "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  if (session) {
    return <Navigate to="/" replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setError("");
    try {
      await signIn(username.trim(), password);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Giriş yapılamadı.");
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="login">
      <form className="panel" onSubmit={submit}>
        <h1>FF Logistics</h1>
        <p className="lede">Kiracın, girdiğin hesaptan gelir.</p>
        <Notice>{error}</Notice>
        <Field label="Kullanıcı adı">
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required />
        </Field>
        <Field label="Parola">
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
        </Field>
        <Button type="submit" disabled={pending}>{pending ? "Giriliyor…" : "Giriş"}</Button>
        {LOCAL_ACCOUNTS && <p className="hint">Yerel hesaplar: nexus.planner, geodispatch.operator. Parola fflogistics2026.</p>}
      </form>
    </main>
  );
}
