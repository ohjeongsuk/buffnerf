import type { NextConfig } from "next";

// 브라우저의 /api/* 요청을 백엔드로 넘긴다 (같은 도메인이라 CORS 불필요)
const API_BASE = process.env.API_BASE ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_BASE}/api/:path*` }];
  },
};

export default nextConfig;
