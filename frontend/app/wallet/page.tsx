"use client"

import { useState } from "react"
import { Plus, Minus, ArrowLeftRight, Wallet as WalletIcon, Landmark } from "lucide-react"
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

const FX_CODES: CurrencyCode[] = ["USD"/*, "JPY", "EUR", "CNY"*/]

interface MockAccountInfo {
  bankName: string
  accountNumber: string
  currency: string
  balance: number
}

export default function WalletPage() {
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [totalKrw, setTotalKrw] = useState<number>(0)
  const [fxBalances, setFxBalances] = useState<Record<CurrencyCode, number>>({
    KRW: 0,
    USD: 0,
    JPY: 0,
    EUR: 0,
    CNY: 0,
  })
  const [mockAccount, setMockAccount] = useState<MockAccountInfo | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [mode, setMode] = useState<"deposit" | "withdraw" | "transfer">("deposit")
  const [amount, setAmount] = useState("")
  const [bank, setBank] = useState(KOREAN_BANKS[0])
  const [account, setAccount] = useState("")
  const [recipientEmail, setRecipientEmail] = useState("")
  const [transferCurrency, setTransferCurrency] = useState<CurrencyCode>("KRW")
  const [memo, setMemo] = useState("")
  const [open, setOpen] = useState(false)
  const [recipientName, setRecipientName] = useState("")
  const [checkingEmail, setCheckingEmail] = useState(false)

  function handleAddAmount(amountToAdd: number) {
    setAmount((prev) => {
      const current = Number(prev) || 0
      return String(current + amountToAdd)
    })
  }

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
      setTotalKrw(balanceRes.totalKrw || 0)

      // 연결된 모의계좌(KRW) 잔액 조회 — 미연결 상태(404 등)면 카드를 숨긴다
      try {
        const mockAccountRes = await apiRequest<MockAccountInfo>(
          "GET",
          "/api/v1/users/me/mock-account"
        )
        setMockAccount(mockAccountRes)
      } catch (err: any) {
        // 회원가입 시 계좌가 즉시 연결되지만, 연결 실패/탈퇴 등으로 404가 날 수 있다.
        // 이 경우는 정상 흐름이므로 console.error로 시끄럽게 남기지 않는다.
        if (err?.status === 404) {
          console.debug("연결된 모의계좌 없음", err)
        } else {
          console.error("Failed to load mock account", err)
        }
        setMockAccount(null)
      }

      const historyRes = await apiRequest<{ totalCount: number; transactionResponseList: any[] }>(
        "GET",
        "/api/v1/wallets/transactions?size=50"
      )

      if (historyRes.transactionResponseList) {
        const rawList = (historyRes.transactionResponseList || []).filter(Boolean)
        const exchangeCounts: Record<string, number> = {}

        const mappedTxList = rawList.map((tx: any) => {
          let direction = tx.direction
          let currency = tx.currency

          // Fallback for exchanges if backend has not been restarted
          if (tx.type === "EXCHANGE" && (!direction || !currency)) {
            const count = exchangeCounts[tx.journalId] || 0
            exchangeCounts[tx.journalId] = count + 1
            
            if (count % 2 === 0) {
              direction = "DEBIT"
              currency = tx.fromCurrency || "KRW"
            } else {
              direction = "CREDIT"
              currency = tx.toCurrency || "USD"
            }
          }

          const isCredit = direction === "CREDIT"
          const amountSign = isCredit ? 1 : -1
          
          let type: TxType = "transfer"
          let title = ""
          let amountKRW = Number(tx.amount || 0) * amountSign
          let fromCurrency: CurrencyCode | undefined = undefined
          let toCurrency: CurrencyCode | undefined = currency as CurrencyCode
          let rate: number | undefined = undefined
          let fee: number | undefined = undefined
          let detailStr = tx.memo || undefined

          if (tx.type === "CHARGE") {
            type = "deposit"
            title = "KRW 입금"
            detailStr = "모의계좌 입금"
          } else if (tx.type === "WITHDRAW") {
            type = "withdraw"
            title = "KRW 출금"
            detailStr = "모의계좌 출금"
          } else if (tx.type === "EXCHANGE") {
            type = "exchange"
            title = `${tx.fromCurrency} → ${tx.toCurrency} 환전`
            
            amountKRW = direction === "CREDIT" ? Number(tx.toAmount) : -Number(tx.fromAmount)
            fromCurrency = tx.fromCurrency as CurrencyCode
            toCurrency = tx.toCurrency as CurrencyCode
            rate = Number(tx.exchangeRate)
            fee = Number(tx.feeAmount)
          } else if (tx.type === "TRANSFER") {
            type = "transfer"
            title = `이체 (${isCredit ? "받음" : "보냄"})`
            detailStr = tx.counterpartyEmail ? `${isCredit ? "보낸이" : "받는이"}: ${tx.counterpartyEmail}` : tx.memo
          }

          return {
            id: `w-${tx.journalId || Math.random()}-${currency || "KRW"}`,
            journalId: tx.journalId,
            type,
            title,
            amountKRW,
            fromCurrency,
            toCurrency,
            rate,
            fee,
            status: "completed" as TxStatus,
            createdAt: tx.createdAt ? new Date(tx.createdAt).toISOString() : new Date().toISOString(),
            detail: detailStr,
            fromAmount: tx.type === "EXCHANGE" ? Number(tx.fromAmount) : undefined,
            toAmount: tx.type === "EXCHANGE" ? Number(tx.toAmount) : undefined
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

  async function checkRecipient() {
    if (!recipientEmail || !recipientEmail.includes("@")) {
      setRecipientName("")
      return
    }
    setCheckingEmail(true)
    try {
      const res = await apiRequest<{ name: string; email: string }>(
        "GET",
        `/api/v1/users/check?email=${recipientEmail.trim()}`
      )
      setRecipientName(res.name)
    } catch (err: any) {
      setRecipientName("")
      toast.error(err.message || "존재하지 않는 회원입니다.")
    } finally {
      setCheckingEmail(false)
    }
  }

  const fxValueKRW = FX_CODES.reduce((sum, c) => sum + (fxBalances[c] ?? 0) * krwPerUnit(c), 0)
  const totalKRW = totalKrw

  async function submit() {
    const value = Number(amount.replace(/,/g, ""))
    if (!value || value <= 0) return toast.error("올바른 금액을 입력하세요.")
    if (mode === "withdraw" && value > krwBalance) return toast.error("출금 가능 잔액을 초과했습니다.")

    if (mode === "transfer") {
      if (checkingEmail) return toast.error("이메일 검증이 완료될 때까지 기다려 주세요.")
      if (!recipientEmail.trim()) return toast.error("수취인 이메일을 입력하세요.")
      if (!recipientName) return toast.error("올바른 수취인 이메일을 입력하고 확인을 받으세요.")
      const selectedBalance = transferCurrency === "KRW" ? krwBalance : (fxBalances[transferCurrency] ?? 0)
      if (value > selectedBalance) return toast.error(`${transferCurrency} 잔액을 초과했습니다.`)
    }

    try {
      const userIdStr = typeof window !== "undefined" ? localStorage.getItem("fxflow-userId") : null
      const bankAccountId = userIdStr ? Number(userIdStr) : 1

      if (mode === "deposit") {
        await apiRequest("POST", "/api/v1/wallets/charge", {
          bankAccountId: bankAccountId,
          amount: value,
        })
        toast.success(`${formatKRW(value)} 충전이 완료되었습니다.`)
      } else if (mode === "withdraw") {
        await apiRequest("POST", "/api/v1/wallets/withdraw", {
          bankAccountId: bankAccountId,
          amount: value,
        })
        toast.success(`${formatKRW(value)} 출금이 완료되었습니다.`)
      } else if (mode === "transfer") {
        await apiRequest("POST", "/api/v1/wallets/transfer", {
          recipientEmail: recipientEmail.trim(),
          currency: transferCurrency,
          amount: value,
          memo: memo.trim(),
        })
        toast.success(`${recipientEmail}님께 ${formatCurrency(value, transferCurrency)} 송금이 완료되었습니다.`)
      }

      setAmount("")
      setAccount("")
      setRecipientEmail("")
      setRecipientName("")
      setMemo("")
      setOpen(false)
      fetchData()
    } catch (err: any) {
      console.error(err)
      toast.error(err.message || "거래 처리에 실패했습니다.")
    }
  }

  const walletTx = transactions.filter((t) => t.type === "deposit" || t.type === "withdraw")
  const exchangeTx = transactions.filter((t) => t.type === "exchange")
  const transferTx = transactions.filter((t) => t.type === "transfer")

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
                    <Plus className="size-4" /> 충전
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
              <DialogTrigger
                render={
                  <Button variant="secondary" size="sm" onClick={() => setMode("transfer")}>
                    <ArrowLeftRight className="size-4" /> 지갑 송금
                  </Button>
                }
              />
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>
                    {mode === "deposit" ? "KRW 충전" : mode === "withdraw" ? "KRW 출금" : "P2P 지갑 송금"}
                  </DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                  {mode === "transfer" && (
                    <>
                      <div className="space-y-2">
                        <Label htmlFor="recipientEmail">수취인 이메일</Label>
                        <Input
                          id="recipientEmail"
                          type="email"
                          placeholder="recipient@example.com"
                          value={recipientEmail}
                          onChange={(e) => {
                            setRecipientEmail(e.target.value)
                            setRecipientName("")
                          }}
                          onBlur={checkRecipient}
                        />
                        {checkingEmail && <p className="text-xs text-muted-foreground animate-pulse">이메일 확인 중...</p>}
                        {recipientName && (
                          <p className="text-xs text-emerald-600 font-medium">
                            수취인 확인: {recipientName}님
                          </p>
                        )}
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="transferCurrency">통화 선택</Label>
                        <Select
                          value={transferCurrency}
                          onValueChange={(v) => setTransferCurrency((v as CurrencyCode) ?? "KRW")}
                        >
                          <SelectTrigger id="transferCurrency">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {["KRW", ...FX_CODES].map((c) => (
                              <SelectItem key={c} value={c}>
                                {c} ({CURRENCY_META[c as CurrencyCode]?.name || "원"})
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    </>
                  )}
                  <div className="space-y-2">
                    <Label htmlFor="amount">금액 {mode === "transfer" ? `(${transferCurrency})` : "(KRW)"}</Label>
                    <Input
                      id="amount"
                      inputMode="decimal"
                      placeholder="0"
                      value={amount}
                      onChange={(e) => {
                        const val = e.target.value
                        if (mode === "transfer" && transferCurrency !== "KRW") {
                          setAmount(val.replace(/[^\d.]/g, ""))
                        } else {
                          setAmount(val.replace(/[^\d]/g, ""))
                        }
                      }}
                    />
                  </div>
                  {mode === "transfer" && (
                    <div className="flex flex-wrap gap-2 items-center">
                      {transferCurrency === "KRW" ? (
                        [100000, 500000, 1000000].map((q) => (
                          <Button
                            key={q}
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleAddAmount(q)}
                          >
                            +{(q / 10000).toLocaleString("ko-KR")}만 원
                          </Button>
                        ))
                      ) : (
                        [100, 500, 1000].map((q) => (
                          <Button
                            key={q}
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => handleAddAmount(q)}
                          >
                            +${q.toLocaleString("ko-KR")}
                          </Button>
                        ))
                      )}
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => setAmount("")}
                        className="text-muted-foreground hover:text-foreground"
                      >
                        초기화
                      </Button>
                    </div>
                  )}
                  {mode === "transfer" && (
                    <div className="space-y-2">
                      <Label htmlFor="memo">메모</Label>
                      <Input
                        id="memo"
                        placeholder="이체 메모 입력"
                        value={memo}
                        onChange={(e) => setMemo(e.target.value)}
                      />
                    </div>
                  )}
                  <Button
                    className="w-full"
                    onClick={submit}
                    disabled={mode === "transfer" && checkingEmail}
                  >
                    {mode === "deposit" ? "충전하기" : mode === "withdraw" ? "출금하기" : checkingEmail ? "이메일 확인 중..." : "송금하기"}
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          </div>
        </Card>

        {/* Linked mock bank account */}
        {mockAccount && (
          <div>
            <h2 className="mb-3 text-sm font-semibold text-muted-foreground">연결된 계좌</h2>
            <Card className="p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="flex size-10 items-center justify-center rounded-full bg-secondary text-muted-foreground">
                    <Landmark className="size-[18px]" />
                  </span>
                  <div>
                    <p className="text-sm font-semibold">{mockAccount.bankName}</p>
                    <p className="text-xs text-muted-foreground">
                      {mockAccount.accountNumber.replace(/(\d{3})(\d{3})(\d{6})/, "$1-$2-$3")}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">잔액</p>
                  <p className="text-lg font-bold tabular-nums">{formatKRW(mockAccount.balance)}</p>
                </div>
              </div>
            </Card>
          </div>
        )}

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
                <TabsTrigger value="transfer">이체</TabsTrigger>
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
            <TabsContent value="transfer" className="mt-2 divide-y divide-border">
              {transferTx.map((t) => (
                <TransactionRow key={t.id} tx={t} />
              ))}
            </TabsContent>
          </Tabs>
        </Card>
      </div>
    </AppShell>
  )
}
