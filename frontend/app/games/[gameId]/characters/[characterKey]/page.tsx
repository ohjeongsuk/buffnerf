import Link from "next/link";
import { notFound } from "next/navigation";
import { api, fmtDate, type Timeline } from "@/lib/api";
import { Badge, ChangeRow, Empty } from "../../../../components";

/** 캐릭터 타임라인: 패치별 상향·하향 이력, 최신순 (F6) */
export default async function CharacterPage({ params }: { params: Promise<{ gameId: string; characterKey: string }> }) {
  const { gameId, characterKey } = await params;
  // 한글 키(예: 재키)도 안전하게: 디코드 후 다시 인코드
  const key = encodeURIComponent(decodeURIComponent(characterKey));
  const t = await api<Timeline>(`/api/games/${gameId}/characters/${key}/timeline`);
  if (!t) notFound();
  const entries = [...t.entries].reverse();

  return (
    <>
      <Link href={`/games/${gameId}`} className="text-sm text-neutral-500 hover:underline">
        ← 패치 목록
      </Link>
      <h1 className="mb-6 mt-1 text-2xl font-bold">{t.name} 변경 이력</h1>
      {entries.length === 0 && <Empty>변경 이력이 없습니다.</Empty>}
      <ol className="relative border-l border-neutral-300 pl-5 dark:border-neutral-700">
        {entries.map((e) => (
          <li key={e.patchId} className="mb-6">
            <span className="absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full bg-neutral-400" />
            <Link href={`/patches/${e.patchId}`} className="font-semibold hover:underline">
              {e.title}
            </Link>
            <span className="ml-2 text-xs text-neutral-400">{fmtDate(e.publishedAt)}</span>
            <div className="mt-1 flex gap-1">
              {(["BUFF", "NERF", "ADJUST"] as const)
                .filter((type) => e.changes.some((c) => c.changeType === type))
                .map((type) => <Badge key={type} type={type} />)}
            </div>
            <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
              {e.changes.map((c, i) => <ChangeRow key={i} c={c} />)}
            </ul>
          </li>
        ))}
      </ol>
    </>
  );
}
