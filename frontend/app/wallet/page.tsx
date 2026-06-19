"use client"

import { useState } from "react"
import { Plus, Minus, Wallet as WalletIcon } from "lucide-react"
import { toast } from "sonner"
import { AppShell } from "@/components/app/app-shell"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { TransactionRow } from "@/components/app/transaction-row"
import { useStore, type Transaction, type TxType } from "@/lib/store"
import { CURRENCY_META, formatKRW, formatCurrency, krwPerUnit, type CurrencyCode } from "@/lib/fx-data"
import { KOREAN_BANKS } from "@/lib/fx-data"
import { apiRequest } from "@/lib/api"
import { useEffect } from "react"

const FX_CODES: CurrencyCode[] = ["USD", "JPY", "EUR", "CNY"]

export default function WalletPage() {
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [fxBalances, setFxBalances] = useState<Record<CurrencyCode, number>>({
    KRW: 0,
    USD: 0,
    JPY: 0,
    EUR: 0,
    CNY: 0,
  })
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [mode, setMode] = useState<"deposit" | "withdraw">("deposit")
  const [amount, setAmount] = useState("")
  const [bank, setBank] = useState(KOREAN_BANKS[0])
  const [account, setAccount] = useState("")
  const [open, setOpen] = useState(false)

  const fetchData = async () => {
    try {
      const balanceRes = await apiRequest<{ totalKrw: number; walletResponseList: { currency: string; balance: number }[] }>(
        "GET",
        "/api/v1/wallets"
      )
      
      const newFxBalances: Record<CurrencyCode, number> = {
        KRW: 0,
        USD: 0,
        JPY: 0,
        EUR: 0,
        CNY: 0,
      }
      
      let localKrw = 0
      if (balanceRes.walletResponseList) {
        balanceRes.walletResponseList.forEach((w) => {
          if (w.currency === "KRW") {
            localKrw = w.balance
          }
          newFxBalances[w.currency as CurrencyCode] = w.balance
        })
      }
      setKrwBalance(localKrw)
      setFxBalances(newFxBalances)

      const historyRes = await apiRequest<{ totalCount: number; transactionResponseList: any[] }>(
        "GET",
        "/api/v1/wallets/transactions?size=50"
      )

      if (historyRes.transactionResponseList) {
        const mappedTxList = historyRes.transactionResponseList.map((tx: any) => {
          let type: TxType = "deposit"
          let title = ""
          let amountKRW = Number(tx.amount)

          if (tx.type === "CHARGE") {
            type = "deposit"
            title = "KRW 입금"
          } else if (tx.type === "WITHDRAW") {
            type = "withdraw"
            title = "KRW 출금"
            amountKRW = -amountKRW
          } else if (tx.type === "EXCHANGE") {
            type = "exchange"
            if (tx.currency === "KRW") {
              title = "KRW → USD 환전 (지불)"
              amountKRW = -amountKRW
            } else {
              title = "KRW → USD 환전 (수취)"
              amountKRW = amountKRW * 1530 // Convert to KRW for UI list display
            }
          } else if (tx.type === "TRANSFER") {
            type = "remittance"
            title = "해외송금"
            amountKRW = -amountKRW
          }

          return {
            id: String(tx.transactionId),
            type,
            title,
            amountKRW,
            status: "completed",
            createdAt: tx.createdAt ? new Date(tx.createdAt).toISOString() : new Date().toISOString(),
          } as Transaction
        })
        setTransactions(mappedTxList)
      } else {
        setTransactions([])
      }
    } catch (err) {
      console.error("Failed to load wallet data", err)
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  const fxValueKRW = FX_CODES.reduce((sum, c) => sum + (fxBalances[c] ?? 0) * krwPerUnit(c), 0)
  const totalKRW = krwBalance + fxValueKRW

  async function submit() {
    const value = Number(amount.replace(/,/g, ""))
    if (!value || value <= 0) return toast.error("올바른 금액을 입력하세요.")
    // if (!account.trim()) return toast.error("계좌번호를 입력하세요.")
    if (mode === "withdraw" && value > krwBalance) return toast.error("출금 가능 잔액을 초과했습니다.")

    // const cleanAccount = account.replace(/[^\d]/g, "").padEnd(12, "0").slice(0, 12)

    try {
      const userIdStr = typeof window !== "undefined" ? localStorage.getItem("fxflow-userId") : null
      const bankAccountId = userIdStr ? Number(userIdStr) : 1

      if (mode === "deposit") {
        await apiRequest("POST", "/api/v1/wallets/charge", {
          bankAccountId: bankAccountId,
          amount: value,
        })
        toast.success(`${formatKRW(value)} 입금이 완료되었습니다.`)
      } else {
        await apiRequest("POST", "/api/v1/wallets/withdraw", {
          bankAccountId: bankAccountId,
          amount: value,
        })
        toast.success(`${formatKRW(value)} 출금이 완료되었습니다.`)
      }

      setAmount("")
      setAccount("")
      setOpen(false)
      fetchData()
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "거래 처리에 실패했습니다.")
    }
  }

  const walletTx = transactions.filter((t) => t.type === "deposit" || t.type === "withdraw")
  const exchangeTx = transactions.filter((t) => t.type === "exchange")

  return (
    <AppShell title="내 지갑">
      <div className="space-y-6">
        {/* Total balance */}
        <Card className="overflow-hidden border-0 bg-primary p-6 text-primary-foreground">
          <div className="flex items-center gap-2 text-sm text-primary-foreground/80">
            <WalletIcon className="size-4" /> 총 자산 (KRW 환산)
          </div>
          <p className="mt-2 text-4xl font-bold tabular-nums">{formatKRW(totalKRW)}</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Dialog open={open} onOpenChange={setOpen}>
              <DialogTrigger
                render={
                  <Button variant="secondary" size="sm" onClick={() => setMode("deposit")}>
                    <Plus className="size-4" /> 입금
                  </Button>
                }
              />
              <DialogTrigger
                render={
                  <Button variant="secondary" size="sm" onClick={() => setMode("withdraw")}>
                    <Minus className="size-4" /> 출금
                  </Button>
                }
              />
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>{mode === "deposit" ? "KRW 입금" : "KRW 출금"}</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                  {/*
                  <div className="space-y-2">
                    <Label htmlFor="bank">은행</Label>
                    <Select value={bank} onValueChange={(v) => setBank(v ?? KOREAN_BANKS[0])}>
                      <SelectTrigger id="bank">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {KOREAN_BANKS.map((b) => (
                          <SelectItem key={b} value={b}>
                            {b}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="account">계좌번호</Label>
                    <Input
                      id="account"
                      placeholder="계좌번호 입력"
                      value={account}
                      onChange={(e) => setAccount(e.target.value)}
                    />
                  </div>
                  */}
                  <div className="space-y-2">
                    <Label htmlFor="amount">금액 (KRW)</Label>
                    <Input
                      id="amount"
                      inputMode="numeric"
                      placeholder="0"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value.replace(/[^\d]/g, ""))}
                    />
                  </div>
                  <Button className="w-full" onClick={submit}>
                    {mode === "deposit" ? "입금하기" : "출금하기"}
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          </div>
        </Card>

        {/* Balances per currency */}
        <div>
          <h2 className="mb-3 text-sm font-semibold text-muted-foreground">통화별 잔액</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <Card className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-2xl" aria-hidden>
                    {CURRENCY_META.KRW.flag}
                  </span>
                  <div>
                    <p className="text-sm font-semibold">KRW</p>
                    <p className="text-xs text-muted-foreground">대한민국 원</p>
                  </div>
                </div>
                <p className="text-lg font-bold tabular-nums">{formatKRW(krwBalance)}</p>
              </div>
            </Card>
            {FX_CODES.map((c) => (
              <Card key={c} className="p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-2xl" aria-hidden>
                      {CURRENCY_META[c].flag}
                    </span>
                    <div>
                      <p className="text-sm font-semibold">{c}</p>
                      <p className="text-xs text-muted-foreground">{CURRENCY_META[c].name}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold tabular-nums">{formatCurrency(fxBalances[c] ?? 0, c)}</p>
                    <p className="text-xs text-muted-foreground tabular-nums">
                      ≈ {formatKRW((fxBalances[c] ?? 0) * krwPerUnit(c))}
                    </p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>

        {/* Deposit/withdraw and Exchange history */}
        <Card className="p-2">
          <Tabs defaultValue="all" className="w-full">
            <div className="flex items-center justify-between px-3 pt-2">
              <h2 className="text-sm font-semibold">입출금 및 환전 내역</h2>
              <TabsList>
                <TabsTrigger value="all">전체</TabsTrigger>
                <TabsTrigger value="deposit">입금</TabsTrigger>
                <TabsTrigger value="withdraw">출금</TabsTrigger>
                <TabsTrigger value="exchange">환전</TabsTrigger>
              </TabsList>
            </div>
            <TabsContent value="all" className="mt-2 divide-y divide-border">
              {transactions.map((t) => (
                <TransactionRow key={t.id} tx={t} />
              ))}
            </TabsContent>
            <TabsContent value="deposit" className="mt-2 divide-y divide-border">
              {walletTx
                .filter((t) => t.type === "deposit")
                .map((t) => (
                  <TransactionRow key={t.id} tx={t} />
                ))}
            </TabsContent>
            <TabsContent value="withdraw" className="mt-2 divide-y divide-border">
              {walletTx
                .filter((t) => t.type === "withdraw")
                .map((t) => (
                  <TransactionRow key={t.id} tx={t} />
                ))}
            </TabsContent>
            <TabsContent value="exchange" className="mt-2 divide-y divide-border">
              {exchangeTx.map((t) => (
                <TransactionRow key={t.id} tx={t} />
              ))}
            </TabsContent>
          </Tabs>
        </Card>
      </div>
    </AppShell>
  )
}
