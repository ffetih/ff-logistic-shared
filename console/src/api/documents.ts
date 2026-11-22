import { api } from "./client";
import { ApiError } from "./problem";

export type DocumentTransfer = {
  url: string;
  method: string;
  validForSeconds: number;
  contentType: string;
  headers: Record<string, string>;
};

export async function uploadToPresignedUrl(uploadUrlPath: string, file: File): Promise<void> {
  const transfer = await api<DocumentTransfer>(uploadUrlPath, { method: "POST" });
  let response: Response;
  try {
    response = await fetch(transfer.url, {
      method: transfer.method || "PUT",
      headers: transfer.headers,
      body: file,
    });
  } catch {
    throw new ApiError("Depolama sunucusuna ulaşılamadı.", 503);
  }
  if (response.status === 412) {
    throw new ApiError("Belge zaten yüklendi ve değiştirilemez (BR-DOC-01).", 409);
  }
  if (!response.ok) {
    throw new ApiError(`Belge yüklenemedi (${response.status}).`, response.status);
  }
}

export async function openPresignedDownload(downloadUrlPath: string): Promise<void> {
  const transfer = await api<DocumentTransfer>(downloadUrlPath);
  window.open(transfer.url, "_blank", "noopener,noreferrer");
}
