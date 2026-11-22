import { useState } from "react";
import { ApiError } from "../api/problem";
import { Button, Notice } from "../ui";
import { issueSupersetGuestToken, type SupersetGuestToken } from "./api";
import { SupersetEmbed } from "./SupersetEmbed";

export function ReportPage() {
  const [token, setToken] = useState<SupersetGuestToken | null>(null);
  const [error, setError] = useState("");

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
          <p className="lede">Aylık teslimatlar. Kiracıyı guest token söyler.</p>
        </div>
        <div className="actions">
          <Button type="button" tone="quiet" onClick={() => void openEmbeddedDashboard()}>
            {token ? "Gömülü panoyu yenile" : "Gömülü BI panosu"}
          </Button>
        </div>
      </div>
      <Notice>{error}</Notice>
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

function text(caught: unknown): string {
  return caught instanceof ApiError ? caught.message : "Pano açılamadı.";
}
