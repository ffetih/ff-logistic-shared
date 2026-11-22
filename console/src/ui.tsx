import { useRef, useState, type ButtonHTMLAttributes, type ChangeEvent, type ReactNode } from "react";
import { openPresignedDownload, uploadToPresignedUrl } from "./api/documents";
import { ApiError } from "./api/problem";

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}

export function Button({ children, tone = "primary", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "primary" | "quiet" }) {
  return (
    <button className={tone === "quiet" ? "button quiet" : "button"} {...props}>
      {children}
    </button>
  );
}

export function Notice({ children }: { children: ReactNode }) {
  if (!children) {
    return null;
  }
  return <p className="notice" role="alert">{children}</p>;
}

export function Metric({ value, label }: { value: string; label: string }) {
  return (
    <div className="metric">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

export function CopyButton({ value, label = "Kopyala" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard unavailable
    }
  }

  return (
    <button type="button" className="button quiet small" onClick={() => void copy()}>
      {copied ? "Kopyalandı" : label}
    </button>
  );
}

export function DocumentActions({
  label,
  accept,
  uploadUrlPath,
  downloadUrlPath,
  canUpload = true,
  onNote,
  onError,
}: {
  label: string;
  accept: string;
  uploadUrlPath: string;
  downloadUrlPath: string;
  canUpload?: boolean;
  onNote?: (message: string) => void;
  onError: (message: string) => void;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [busy, setBusy] = useState(false);

  async function onFilePicked(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    setBusy(true);
    try {
      await uploadToPresignedUrl(uploadUrlPath, file);
      onNote?.(`${label} yüklendi (${file.name}).`);
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : `${label} yüklenemedi.`);
    } finally {
      setBusy(false);
    }
  }

  async function onDownload() {
    setBusy(true);
    try {
      await openPresignedDownload(downloadUrlPath);
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : `${label} indirilemedi.`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="actions">
      {canUpload && (
        <>
          <input
            ref={inputRef}
            type="file"
            accept={accept}
            hidden
            onChange={(event) => void onFilePicked(event)}
          />
          <button
            type="button"
            className="button quiet small"
            disabled={busy}
            onClick={() => inputRef.current?.click()}
          >
            {label} yükle
          </button>
        </>
      )}
      <button
        type="button"
        className="button quiet small"
        disabled={busy}
        onClick={() => void onDownload()}
      >
        {label} indir
      </button>
    </span>
  );
}
