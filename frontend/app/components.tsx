import { attributeLabel, type Change, type ChangeType } from "@/lib/api";

const BADGE: Record<ChangeType, { label: string; cls: string }> = {
  BUFF: { label: "상향", cls: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/50 dark:text-emerald-300" },
  NERF: { label: "하향", cls: "bg-rose-100 text-rose-800 dark:bg-rose-900/50 dark:text-rose-300" },
  ADJUST: { label: "조정", cls: "bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-300" },
};

export function Badge({ type }: { type: ChangeType }) {
  const b = BADGE[type];
  return <span className={`inline-block shrink-0 rounded px-1.5 py-0.5 text-xs font-semibold ${b.cls}`}>{b.label}</span>;
}

/** 근거 문장이 위에 보이는 "항목 전 → 후"와 같은 내용이면 true (예: "피해량: 75 ⇒ 80"). 문장형 근거는 false */
function repeatsRow(c: Change): boolean {
  if (!c.evidence || !c.before || !c.after) return false;
  const sq = (s: string) => s.replace(/\s+/g, "");
  const rest = sq(c.evidence).replace(sq(c.before), "").replace(sq(c.after), "").replace(/[→⇒:>-]/g, "");
  return sq(attributeLabel(c.attribute)).includes(rest);
}

/** 변경 한 줄: 배지, 항목, 전→후, 근거 문장(펼치기, 위 내용과 겹치면 생략) */
export function ChangeRow({ c }: { c: Change }) {
  return (
    <li className="py-2">
      <div className="flex flex-wrap items-baseline gap-2">
        <Badge type={c.changeType} />
        <span className="font-medium">{attributeLabel(c.attribute)}</span>
        {(c.before || c.after) && (
          <span className="text-sm text-neutral-600 dark:text-neutral-400">
            {c.before ?? "—"} → <strong className="text-neutral-900 dark:text-neutral-100">{c.after ?? "—"}</strong>
          </span>
        )}
      </div>
      {c.evidence && !repeatsRow(c) && (
        <details className="mt-1 text-sm">
          <summary className="cursor-pointer text-neutral-500">{c.source === "AI" ? "원문 근거" : "출처"}</summary>
          <p className="mt-1 rounded bg-neutral-100 p-2 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300">
            {c.evidence}
          </p>
        </details>
      )}
    </li>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <p className="rounded border border-dashed border-neutral-300 p-6 text-center text-neutral-500 dark:border-neutral-700">{children}</p>;
}
