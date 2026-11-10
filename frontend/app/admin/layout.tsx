import type { Metadata } from "next";

// page.tsx가 클라이언트 컴포넌트라 metadata를 여기서 준다: 검색 엔진에 잡히지 않게
export const metadata: Metadata = { title: "관리자 | BuffLog", robots: { index: false, follow: false } };

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return children;
}
