const steps = [
  { n: "01", title: "회원가입 및 KYC 인증", desc: "이메일로 가입하고 가상계좌 1원 인증을 완료하세요." },
  { n: "02", title: "가상 계좌 생성 및 입금", desc: "자동 생성된 KRW 지갑에 가상 원화를 입금합니다." },
  { n: "03", title: "환전 또는 해외송금", desc: "실시간 환율로 환전하거나 해외송금을 신청합니다." },
  { n: "04", title: "거래 상태 확인", desc: "타임라인으로 거래 진행 상황을 추적합니다." },
]

export function HowItWorks() {
  return (
    <section id="how" className="bg-secondary/40 py-16">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">이용 방법</h2>
          <p className="mt-3 text-pretty text-muted-foreground">4단계로 간단하게 외환 거래를 시작하세요.</p>
        </div>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {steps.map((s) => (
            <div key={s.n} className="relative rounded-3xl border border-border bg-card p-6">
              <span className="text-3xl font-bold text-primary/30 tabular-nums">{s.n}</span>
              <h3 className="mt-3 text-lg font-semibold">{s.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{s.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
