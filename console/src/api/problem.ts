export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function problemMessage(response: Response): Promise<string> {
  const text = await response.text();
  if (text) {
    try {
      const body: unknown = JSON.parse(text);
      if (body && typeof body === "object" && "detail" in body && typeof body.detail === "string" && body.detail) {
        return body.detail;
      }
    } catch {
      return text;
    }
  }
  if (response.status === 401) {
    return "Oturum geçersiz.";
  }
  if (response.status === 403) {
    return "Bu işlem için yetkin yok.";
  }
  return `İstek başarısız (${response.status}).`;
}
