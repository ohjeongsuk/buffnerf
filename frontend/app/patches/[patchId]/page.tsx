import Link from "next/link";
import { notFound } from "next/navigation";
import { api, fmtDate, gameColor, shown, verdict, type Accuracy, type Change, type ChangeType, type Page, type PatchDetail, type PatchSummary } from "@/lib/api";
import { Badge, ChangeRow, Empty } from "../../components";

const ORDER: Record<ChangeType, number> = { BUFF: 0, NERF: 1, ADJUST: 2 };
const GROUP: { type: ChangeType; label: string; cls: string }[] = [
  { type: "BUFF", label: "상향", cls: "border-emerald-300 text-emerald-800 hover:bg-emerald-50 dark:border-emerald-800 dark:text-emerald-300 dark:hover:bg-emerald-950" },
  { type: "NERF", label: "하향", cls: "border-rose-300 text-rose-800 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950" },
  { type: "ADJUST", label: "조정", cls: "border-amber-300 text-amber-800 hover:bg-amber-50 dark:border-amber-800 dark:text-amber-300 dark:hover:bg-amber-950" },
];

/** 패치 상세: AI 3줄 요약, 상향·하향 집계와 바로가기, 캐릭터별 변경, 근거 문장, 롤 AI 정확도 (F3, F4, F5, F8) */
export default async function PatchPage({ params }: { params: Promise<{ patchId: string }> }) {
  const { patchId } = await params;
  const p = await api<PatchDetail>(`/api/patches/${patchId}`);
  if (!p) notFound();
  // 이전·다음 패치: 게임별 패치는 수십 개라 한 번에 받아 위치를 찾는다
  const list = (await api<Page<PatchSummary>>(`/api/games/${p.gameId}/patches?size=200`))?.content ?? [];
  const at = list.findIndex((x) => x.id === p.id);
  const newer = at > 0 ? list[at - 1] : undefined;
  const older = at >= 0 ? list[at + 1] : undefined;

  const changes = shown(p.changes);
  const characters = [...Map.groupBy(changes, (c) => c.characterKey).values()]
    .map((cs) => ({ cs, v: verdict(cs) }))
    .sort((a, b) => ORDER[a.v] - ORDER[b.v] || a.cs[0].characterName.localeCompare(b.cs[0].characterName, "ko"));
  // 목록 화면(▲▼)과 같은 기준: 상향이 하나라도 있는 캐릭터, 하향이 하나라도 있는 캐릭터
  const buffs = characters.filter((x) => x.cs.some((c) => c.changeType === "BUFF")).length;
  const nerfs = characters.filter((x) => x.cs.some((c) => c.changeType === "NERF")).length;

  return (
    <>
      <Link href={`/games/${p.gameId}`} className={`inline-block rounded px-2 py-0.5 text-sm font-medium hover:underline ${gameColor(p.gameName).tag}`}>
        ← {p.gameName}
      </Link>
      <h1 className="mt-1 text-2xl font-bold">{p.title}</h1>
      <p className="mb-4 text-sm text-neutral-400">
        {fmtDate(p.publishedAt)}
        {p.sourceUrl && (
          <>
            {" · "}
            <a href={p.sourceUrl} target="_blank" rel="noopener noreferrer" className="underline hover:text-neutral-600">
              공식 패치노트 원문 ↗
            </a>
          </>
        )}
      </p>

      {characters.length > 0 && (
        <dl className="mb-4 grid grid-cols-3 gap-2 text-center">
          <Stat label="변경 캐릭터" value={`${characters.length}명`} />
          <Stat label="▲ 상향" value={`${buffs}명`} cls="text-emerald-600" />
          <Stat label="▼ 하향" value={`${nerfs}명`} cls="text-rose-600" />
        </dl>
      )}

      <section className="mb-6 rounded-lg bg-neutral-100 p-4 dark:bg-neutral-900">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">AI 3줄 요약</h2>
        {p.summaryKo ? (
          <ol className="list-decimal space-y-1 pl-5">
            {p.summaryKo.split("\n").map((line, i) => <li key={i}>{line}</li>)}
          </ol>
        ) : (
          <p className="text-neutral-500">
            {p.contentStatus === "PENDING_CONTENT" ? "패치노트 원문을 아직 확보하지 못했습니다." : "요약을 준비 중입니다."}
          </p>
        )}
      </section>

      {p.accuracy && <AccuracyBox a={p.accuracy} />}

      <h2 className="mb-2 text-lg font-semibold">캐릭터별 변경</h2>
      {changes.length > 0 && changes[0].source === "DIFF" && (
        <p className="mb-2 text-sm text-neutral-500">AI 추출 전이라 Data Dragon 기본 스탯 비교 결과를 보여줍니다.</p>
      )}
      {changes.length === 0 && <Empty>캐릭터 밸런스 변경이 없습니다.</Empty>}

      {/* 판정별 바로가기: 누르면 해당 캐릭터 카드로 이동 */}
      {characters.length > 0 && (
        <nav aria-label="캐릭터 바로가기" className="mb-4 space-y-2">
          {GROUP.map((g) => {
            const members = characters.map((x, i) => ({ ...x, i })).filter((x) => x.v === g.type);
            if (members.length === 0) return null;
            return (
              <div key={g.type} className="flex flex-wrap items-baseline gap-1.5">
                <span className="mr-1 w-14 shrink-0 text-sm font-semibold text-neutral-500">
                  {g.label} {members.length}
                </span>
                {members.map((x) => (
                  <a key={x.i} href={`#c-${x.i}`} className={`rounded-full border px-2.5 py-0.5 text-sm ${g.cls}`}>
                    {x.cs[0].characterName}
                  </a>
                ))}
              </div>
            );
          })}
        </nav>
      )}

      <div className="space-y-4">
        {characters.map((x, i) => (
          <CharacterCard key={i} id={`c-${i}`} gameId={p.gameId} cs={x.cs} v={x.v} />
        ))}
      </div>

      {(newer || older) && (
        <nav aria-label="다른 패치" className="mt-8 grid grid-cols-2 gap-3 text-sm">
          {older ? (
            <Link href={`/patches/${older.id}`} className="rounded-lg border border-neutral-200 p-3 hover:bg-neutral-50 dark:border-neutral-800 dark:hover:bg-neutral-900">
              <span className="block text-xs text-neutral-400">← 이전 패치</span>
              {older.title}
            </Link>
          ) : <span />}
          {newer && (
            <Link href={`/patches/${newer.id}`} className="rounded-lg border border-neutral-200 p-3 text-right hover:bg-neutral-50 dark:border-neutral-800 dark:hover:bg-neutral-900">
              <span className="block text-xs text-neutral-400">다음 패치 →</span>
              {newer.title}
            </Link>
          )}
        </nav>
      )}
    </>
  );
}

function Stat({ label, value, cls = "" }: { label: string; value: string; cls?: string }) {
  return (
    <div className="rounded-lg border border-neutral-200 py-2 dark:border-neutral-800">
      <dt className="text-xs text-neutral-500">{label}</dt>
      <dd className={`text-xl font-bold ${cls}`}>{value}</dd>
    </div>
  );
}

function CharacterCard({ id, gameId, cs, v }: { id: string; gameId: number; cs: Change[]; v: ChangeType }) {
  const c = cs[0];
  return (
    <section id={id} className="scroll-mt-4 rounded-lg border border-neutral-200 px-4 py-2 dark:border-neutral-800">
      <div className="flex items-center gap-2 pt-1">
        <Badge type={v} />
        <Link href={`/games/${gameId}/characters/${encodeURIComponent(c.characterKey)}`} className="font-semibold hover:underline">
          {c.characterName} →
        </Link>
        <span className="text-xs text-neutral-400">{cs.length}건</span>
      </div>
      <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
        {cs.map((x, i) => <ChangeRow key={i} c={x} />)}
      </ul>
    </section>
  );
}

function AccuracyBox({ a }: { a: Accuracy }) {
  const pct = (v: number | null) => (v == null ? "—" : `${Math.round(v * 100)}%`);
  return (
    <section className="mb-6 rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
      <h2 className="mb-2 text-sm font-semibold text-neutral-500">AI 추출 정확도 (기본 스탯, Data Dragon 기준)</h2>
      <dl className="grid grid-cols-2 gap-2 text-center">
        <div>
          <dt className="text-xs text-neutral-500">재현율 — 실제 변경 중 AI가 찾은 비율</dt>
          <dd className="text-2xl font-bold">{pct(a.recall)}</dd>
          <dd className="text-xs text-neutral-400">{a.matched} / {a.diffCount}</dd>
        </div>
        <div>
          <dt className="text-xs text-neutral-500">정밀도 — AI가 뽑은 변경 중 맞은 비율</dt>
          <dd className="text-2xl font-bold">{pct(a.precision)}</dd>
          <dd className="text-xs text-neutral-400">{a.matched} / {a.aiCount}</dd>
        </div>
      </dl>
    </section>
  );
}
