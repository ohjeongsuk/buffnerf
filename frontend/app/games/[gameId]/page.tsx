import Link from "next/link";
import { notFound } from "next/navigation";
import { api, fmtDate, gameColor, type CharacterView, type Game, type Page, type PatchSummary } from "@/lib/api";
import { Empty } from "../../components";

/** 패치 목록 + 패치별 상향·하향 차트 (F5, F7) */
export default async function GamePage({
  params,
  searchParams,
}: {
  params: Promise<{ gameId: string }>;
  searchParams: Promise<{ page?: string }>;
}) {
  const { gameId } = await params;
  const page = Math.max(0, Number((await searchParams).page ?? 0) || 0);
  const [games, patches, stats, characters] = await Promise.all([
    api<Game[]>("/api/games"),
    api<Page<PatchSummary>>(`/api/games/${gameId}/patches?page=${page}&size=20`),
    api<PatchSummary[]>(`/api/games/${gameId}/stats`),
    api<CharacterView[]>(`/api/games/${gameId}/characters`),
  ]);
  const game = games?.find((g) => String(g.id) === gameId);
  if (!game || !patches) notFound();

  return (
    <>
      <h1 className="mb-4 flex items-center gap-2 text-2xl font-bold">
        <span aria-hidden className={`inline-block size-3 rounded-full ${gameColor(game.name).dot}`} />
        {game.name}
      </h1>

      {stats && stats.length > 0 && <StatsChart stats={stats.slice(-20)} />}

      <h2 className="mb-2 mt-6 text-lg font-semibold">패치</h2>
      {patches.content.length === 0 && <Empty>아직 수집된 패치가 없습니다.</Empty>}
      <ul className="divide-y divide-neutral-200 dark:divide-neutral-800">
        {patches.content.map((p) => (
          <li key={p.id}>
            <Link href={`/patches/${p.id}`} className="flex items-center justify-between gap-3 py-3 hover:bg-neutral-50 dark:hover:bg-neutral-900">
              <span className="min-w-0">
                <span className="block truncate font-medium">{p.title}</span>
                <span className="text-xs text-neutral-400">{fmtDate(p.publishedAt)}</span>
              </span>
              <span className="shrink-0 text-sm">
                <span className="text-emerald-600">▲{p.buffs}</span> <span className="text-rose-600">▼{p.nerfs}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <Pager gameId={gameId} page={page} totalPages={patches.page.totalPages} />

      {characters && characters.length > 0 && (
        <>
          <h2 className="mb-2 mt-8 text-lg font-semibold">캐릭터별 변경 이력</h2>
          <ul className="flex flex-wrap gap-2">
            {characters.map((c) => (
              <li key={c.key}>
                <Link
                  href={`/games/${gameId}/characters/${encodeURIComponent(c.key)}`}
                  className="inline-block rounded-full border border-neutral-300 px-3 py-1 text-sm hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-800"
                >
                  {c.name} <span className="text-neutral-400">{c.patchCount}</span>
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}

/** 패치별 상향(위)·하향(아래) 캐릭터 수 막대 */
function StatsChart({ stats }: { stats: PatchSummary[] }) {
  const max = Math.max(1, ...stats.flatMap((s) => [s.buffs, s.nerfs]));
  return (
    <figure className="rounded-lg border border-neutral-200 p-3 dark:border-neutral-800">
      <figcaption className="mb-2 text-sm text-neutral-500">
        최근 패치별 상향 <span className="text-emerald-600">▲</span> · 하향 <span className="text-rose-600">▼</span> 캐릭터 수
      </figcaption>
      <div className="flex h-32 items-stretch gap-1" role="img" aria-label="패치별 상향·하향 캐릭터 수 차트">
        {stats.map((s) => (
          <div key={s.id} className="flex flex-1 flex-col" title={`${s.title}: 상향 ${s.buffs}, 하향 ${s.nerfs}`}>
            <div className="flex flex-1 items-end">
              <div className="w-full rounded-t bg-emerald-500" style={{ height: `${(s.buffs / max) * 100}%` }} />
            </div>
            <div className="h-px bg-neutral-300 dark:bg-neutral-700" />
            <div className="flex flex-1 items-start">
              <div className="w-full rounded-b bg-rose-500" style={{ height: `${(s.nerfs / max) * 100}%` }} />
            </div>
          </div>
        ))}
      </div>
    </figure>
  );
}

function Pager({ gameId, page, totalPages }: { gameId: string; page: number; totalPages: number }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-3 flex justify-between text-sm">
      {page > 0 ? <Link href={`/games/${gameId}?page=${page - 1}`} className="hover:underline">← 최신</Link> : <span />}
      <span className="text-neutral-400">{page + 1} / {totalPages}</span>
      {page + 1 < totalPages ? <Link href={`/games/${gameId}?page=${page + 1}`} className="hover:underline">이전 →</Link> : <span />}
    </div>
  );
}
