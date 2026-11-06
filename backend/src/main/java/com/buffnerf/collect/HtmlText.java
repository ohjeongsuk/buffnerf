package com.buffnerf.collect;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** HTML 본문 → 줄 단위 텍스트. 안쪽에 다른 블록이 없는 블록(가장 안쪽 문단·목록 항목)마다 한 줄. */
public final class HtmlText {

	private static final Set<String> BLOCK = Set.of("div", "p", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
			"table", "tr", "td", "th", "section", "article", "blockquote");

	private HtmlText() {
	}

	public static String lines(Element root) {
		List<String> out = new ArrayList<>();
		walk(root, out);
		return String.join("\n", out);
	}

	/** @return e 또는 그 안에 블록 요소가 있으면 true */
	private static boolean walk(Element e, List<String> out) {
		boolean childBlock = false;
		for (Element c : e.children()) childBlock |= walk(c, out);
		boolean block = BLOCK.contains(e.normalName());
		if (block && !childBlock) {
			String t = e.text().replace(' ', ' ').strip();
			if (!t.isEmpty()) out.add(t);
		}
		return block || childBlock;
	}
}
