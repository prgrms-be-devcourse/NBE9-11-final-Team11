"use client"

import { useMemo, useState, useEffect } from "react"
import { ArrowDownUp, Info } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { useStore } from "@/lib/store"
import {
  CURRENCY_META,
  RATES,
  exchangeFee,
  formatKRW,
  formatCurrency,
  krwPerUnit,
  type CurrencyCode,
} from "@/lib/fx-data"
import { apiRequest } from "@/lib/api"

const FX_CODES: Exclude<CurrencyCode, "KRW">[] = ["USD", "JPY", "EUR", "CNY"]

export default function ExchangePage() {
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [target, setTarget] = useState<Exclude<CurrencyCode, "KRW">>("USD")
  const [krwInput, setKrwInput] = useState("500000")
  const [quote, setQuote] = useState<any>(null)
  const [loadingQuote, setLoadingQuote] = useState(false)
  const [quoteError, setQuoteError] = useState<string>("")

  const krwAmount = Number(krwInput.replace(/[^\d]/g, "")) || 0

  const fetchBalance = async () => {
    try {
      const data = await apiRequest<{ totalKrw: number }>("GET", "/api/v1/wallets")
      setKrwBalance(data.totalKrw || 0)
    } catch (err) {
      console.error(err)
    }
  }

  useEffect(() => {
    fetchBalance()
  }, [])

  useEffect(() => {
    if (krwAmount <= 0) {
      setQuote(null)
      setQuoteError("")
      return
    }

    const timer = setTimeout(async () => {
      setLoadingQuote(true)
      setQuoteError("")
      try {
        const data = await apiRequest<any>(
          "GET",
          `/api/v1/wallets/exchange/quote?fromCurrency=KRW&toCurrency=${target}&amount=${krwAmount}`
        )
        setQuote(data)
      } catch (err: any) {
        console.error(err)
        setQuoteError(err.message || "해당 통화의 환전 정보를 가져올 수 없습니다.")
        setQuote(null)
      } finally {
        setLoadingQuote(false)
      }
    }, 400)

    return () => clearTimeout(timer)
  }, [krwAmount, target])

  const appliedRate = quote ? quote.exchangeRate : 0
  const netKRW = quote ? quote.fromAmount : krwAmount
  const fee = quote ? quote.fee : 0
  const totalDeduct = quote ? quote.totalAmount : krwAmount
  const received = quote ? quote.toAmount : 0

  async function submit() {
    if (krwAmount <= 0) return toast.error("환전할 금액을 입력하세요.")
    if (quoteError) return toast.error(quoteError)
    if (!quote || !quote.quoteId) return toast.error("견적을 불러오는 중이거나 실패했습니다.")
    if (totalDeduct > krwBalance) return toast.error("KRW 잔액이 부족합니다.")

    try {
      const res = await apiRequest<any>("POST", "/api/v1/wallets/exchange", {
        quoteId: quote.quoteId,
      })
      toast.success(`${formatCurrency(res.toAmount, res.toCurrency)} 환전이 완료되었습니다.`)
      setKrwInput("")
      setQuote(null)
      fetchBalance()
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "환전에 실패했습니다.")
    }
  }

  return (
    <AppShell title="환전">
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        {/* Converter */}
        <Card className="p-5">
          <h2 className="text-base font-bold">통화 환전</h2>
          <p className="mt-1 text-sm text-muted-foreground">KRW를 외화로 환전합니다.</p>

          <div className="mt-5 space-y-3">
            {/* From */}
            <div className="rounded-2xl border border-border bg-secondary/40 p-4">
              <div className="flex items-center justify-between">
                <Label className="text-xs text-muted-foreground">보내는 금액</Label>
                <span className="text-xs text-muted-foreground tabular-nums">
                  잔액 {formatKRW(krwBalance)}
                </span>
              </div>
              <div className="mt-2 flex items-center gap-3">
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="text-xl" aria-hidden>
                    {CURRENCY_META.KRW.flag}
                  </span>
                  KRW
                </span>
                <Input
                  inputMode="numeric"
                  value={krwInput ? Number(krwInput).toLocaleString("ko-KR") : ""}
                  onChange={(e) => setKrwInput(e.target.value.replace(/[^\d]/g, ""))}
                  className="border-0 bg-transparent text-right text-2xl font-bold tabular-nums shadow-none focus-visible:ring-0"
                  placeholder="0"
                />
              </div>
            </div>

            <div className="flex justify-center">
              <span className="flex size-9 items-center justify-center rounded-full border border-border bg-card">
                <ArrowDownUp className="size-4 text-primary" />
              </span>
            </div>

            {/* To */}
            <div className="rounded-2xl border border-border bg-secondary/40 p-4">
              <Label className="text-xs text-muted-foreground">받는 금액 (예상)</Label>
              <div className="mt-2 flex items-center gap-3">
                <Select value={target} onValueChange={(v) => setTarget(v as Exclude<CurrencyCode, "KRW">)}>
                  <SelectTrigger className="w-32">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {FX_CODES.map((c) => (
                      <SelectItem key={c} value={c}>
                        {CURRENCY_META[c].flag} {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="flex-1 text-right text-2xl font-bold tabular-nums">
                  {received.toLocaleString("ko-KR", {
                    minimumFractionDigits: target === "JPY" ? 0 : 2,
                    maximumFractionDigits: target === "JPY" ? 0 : 2,
                  })}
                </p>
              </div>
            </div>
          </div>

          {/* Quick amounts */}
          <div className="mt-4 flex flex-wrap gap-2">
            {[100000, 500000, 1000000].map((q) => (
              <Button key={q} variant="outline" size="sm" onClick={() => setKrwInput(String(q))}>
                +{(q / 10000).toLocaleString("ko-KR")}만
              </Button>
            ))}
          </div>

          <Button className="mt-5 w-full" size="lg" onClick={submit}>
            환전하기
          </Button>
        </Card>

        {/* Summary */}
        <div className="space-y-4">
          <Card className="p-5">
            <h3 className="text-sm font-semibold">환전 요약</h3>
            <dl className="mt-3 space-y-2.5 text-sm">
              <div className="flex justify-between">
                <dt className="text-muted-foreground">적용 환율</dt>
                <dd className="font-medium tabular-nums">
                  {loadingQuote ? (
                    "계산 중..."
                  ) : quoteError ? (
                    <span className="text-destructive text-xs">{quoteError}</span>
                  ) : appliedRate > 0 ? (
                    `₩${appliedRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })} / ${target}`
                  ) : (
                    `₩${(RATES[target]?.rate || 0).toLocaleString("ko-KR")} / ${target}`
                  )}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-muted-foreground">환전 금액</dt>
                <dd className="font-medium tabular-nums">{formatKRW(netKRW)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-muted-foreground">수수료</dt>
                <dd className="font-medium tabular-nums">{formatKRW(fee)}</dd>
              </div>
              <Separator />
              <div className="flex justify-between">
                <dt className="font-semibold">총 출금액</dt>
                <dd className="font-bold tabular-nums">{formatKRW(totalDeduct)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="font-semibold text-primary">받는 금액</dt>
                <dd className="font-bold tabular-nums text-primary">{formatCurrency(received, target)}</dd>
              </div>
            </dl>
          </Card>

          <Card className="flex gap-3 bg-secondary/40 p-4">
            <Info className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <p className="text-xs leading-relaxed text-muted-foreground">
              환율은 실시간으로 변동될 수 있습니다. 원하는 환율에 도달하면 자동으로 환전되도록 예약 기능을 이용해보세요.
            </p>
          </Card>
        </div>
      </div>
    </AppShell>
  )
}
