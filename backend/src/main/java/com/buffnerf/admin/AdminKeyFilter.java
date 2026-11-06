package com.buffnerf.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** /api/admin/** 는 X-Admin-Key 헤더가 서버 환경변수 ADMIN_KEY와 같아야 통과. 키가 비어 있으면 전부 거부. */
@Component
class AdminKeyFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Admin-Key";

	private final byte[] key;

	AdminKeyFilter(@Value("${admin.key:}") String key) {
		this.key = key.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest req) {
		return !req.getRequestURI().startsWith("/api/admin/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String given = req.getHeader(HEADER);
		// 상수 시간 비교 (타이밍 공격 방지)
		if (key.length == 0 || given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
			res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		chain.doFilter(req, res);
	}
}
