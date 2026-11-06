package com.buffnerf.domain;

import jakarta.persistence.*;

@Entity
public class BalanceChange {

	public enum ChangeType { BUFF, NERF, ADJUST }
	public enum Source { AI, DIFF }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Patch patch;

	private String characterName;
	private String attribute;

	@Enumerated(EnumType.STRING)
	private ChangeType changeType;

	private String beforeValue;
	private String afterValue;

	@Column(columnDefinition = "text")
	private String evidence;

	@Enumerated(EnumType.STRING)
	private Source source;

	protected BalanceChange() {
	}

	public BalanceChange(Patch patch, String characterName, String attribute, ChangeType changeType,
			String beforeValue, String afterValue, String evidence, Source source) {
		this.patch = patch;
		this.characterName = characterName;
		this.attribute = attribute;
		this.changeType = changeType;
		this.beforeValue = beforeValue;
		this.afterValue = afterValue;
		this.evidence = evidence;
		this.source = source;
	}

	public Long getId() { return id; }
	public Patch getPatch() { return patch; }
	public String getCharacterName() { return characterName; }
	public String getAttribute() { return attribute; }
	public ChangeType getChangeType() { return changeType; }
	public String getBeforeValue() { return beforeValue; }
	public String getAfterValue() { return afterValue; }
	public String getEvidence() { return evidence; }
	public Source getSource() { return source; }
}
