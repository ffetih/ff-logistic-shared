export function km(value: number): string {
  return `${value.toLocaleString("tr-TR", { maximumFractionDigits: 1 })} km`;
}

export function minutes(value: number): string {
  return `${value.toLocaleString("tr-TR")} dk`;
}

export function kg(value: number): string {
  return `${value.toLocaleString("tr-TR", { maximumFractionDigits: 1 })} kg`;
}

export function percent(value: number): string {
  return `%${value.toLocaleString("tr-TR", { maximumFractionDigits: 1 })}`;
}
