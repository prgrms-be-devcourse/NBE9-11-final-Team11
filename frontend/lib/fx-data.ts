// Static mock FX data and helpers for the FXFlow simulation platform.

export type CurrencyCode = "USD" | "JPY" | "EUR" | "CNY" | "KRW"

export interface RateInfo {
  code: CurrencyCode
  name: string
  flag: string
  /** KRW per 1 unit of currency (for JPY this is per 100 units, see unit) */
  rate: number
  unit: number
  change: number // percent change
  updatedAt: string
}

export const RATES: Record<Exclude<CurrencyCode, "KRW">, RateInfo> = {
  USD: {
    code: "USD",
    name: "미국 달러",
    flag: "🇺🇸",
    rate: 1387.5,
    unit: 1,
    change: 0.34,
    updatedAt: "방금 전",
  },
  JPY: {
    code: "JPY",
    name: "일본 엔",
    flag: "🇯🇵",
    rate: 912.8,
    unit: 100,
    change: -0.21,
    updatedAt: "방금 전",
  },
  EUR: {
    code: "EUR",
    name: "유로",
    flag: "🇪🇺",
    rate: 1502.3,
    unit: 1,
    change: 0.12,
    updatedAt: "방금 전",
  },
  CNY: {
    code: "CNY",
    name: "중국 위안",
    flag: "🇨🇳",
    rate: 191.4,
    unit: 1,
    change: -0.08,
    updatedAt: "방금 전",
  },
}

export const CURRENCY_META: Record<CurrencyCode, { name: string; flag: string; symbol: string }> = {
  KRW: { name: "대한민국 원", flag: "🇰🇷", symbol: "₩" },
  USD: { name: "미국 달러", flag: "🇺🇸", symbol: "$" },
  JPY: { name: "일본 엔", flag: "🇯🇵", symbol: "¥" },
  EUR: { name: "유로", flag: "🇪🇺", symbol: "€" },
  CNY: { name: "중국 위안", flag: "🇨🇳", symbol: "¥" },
}

/** KRW value of 1 unit (JPY normalized per single yen) */
export function krwPerUnit(code: CurrencyCode): number {
  if (code === "KRW") return 1
  const info = RATES[code]
  return info.rate / info.unit
}

export function formatKRW(amount: number): string {
  return "₩" + Math.floor(amount).toLocaleString("ko-KR")
}

export function floorToTwoDecimals(value: number): number {
  const [whole, decimals = ""] = value.toFixed(12).split(".")
  return Number(`${whole}.${decimals.padEnd(2, "0").slice(0, 2)}`)
}

export function formatCurrency(amount: number, code: CurrencyCode): string {
  const meta = CURRENCY_META[code]
  if (code === "KRW") return formatKRW(amount)
  if (code === "JPY") return meta.symbol + Math.floor(amount).toLocaleString("ko-KR")

  const floored = floorToTwoDecimals(amount)
  return meta.symbol + floored.toLocaleString("ko-KR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 30-day rate history generator (deterministic so it stays stable)
export function rateHistory(code: Exclude<CurrencyCode, "KRW">): { date: string; rate: number }[] {
  const base = krwPerUnit(code) * (code === "JPY" ? 100 : 1)
  const out: { date: string; rate: number }[] = []
  let seed = code.charCodeAt(0) * 13
  for (let i = 29; i >= 0; i--) {
    seed = (seed * 9301 + 49297) % 233280
    const noise = (seed / 233280 - 0.5) * base * 0.04
    const trend = Math.sin((29 - i) / 6) * base * 0.02
    const d = new Date()
    d.setDate(d.getDate() - i)
    out.push({
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      rate: Math.round((base + noise + trend) * 10) / 10,
    })
  }
  return out
}

export const COUNTRIES = [
  { code: "US", name: "미국", currency: "USD" as CurrencyCode, flag: "🇺🇸" },
  { code: "JP", name: "일본", currency: "JPY" as CurrencyCode, flag: "🇯🇵" },
  { code: "EU", name: "유럽연합", currency: "EUR" as CurrencyCode, flag: "🇪🇺" },
  { code: "CN", name: "중국", currency: "CNY" as CurrencyCode, flag: "🇨🇳" },
]

export const REMITTANCE_REASONS = ["가족생활비", "유학경비", "여행경비", "투자", "기타"]

export const KOREAN_BANKS = ["국민은행", "신한은행", "우리은행", "하나은행", "농협은행", "카카오뱅크", "토스뱅크"]

/** Exchange fee: 0.4% of KRW value, min 1,000 KRW */
export function exchangeFee(krwAmount: number): number {
  return Math.max(1000, Math.round(krwAmount * 0.004))
}

/** Remittance fee: flat 5,000 KRW + 0.5% */
export function remittanceFee(krwAmount: number): number {
  return 5000 + Math.round(krwAmount * 0.005)
}

/** Bank comparison: banks typically charge ~1.75% spread + higher fees */
export function bankFee(krwAmount: number): number {
  return 15000 + Math.round(krwAmount * 0.0175)
}

export function sanitizeDecimalInput(value: string, maxDecimals: number = 2): string {
  let clean = value.replace(/[^\d.]/g, "");
  const parts = clean.split(".");
  if (parts.length > 2) {
    clean = parts[0] + "." + parts.slice(1).join("");
  }
  const firstDotIndex = clean.indexOf(".");
  if (firstDotIndex !== -1) {
    const beforeDot = clean.substring(0, firstDotIndex);
    const afterDot = clean.substring(firstDotIndex + 1);
    clean = beforeDot + "." + afterDot.substring(0, maxDecimals);
  }
  return clean;
}
