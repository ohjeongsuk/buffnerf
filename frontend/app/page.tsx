import Link from "next/link";
import { api, fmtDate, type Game, type Page, type PatchSummary } from "@/lib/api";
import { Empty } from "./components";

// 빌드 시점엔 백엔드가 없을 수 있어 요청 때 렌더링 (데이터는 api()의 60초 캐시)
export const dynamic = "force-dynamic";

/** 홈: 게임 선택 + 최신 패치 바로가기 (F5) */
export default async function Home() {
  const games = (await api<Game[]>("/api/games")) ?? [];
  const latest = await Promise.all(
    games.map((g) => api<Page<PatchSummary>>(`/api/games/${g.id}/patches?size=1`).then((p) => p?.content[0])),
  );

  return (
    <>
      <h1 className="mb-1 text-2xl font-bold">게임 패치노트, 빠르게 확인</h1>
      <p className="mb-6 text-neutral-500">패치노트를 3줄로 정리하고, 캐릭터별 상향·하향을 한눈에 보여드려요.</p>
      {games.length === 0 && <Empty>게임 정보를 불러오지 못했습니다.</Empty>}
      <ul className="grid gap-3 sm:grid-cols-3">
        {games.map((g, i) => (
          <li key={g.id} className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
            <Link href={`/games/${g.id}`} className="text-lg font-semibold hover:underline">
              {g.name}
            </Link>
            {latest[i] ? (
              <Link href={`/patches/${latest[i].id}`} className="mt-2 block text-sm text-neutral-600 hover:underline dark:text-neutral-400">
                최신: {latest[i].title}
                <span className="block text-xs text-neutral-400">{fmtDate(latest[i].publishedAt)}</span>
              </Link>
            ) : (
              <p className="mt-2 text-sm text-neutral-400">수집된 패치 없음</p>
            )}
          </li>
        ))}
      </ul>
    </>
  );
}
