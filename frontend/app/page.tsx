import Link from "next/link";
import { api, fmtDate, gameColor, type Game, type Page, type PatchSummary } from "@/lib/api";
import { Empty } from "./components";

// 빌드 시점엔 백엔드가 없을 수 있어 요청 때 렌더링 (데이터는 api()의 60초 캐시)
export const dynamic = "force-dynamic";

const FEED = 6;

/** 홈: 게임 선택 + 게임별 최신 패치 + 전체 최근 패치 피드 (F5) */
export default async function Home() {
  const games = (await api<Game[]>("/api/games")) ?? [];
  const recent = await Promise.all(
    games.map((g) => api<Page<PatchSummary>>(`/api/games/${g.id}/patches?size=${FEED}`).then((p) => p?.content ?? [])),
  );
  const feed = games
    .flatMap((g, i) => recent[i].map((p) => ({ game: g, p })))
    .sort((a, b) => (b.p.publishedAt ?? "").localeCompare(a.p.publishedAt ?? ""))
    .slice(0, FEED);

  return (
    <>
      <h1 className="mb-1 text-2xl font-bold">게임 패치노트, 빠르게 확인</h1>
      <p className="mb-6 text-neutral-500">패치노트를 3줄로 정리하고, 캐릭터별 상향·하향을 한눈에 보여드려요.</p>
      {games.length === 0 && <Empty>게임 정보를 불러오지 못했습니다.</Empty>}

      <ul className="grid gap-3 sm:grid-cols-3">
        {games.map((g, i) => {
          const latest = recent[i][0];
          return (
            <li key={g.id} className={`flex flex-col rounded-lg border border-t-4 border-neutral-200 p-4 dark:border-neutral-800 ${gameColor(g.name).bar}`}>
              <Link href={`/games/${g.id}`} className="text-lg font-semibold hover:underline">
                {g.name}
              </Link>
              {latest ? (
                <Link href={`/patches/${latest.id}`} className="group mt-2 flex flex-1 flex-col text-sm">
                  <span className="text-xs text-neutral-400">최신 · {fmtDate(latest.publishedAt)}</span>
                  <span className="font-medium group-hover:underline">{latest.title}</span>
                  <Counts p={latest} />
                  {latest.summaryKo && (
                    <span className="mt-2 line-clamp-2 text-neutral-500">{latest.summaryKo.split("\n")[0]}</span>
                  )}
                </Link>
              ) : (
                <p className="mt-2 text-sm text-neutral-400">수집된 패치 없음</p>
              )}
            </li>
          );
        })}
      </ul>

      {feed.length > 0 && (
        <section className="mt-10">
          <h2 className="mb-2 text-lg font-semibold">최근 패치</h2>
          <ul className="divide-y divide-neutral-200 dark:divide-neutral-800">
            {feed.map(({ game, p }) => (
              <li key={p.id}>
                <Link href={`/patches/${p.id}`} className="block py-3 hover:bg-neutral-50 dark:hover:bg-neutral-900">
                  <span className="flex items-center justify-between gap-3">
                    <span className="min-w-0">
                      <span className={`mr-2 rounded px-1.5 py-0.5 text-xs font-medium ${gameColor(game.name).tag}`}>
                        {game.name}
                      </span>
                      <span className="font-medium">{p.title}</span>
                    </span>
                    <span className="shrink-0 text-xs text-neutral-400">{fmtDate(p.publishedAt)}</span>
                  </span>
                  <Counts p={p} />
                  {p.summaryKo && <span className="mt-1 block line-clamp-1 text-sm text-neutral-500">{p.summaryKo.split("\n")[0]}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

function Counts({ p }: { p: PatchSummary }) {
  if (p.buffs === 0 && p.nerfs === 0) return null;
  return (
    <span className="mt-1 block text-sm">
      <span className="text-emerald-600">▲ 상향 {p.buffs}</span> <span className="ml-2 text-rose-600">▼ 하향 {p.nerfs}</span>
    </span>
  );
}
