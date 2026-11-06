import Link from "next/link";
import { notFound } from "next/navigation";
import { api, fmtDate, shown, type Accuracy, type Change, type PatchDetail } from "@/lib/api";
import { ChangeRow, Empty } from "../../components";

/** 패치 상세: AI 3줄 요약, 캐릭터별 변경, 근거 문장, 롤 AI 정확도 (F3, F4, F5, F8) */
export default async function PatchPage({ params }: { params: Promise<{ patchId: string }> }) {
  const { patchId } = await params;
  const p = await api<PatchDetail>(`/api/patches/${patchId}`);
  if (!p) notFound();

  const changes = shown(p.changes);
  const byCharacter = Map.groupBy(changes, (c) => c.characterKey);

  return (
    <>
      <Link href={`/games/${p.gameId}`} className="text-sm text-neutral-500 hover:underline">
        ← {p.gameName}
      </Link>
      <h1 className="mt-1 text-2xl font-bold">{p.title}</h1>
      <p className="mb-4 text-sm text-neutral-400">{fmtDate(p.publishedAt)}</p>

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
      <div className="space-y-4">
        {[...byCharacter].map(([key, cs]) => (
          <CharacterCard key={key} gameId={p.gameId} cs={cs!} />
        ))}
      </div>
    </>
  );
}

function CharacterCard({ gameId, cs }: { gameId: number; cs: Change[] }) {
  const c = cs[0];
  return (
    <section className="rounded-lg border border-neutral-200 px-4 py-2 dark:border-neutral-800">
      <Link href={`/games/${gameId}/characters/${encodeURIComponent(c.characterKey)}`} className="font-semibold hover:underline">
        {c.characterName} →
      </Link>
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
