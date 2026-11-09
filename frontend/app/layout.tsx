import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";
import { Geist } from "next/font/google";
import { cn } from "@/lib/utils";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export const metadata: Metadata = {
  title: "BuffLog",
  description: "롤·오버워치·이터널 리턴 패치노트를 AI로 요약하고 캐릭터별 버프/너프를 타임라인으로 보여줍니다",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko" className={cn("font-sans", geist.variable)}>
      <body className="flex min-h-screen flex-col antialiased">
        <header className="border-b border-neutral-200 dark:border-neutral-800">
          <nav className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3">
            <Link href="/" className="text-lg font-bold">
              <span className="text-emerald-600">Buff</span>Log
            </Link>
            <Link href="/admin" className="text-sm text-neutral-500 hover:underline">
              관리자
            </Link>
          </nav>
        </header>
        <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-6">{children}</main>
        <footer className="mx-auto w-full max-w-3xl space-y-1 px-4 pt-4 pb-8 text-xs text-neutral-400">
          <p>League of Legends는 Riot Games, Inc., Overwatch는 Blizzard Entertainment, Inc., 이터널 리턴은 님블뉴런의 상표입니다.</p>
          {/* Riot "Legal Jibber Jabber" 정책 §6이 요구하는 고지 (원문 그대로) */}
          <p>
            BuffLog was created under Riot Games&apos; &quot;Legal Jibber Jabber&quot; policy using assets owned by Riot Games.
            Riot Games does not endorse or sponsor this project.
          </p>
          <p>BuffLog는 Riot Games의 &quot;Legal Jibber Jabber&quot; 정책에 따라 만들었으며, Riot Games가 보증하거나 후원하지 않습니다.</p>
        </footer>
      </body>
    </html>
  );
}
