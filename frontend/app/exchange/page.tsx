"use client"

import { useState, useEffect } from "react"
import { ArrowDownUp, Info } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { useStore } from "@/lib/store"
import {
  CURRENCY_META,
  formatKRW,
  formatCurrency,
} from "@/lib/fx-data"
import { apiRequest } from "@/lib/api"

export default function ExchangePage() {
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [usdBalance, setUsdBalance] = useState<number>(0)
  const [direction, setDirection] = useState<"KRW_TO_USD" | "USD_TO_KRW">("KRW_TO_USD")
  const [inputAmount, setInputAmount] = useState("10000")
  const [quote, setQuote] = useState<any>(null)
  const [loadingQuote, setLoadingQuote] = useState(false)
  const [quoteError, setQuoteError] = useState<string>("")

  const parsedAmount = Number(inputAmount.replace(/[^\d.]/g, "")) || 0

  const fetchBalances = async () => {
    try {
      const data = await apiRequest<{ totalKrw: number; walletResponseList: { currency: string; balance: number }[] }>(
        "GET",
        "/api/v1/wallets"
      )
      const krw = data.walletResponseList?.find((w) => w.currency === "KRW")?.balance || 0
      const usd = data.walletResponseList?.find((w) => w.currency === "USD")?.balance || 0
      setKrwBalance(krw)
      setUsdBalance(usd)
    } catch (err) {
      console.error(err)
    }
  }

  useEffect(() => {
    fetchBalances()
  }, [])

  useEffect(() => {
    if (parsedAmount <= 0) {
      setQuote(null)
      setQuoteError("")
      return
    }

    const timer = setTimeout(async () => {
      setLoadingQuote(true)
      setQuoteError("")
      try {
        const from = direction === "KRW_TO_USD" ? "KRW" : "USD"
        const to = direction === "KRW_TO_USD" ? "USD" : "KRW"
        const data = await apiRequest<any>(
          "GET",
          `/api/v1/wallets/exchange/quote?fromCurrency=${from}&toCurrency=${to}&amount=${parsedAmount}`
        )
        setQuote(data)
      } catch (err: any) {
        console.error(err)
        setQuoteError(err.message || "환율 정보를 가져올 수 없습니다.")
        setQuote(null)
      } finally {
        setLoadingQuote(false)
      }
    }, 400)

    return () => clearTimeout(timer)
  }, [parsedAmount, direction])

  const appliedRate = quote ? quote.exchangeRate : 0
  const netAmount = quote ? quote.fromAmount : parsedAmount
  const fee = quote ? quote.fee : 0
  const totalDeduct = quote ? quote.totalAmount : parsedAmount
  const received = quote ? quote.toAmount : 0

  const fromCurrency = direction === "KRW_TO_USD" ? "KRW" : "USD"
  const toCurrency = direction === "KRW_TO_USD" ? "USD" : "KRW"
  const currentBalance = direction === "KRW_TO_USD" ? krwBalance : usdBalance

  function handleSwap() {
    setQuote(null)
    setQuoteError("")
    if (direction === "KRW_TO_USD") {
      setDirection("USD_TO_KRW")
      setInputAmount("10") // Default to 500 USD
    } else {
      setDirection("KRW_TO_USD")
      setInputAmount("10000") // Default to 500k KRW
    }
  }

  async function submit() {
    if (parsedAmount <= 0) return toast.error("환전할 금액을 입력하세요.")
    if (quoteError) return toast.error(quoteError)
    if (!quote || !quote.quoteId) return toast.error("견적을 불러오는 중이거나 실패했습니다.")
    if (totalDeduct > currentBalance) return toast.error(`${fromCurrency} 잔액이 부족합니다.`)

    try {
      const res = await apiRequest<any>("POST", "/api/v1/wallets/exchange", {
        quoteId: quote.quoteId,
      })
      toast.success(`${formatCurrency(res.toAmount, res.toCurrency)} 환전이 완료되었습니다.`)
      setInputAmount("")
      setQuote(null)
      fetchBalances()
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
          <p className="mt-1 text-sm text-muted-foreground">실시간 환율 기반으로 외화를 환전합니다.</p>

          <div className="mt-5 space-y-3">
            {/* From */}
            <div className="rounded-2xl border border-border bg-secondary/40 p-4">
              <div className="flex items-center justify-between">
                <Label className="text-xs text-muted-foreground">보내는 금액 ({fromCurrency})</Label>
                <span className="text-xs text-muted-foreground tabular-nums">
                  잔액 {formatCurrency(currentBalance, fromCurrency)}
                </span>
              </div>
              <div className="mt-2 flex items-center gap-3">
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="text-xl" aria-hidden>
                    {CURRENCY_META[fromCurrency].flag}
                  </span>
                  {fromCurrency}
                </span>
                <Input
                  inputMode="decimal"
                  value={inputAmount}
                  onChange={(e) => {
                    const val = e.target.value
                    if (fromCurrency === "KRW") {
                      setInputAmount(val.replace(/[^\d]/g, ""))
                    } else {
                      setInputAmount(val.replace(/[^\d.]/g, ""))
                    }
                  }}
                  className="border-0 bg-transparent text-right text-2xl font-bold tabular-nums shadow-none focus-visible:ring-0"
                  placeholder="0"
                />
              </div>
            </div>

            {/* Swap Button */}
            <div className="flex justify-center">
              <Button
                variant="outline"
                size="icon"
                onClick={handleSwap}
                className="size-9 rounded-full border border-border bg-card shadow-sm hover:bg-secondary"
              >
                <ArrowDownUp className="size-4 text-primary" />
              </Button>
            </div>

            {/* To */}
            <div className="rounded-2xl border border-border bg-secondary/40 p-4">
              <Label className="text-xs text-muted-foreground">받는 금액 (예상, {toCurrency})</Label>
              <div className="mt-2 flex items-center gap-3">
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="text-xl" aria-hidden>
                    {CURRENCY_META[toCurrency].flag}
                  </span>
                  {toCurrency}
                </span>
                <p className="flex-1 text-right text-2xl font-bold tabular-nums">
                  {formatCurrency(received, toCurrency)}
                </p>
              </div>
            </div>
          </div>

          {/* Quick amounts */}
          <div className="mt-4 flex flex-wrap gap-2">
            {direction === "KRW_TO_USD" ? (
              [100000, 500000, 1000000].map((q) => (
                <Button key={q} variant="outline" size="sm" onClick={() => setInputAmount(String(q))}>
                  +{(q / 10000).toLocaleString("ko-KR")}만 원
                </Button>
              ))
            ) : (
              [100, 500, 1000].map((q) => (
                <Button key={q} variant="outline" size="sm" onClick={() => setInputAmount(String(q))}>
                  +${q.toLocaleString("ko-KR")}
                </Button>
              ))
            )}
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
                    `₩${appliedRate.toLocaleString("ko-KR", { maximumFractionDigits: 2 })} / USD`
                  ) : (
                    "₩0.00 / USD"
                  )}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-muted-foreground">환전 금액</dt>
                <dd className="font-medium tabular-nums">{formatCurrency(netAmount, fromCurrency)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-muted-foreground">수수료 (0.1%)</dt>
                <dd className="font-medium tabular-nums">{formatCurrency(fee, fromCurrency)}</dd>
              </div>
              <Separator />
              <div className="flex justify-between">
                <dt className="font-semibold">총 차감액</dt>
                <dd className="font-bold tabular-nums">{formatCurrency(totalDeduct, fromCurrency)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="font-semibold text-primary">받는 금액</dt>
                <dd className="font-bold tabular-nums text-primary">{formatCurrency(received, toCurrency)}</dd>
              </div>
            </dl>
          </Card>

          <Card className="flex gap-3 bg-secondary/40 p-4">
            <Info className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <p className="text-xs leading-relaxed text-muted-foreground">
              환율은 실시간으로 변동될 수 있습니다. USD 환전은 영업시간 및 백엔드 설정에 따라 지연이 발생할 수 있습니다.
            </p>
          </Card>
        </div>
      </div>
    </AppShell>
  )
}
