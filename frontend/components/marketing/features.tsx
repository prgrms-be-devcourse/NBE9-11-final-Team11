import { LineChart, Wallet, CalendarClock, Send, ListChecks } from "lucide-react"
import { Card } from "@/components/ui/card"

const features = [
  { icon: LineChart, title: "실시간 환율 조회", desc: "USD, JPY, EUR, CNY 환율을 3분마다 갱신합니다." },
  { icon: Wallet, title: "다중 통화 지갑", desc: "여러 통화를 한 곳에서 보관하고 관리하세요." },
  { icon: CalendarClock, title: "환전 예약", desc: "목표 환율에 도달하면 자동으로 환전합니다." },
  { icon: Send, title: "해외송금 시뮬레이션", desc: "수취인 등록부터 송금까지 단계별로 체험하세요." },
  { icon: ListChecks, title: "거래 추적", desc: "모든 거래 상태를 실시간으로 확인합니다." },
]

export function Features() {
  return (
    <section id="features" className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <div className="mx-auto mb-12 max-w-2xl text-center">
        <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">필요한 모든 기능을 한 곳에서</h2>
        <p className="mt-3 text-pretty text-muted-foreground">
          환율 조회부터 해외송금까지, 복잡한 외환 거래를 직관적인 인터페이스로 시뮬레이션합니다.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {features.map((f) => (
          <Card key={f.title} className="gap-3 p-6 transition-shadow hover:shadow-md">
            <span className="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <f.icon className="size-5" />
            </span>
            <h3 className="text-lg font-semibold">{f.title}</h3>
            <p className="text-sm leading-relaxed text-muted-foreground">{f.desc}</p>
          </Card>
        ))}
      </div>
    </section>
  )
}
