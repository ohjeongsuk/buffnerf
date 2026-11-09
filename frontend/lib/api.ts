// 백엔드 응답 타입 (backend/.../api/PublicController.java 레코드와 같은 모양)
export type Source = "LOL" | "STEAM";
export type ChangeType = "BUFF" | "NERF" | "ADJUST";

export type Game = { id: number; name: string; source: Source };

export type PatchSummary = {
  id: number;
  version: string;
  title: string;
  publishedAt: string | null;
  summaryKo: string | null;
  buffs: number;
  nerfs: number;
};

export type Page<T> = { content: T[]; page: { totalElements: number; totalPages: number; number: number } };

export type Change = {
  characterKey: string;
  characterName: string;
  attribute: string;
  changeType: ChangeType;
  before: string | null;
  after: string | null;
  evidence: string | null;
  source: "AI" | "DIFF";
};

export type Accuracy = { matched: number; diffCount: number; aiCount: number; recall: number | null; precision: number | null };

export type PatchDetail = {
  id: number;
  gameId: number;
  gameName: string;
  version: string;
  title: string;
  publishedAt: string | null;
  summaryKo: string | null;
  contentStatus: "READY" | "PENDING_CONTENT";
  contentSource: "CRAWLED" | "MANUAL" | null;
  sourceUrl: string | null;
  changes: Change[];
  accuracy: Accuracy | null;
};

export type CharacterView = { key: string; name: string; patchCount: number };

export type Timeline = {
  key: string;
  name: string;
  entries: { patchId: number; version: string; title: string; publishedAt: string | null; changes: Change[] }[];
};

const API_BASE = process.env.API_BASE ?? "http://localhost:8080";

/** 서버 컴포넌트용. 60초 캐시 (패치 데이터는 하루 한 번 바뀜) */
export async function api<T>(path: string): Promise<T | null> {
  const res = await fetch(`${API_BASE}${path}`, { next: { revalidate: 60 } });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`${path}: ${res.status}`);
  return res.json();
}

/** 화면에는 AI 결과(근거 문장 있음)를 우선, 없으면 코드 diff (백엔드 PublicController.shown과 같은 규칙) */
export function shown(changes: Change[]): Change[] {
  const want = changes.some((c) => c.source === "AI") ? "AI" : "DIFF";
  return changes.filter((c) => c.source === want);
}

/** 롤 Data Dragon 스탯 키 → 화면 이름 (백엔드 Accuracy.STAT_KEYS와 같은 목록) */
const STAT_LABEL: Record<string, string> = {
  hp: "체력", hpperlevel: "레벨당 체력", mp: "마나", mpperlevel: "레벨당 마나", movespeed: "이동 속도",
  armor: "방어력", armorperlevel: "레벨당 방어력", spellblock: "마법 저항력", spellblockperlevel: "레벨당 마법 저항력",
  attackrange: "사거리", hpregen: "체력 재생", hpregenperlevel: "레벨당 체력 재생", mpregen: "마나 재생",
  mpregenperlevel: "레벨당 마나 재생", attackdamage: "공격력", attackdamageperlevel: "레벨당 공격력",
  attackspeed: "기본 공격 속도", attackspeedperlevel: "레벨당 공격 속도",
};

export function attributeLabel(attribute: string): string {
  return STAT_LABEL[attribute] ?? attribute;
}

/**
 * 게임 구분색. 상향(초록)·하향(빨강)·조정(노랑)과 겹치지 않게 파랑·주황·보라, 표시(태그·띠)에만 쓴다.
 * Tailwind가 클래스를 찾을 수 있게 문자열을 그대로 둔다.
 */
const GAME_COLOR: Record<string, { tag: string; bar: string; dot: string }> = {
  "League of Legends": { tag: "bg-sky-100 text-sky-800 dark:bg-sky-900/50 dark:text-sky-200", bar: "border-t-sky-500 dark:border-t-sky-500", dot: "bg-sky-500" },
  Overwatch: { tag: "bg-orange-100 text-orange-800 dark:bg-orange-900/50 dark:text-orange-200", bar: "border-t-orange-500 dark:border-t-orange-500", dot: "bg-orange-500" },
  "Eternal Return": { tag: "bg-violet-100 text-violet-800 dark:bg-violet-900/50 dark:text-violet-200", bar: "border-t-violet-500 dark:border-t-violet-500", dot: "bg-violet-500" },
};
const NO_COLOR = { tag: "bg-neutral-100 text-neutral-600 dark:bg-neutral-800 dark:text-neutral-300", bar: "border-t-neutral-300", dot: "bg-neutral-400" };

export function gameColor(name: string) {
  return GAME_COLOR[name] ?? NO_COLOR;
}

/** 캐릭터 한 명의 판정: 전부 상향이면 BUFF, 전부 하향이면 NERF, 섞였거나 조정이면 ADJUST */
export function verdict(cs: Change[]): ChangeType {
  if (cs.every((c) => c.changeType === "BUFF")) return "BUFF";
  if (cs.every((c) => c.changeType === "NERF")) return "NERF";
  return "ADJUST";
}

export function fmtDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul" }) : "";
}
