import Link from "next/link";
import { notFound } from "next/navigation";
import { api, fmtDate, gameColor, shown, verdict, type Change, type ChangeType, type Page, type PatchDetail, type PatchSummary } from "@/lib/api";
import { Badge, ChangeRow, Empty } from "../../components";

const ORDER: Record<ChangeType, number> = { BUFF: 0, NERF: 1, ADJUST: 2 };
const GROUP: { type: ChangeType; label: string; cls: string }[] = [
  { type: "BUFF", label: "상향", cls: "border-emerald-300 text-emerald-800 hover:bg-emerald-50 dark:border-emerald-800 dark:text-emerald-300 dark:hover:bg-emerald-950" },
  { type: "NERF", label: "하향", cls: "border-rose-300 text-rose-800 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950" },
  { type: "ADJUST", label: "조정", cls: "border-amber-300 text-amber-800 hover:bg-amber-50 dark:border-amber-800 dark:text-amber-300 dark:hover:bg-amber-950" },
];

/** 패치 상세: AI 3줄 요약, 상향·하향 집계와 바로가기, 캐릭터별 변경, 근거 문장 (F3, F4, F5) */
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
  // 게임 모드별 묶음: 항목이 "[클래식] …"처럼 시작하면 그 모드, 아니면 기본 모드(""). 기본 모드가 맨 앞
  const modes = [...Map.groupBy(changes, (c) => /^\[([^\]]+)\]/.exec(c.attribute)?.[1] ?? "").entries()]
    .sort(([a], [b]) => (a === "" ? -1 : b === "" ? 1 : a.localeCompare(b, "ko")))
    .map(([mode, cs], m) => ({
      mode,
      m,
      // 묶음 제목에 모드가 있으니 항목에서는 뗀다
      characters: byCharacter(mode ? cs.map((c) => ({ ...c, attribute: c.attribute.slice(mode.length + 2).trimStart() })) : cs),
    }));
  const split = modes.some((x) => x.mode !== "");
  const modeLabel = (mode: string) => mode || (p.gameName === "League of Legends" ? "소환사의 협곡" : "기본");
  // 위쪽 집계는 아래 바로가기·목록 화면(▲▼)과 같은 기준: 기본 모드의 캐릭터별 판정. 상향 + 하향 + 조정 = 변경 캐릭터
  const characters = modes.find((x) => x.mode === "")?.characters ?? [];
  const count = (t: ChangeType) => characters.filter((x) => x.v === t).length;

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
        <dl className="mb-4 grid grid-cols-4 gap-2 text-center">
          <Stat label="변경 캐릭터" value={`${characters.length}명`} />
          <Stat label="▲ 상향" value={`${count("BUFF")}명`} cls="text-emerald-600" />
          <Stat label="▼ 하향" value={`${count("NERF")}명`} cls="text-rose-600" />
          <Stat label="조정" value={`${count("ADJUST")}명`} cls="text-amber-600" />
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

      <h2 className="mb-2 text-lg font-semibold">캐릭터별 변경</h2>
      {changes.length > 0 && changes[0].source === "DIFF" && (
        <p className="mb-2 text-sm text-neutral-500">AI 추출 전이라 Data Dragon 기본 스탯 비교 결과를 보여줍니다.</p>
      )}
      {changes.length === 0 && <Empty>캐릭터 밸런스 변경이 없습니다.</Empty>}

      {split && (
        <nav aria-label="게임 모드 바로가기" className="mb-4 flex flex-wrap gap-1.5">
          {modes.map((x) => (
            <a key={x.m} href={`#m-${x.m}`} className="rounded-full border border-neutral-300 px-3 py-1 text-sm font-medium hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900">
              {modeLabel(x.mode)} {x.characters.length}
            </a>
          ))}
        </nav>
      )}

      {modes.map(({ mode, m, characters }) => (
        <section key={m} id={`m-${m}`} className="mb-8 scroll-mt-4">
          {split && <h3 className="mb-2 border-b border-neutral-200 pb-1 font-semibold dark:border-neutral-800">{modeLabel(mode)}</h3>}
          {/* 판정별 바로가기: 누르면 해당 캐릭터 카드로 이동 */}
          <nav aria-label={`${modeLabel(mode)} 캐릭터 바로가기`} className="mb-4 space-y-2">
            {GROUP.map((g) => {
              const members = characters.map((x, i) => ({ ...x, i })).filter((x) => x.v === g.type);
              if (members.length === 0) return null;
              return (
                <div key={g.type} className="flex flex-wrap items-baseline gap-1.5">
                  <span className="mr-1 w-14 shrink-0 text-sm font-semibold text-neutral-500">
                    {g.label} {members.length}
                  </span>
                  {members.map((x) => (
                    <a key={x.i} href={`#c-${m}-${x.i}`} className={`rounded-full border px-2.5 py-0.5 text-sm ${g.cls}`}>
                      {x.cs[0].characterName}
                    </a>
                  ))}
                </div>
              );
            })}
          </nav>
          <div className="space-y-4">
            {characters.map((x, i) => (
              <CharacterCard key={i} id={`c-${m}-${i}`} gameId={p.gameId} cs={x.cs} v={x.v} />
            ))}
          </div>
        </section>
      ))}

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

/** 캐릭터별로 묶어 상향 → 하향 → 조정, 이름순 */
function byCharacter(changes: Change[]) {
  return [...Map.groupBy(changes, (c) => c.characterKey).values()]
    .map((cs) => ({ cs, v: verdict(cs) }))
    .sort((a, b) => ORDER[a.v] - ORDER[b.v] || a.cs[0].characterName.localeCompare(b.cs[0].characterName, "ko"));
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
