"use client";

import { useEffect, useState } from "react";
import type { Game } from "@/lib/api";

type PendingPatch = { id: number; gameId: number; version: string; title: string; contentError: string | null };

const KEY = "buffnerf-admin-key";

/** 관리자: 수동 수집 실행(F12), 원문 대기 패치에 원문 붙여넣기(F1-b). 키는 이 탭의 sessionStorage에만 둔다. */
export default function AdminPage() {
  const [adminKey, setAdminKey] = useState("");
  const [games, setGames] = useState<Game[]>([]);
  const [pending, setPending] = useState<PendingPatch[] | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try {
      setAdminKey(sessionStorage.getItem(KEY) ?? "");
    } catch {}
    fetch("/api/games").then((r) => r.json()).then(setGames).catch(() => setMessage("게임 목록을 불러오지 못했습니다."));
  }, []);

  async function call(path: string, init: RequestInit = {}) {
    const res = await fetch(path, { ...init, headers: { ...init.headers, "X-Admin-Key": adminKey, "Content-Type": "application/json" } });
    if (res.status === 401) throw new Error("관리자 키가 올바르지 않습니다.");
    if (!res.ok) throw new Error(`요청 실패 (${res.status})`);
    return res.status === 202 || res.headers.get("content-length") === "0" ? null : res.json();
  }

  async function run(label: string, fn: () => Promise<string | void>) {
    setBusy(true);
    setMessage(`${label} 중...`);
    try {
      setMessage((await fn()) ?? `${label} 완료`);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  const loadPending = () =>
    run("원문 대기 목록 조회", async () => {
      setPending(await call("/api/admin/patches?status=PENDING_CONTENT"));
    });

  function saveKey(v: string) {
    setAdminKey(v);
    try {
      sessionStorage.setItem(KEY, v);
    } catch {}
  }

  return (
    <>
      <h1 className="mb-4 text-2xl font-bold">관리자</h1>

      <label className="mb-6 block">
        <span className="text-sm text-neutral-500">관리자 키 (서버 ADMIN_KEY)</span>
        <input
          type="password"
          value={adminKey}
          onChange={(e) => saveKey(e.target.value)}
          className="mt-1 block w-full rounded border border-neutral-300 bg-transparent px-3 py-2 dark:border-neutral-700"
          autoComplete="off"
        />
      </label>

      <h2 className="mb-2 text-lg font-semibold">수동 수집</h2>
      <p className="mb-2 text-sm text-neutral-500">수집이 끝나면 원문이 있는 패치는 AI 요약·추출이 자동으로 이어집니다.</p>
      <div className="mb-6 flex flex-wrap gap-2">
        {games.map((g) => (
          <button
            key={g.id}
            disabled={busy || !adminKey}
            onClick={() => run(`${g.name} 수집`, async () => {
              const r = await call(`/api/admin/collect/${g.id}`, { method: "POST" });
              return `${g.name}: 새 패치 ${r.collected}개 수집`;
            })}
            className="rounded bg-neutral-900 px-3 py-2 text-sm text-white disabled:opacity-40 dark:bg-neutral-100 dark:text-neutral-900"
          >
            {g.name} 수집
          </button>
        ))}
      </div>

      <h2 className="mb-2 text-lg font-semibold">원문 대기 패치</h2>
      <button
        disabled={busy || !adminKey}
        onClick={loadPending}
        className="mb-3 rounded border border-neutral-300 px-3 py-2 text-sm disabled:opacity-40 dark:border-neutral-700"
      >
        목록 불러오기
      </button>

      <p role="status" className="mb-3 min-h-5 text-sm text-neutral-600 dark:text-neutral-400">{message}</p>

      {pending?.length === 0 && <p className="text-neutral-500">원문 대기 중인 패치가 없습니다.</p>}
      <ul className="space-y-4">
        {pending?.map((p) => (
          <ContentForm
            key={p.id}
            patch={p}
            gameName={games.find((g) => g.id === p.gameId)?.name ?? ""}
            disabled={busy}
            onSubmit={(content) => run(`${p.title} 원문 등록`, async () => {
              await call(`/api/admin/patches/${p.id}/content`, { method: "PUT", body: JSON.stringify({ content }) });
              setPending((list) => list?.filter((x) => x.id !== p.id) ?? null);
              return `${p.title}: 원문 등록 완료, AI 처리를 시작했습니다.`;
            })}
          />
        ))}
      </ul>
    </>
  );
}

function ContentForm({ patch, gameName, disabled, onSubmit }: {
  patch: PendingPatch;
  gameName: string;
  disabled: boolean;
  onSubmit: (content: string) => void;
}) {
  const [content, setContent] = useState("");
  return (
    <li className="rounded-lg border border-neutral-200 p-3 dark:border-neutral-800">
      <p className="font-medium">{gameName} · {patch.title}</p>
      {patch.contentError && <p className="text-xs text-rose-600">수집 실패: {patch.contentError}</p>}
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        rows={6}
        placeholder="공식 패치노트 본문을 붙여넣으세요"
        aria-label={`${patch.title} 원문`}
        className="mt-2 block w-full rounded border border-neutral-300 bg-transparent p-2 text-sm dark:border-neutral-700"
      />
      <button
        disabled={disabled || !content.trim()}
        onClick={() => onSubmit(content)}
        className="mt-2 rounded bg-emerald-600 px-3 py-2 text-sm text-white disabled:opacity-40"
      >
        원문 등록
      </button>
    </li>
  );
}
