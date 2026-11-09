package com.buffnerf.ai;

import com.buffnerf.domain.BalanceChange;
import com.buffnerf.domain.Game;
import com.buffnerf.domain.Patch;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 프롬프트를 바꾸면 VERSION을 올린다 (AiRun 재호출 방지 기준). */
final class Prompts {

	static final String VERSION = "v2"; // v2: 기본 모델을 Gemini 무료 등급으로 변경

	private Prompts() {
	}

	record Summary(@JsonPropertyDescription("정확히 3개의 한국어 문장") List<String> lines) {
	}

	record Extraction(List<Item> changes) {
	}

	record Item(
			@JsonPropertyDescription("캐릭터 이름, 원문 표기 그대로") String characterName,
			@JsonPropertyDescription("변경 항목") String attribute,
			BalanceChange.ChangeType changeType,
			@JsonPropertyDescription("변경 전 값, 없으면 빈 문자열") String before,
			@JsonPropertyDescription("변경 후 값, 없으면 빈 문자열") String after,
			@JsonPropertyDescription("근거가 된 원문 한 줄을 그대로 복사") String evidence) {
	}

	static final String SYSTEM = "너는 게임 패치노트를 정확하게 정리하는 편집자다. 원문에 없는 내용은 절대 만들지 않는다.";

	static String summary(Patch p) {
		return """
				아래 %s 패치노트를 한국어 3줄로 요약해라.
				- 정확히 3문장, 각 문장은 한 줄.
				- 가장 영향이 큰 변경(캐릭터 상향·하향, 시스템 변화)부터.
				- 원문이 영어여도 한국어로 쓴다.

				<title>%s</title>
				<patch_notes>
				%s
				</patch_notes>""".formatted(p.getGame().getName(), p.getTitle(), p.getRawContent());
	}

	static String extract(Patch p) {
		boolean lol = p.getGame().getSource() == Game.Source.LOL;
		return """
				아래 %s 패치노트에서 캐릭터별 밸런스 변경을 모두 추출해라.
				- 캐릭터(챔피언, 영웅, 실험체)의 변경만. 아이템·룬·맵·시스템 변경은 제외.
				- 수치가 여러 개 바뀌면 항목마다 따로 한 건씩.
				- changeType: 플레이어에게 유리하면 BUFF, 불리하면 NERF, 재설계처럼 판단이 어려우면 ADJUST.
				- evidence: 근거가 된 원문 한 줄을 고치지 말고 그대로 복사.
				%s
				<patch_notes>
				%s
				</patch_notes>""".formatted(p.getGame().getName(), lol ? LOL_RULES : "", p.getRawContent());
	}

	private static final String LOL_RULES = """
			- 기본 능력치 변경은 attribute에 아래 키를 쓰고, before/after에는 단위(%%, +)를 뺀 숫자만 쓴다.
			  hp(체력) hpperlevel(레벨당 체력) mp(마나) mpperlevel(레벨당 마나) movespeed(이동 속도)
			  armor(방어력) armorperlevel(레벨당 방어력) spellblock(마법 저항력) spellblockperlevel(레벨당 마법 저항력)
			  attackrange(사거리) hpregen(체력 재생) hpregenperlevel(레벨당 체력 재생) mpregen(마나 재생)
			  mpregenperlevel(레벨당 마나 재생) attackdamage(공격력) attackdamageperlevel(레벨당 공격력)
			  attackspeed(기본 공격 속도) attackspeedperlevel(레벨당 공격 속도)
			  예: "체력: 640 + 레벨당 99 ⇒ 590 + 레벨당 107" → hp 640→590, hpperlevel 99→107 두 건.
			- 스킬 변경은 attribute를 "Q - 스킬명: 항목" 형식으로, before/after는 원문 값 그대로.
			""";
}
