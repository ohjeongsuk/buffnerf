package com.buffnerf.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Patch {

	public enum ContentSource { CRAWLED, MANUAL }
	public enum ContentStatus { READY, PENDING_CONTENT }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Game game;

	private String version;
	private String title;
	private Instant publishedAt;

	@Column(columnDefinition = "text")
	private String rawContent;

	@Enumerated(EnumType.STRING)
	private ContentSource contentSource;

	@Enumerated(EnumType.STRING)
	private ContentStatus contentStatus = ContentStatus.PENDING_CONTENT;

	@Column(columnDefinition = "text")
	private String contentError;

	@Column(columnDefinition = "text")
	private String summaryKo;

	private boolean isPatchNote = true;

	private String sourceUrl;

	protected Patch() {
	}

	public Patch(Game game, String version, String title, Instant publishedAt) {
		this.game = game;
		this.version = version;
		this.title = title;
		this.publishedAt = publishedAt;
	}

	/** 원문 확보: READY로 전환 */
	public void setContent(String rawContent, ContentSource source) {
		this.rawContent = rawContent;
		this.contentSource = source;
		this.contentStatus = ContentStatus.READY;
		this.contentError = null;
	}

	/** 원문 수집 실패: PENDING_CONTENT로 두고 이유 기록 */
	public void markContentFailed(String error) {
		this.contentStatus = ContentStatus.PENDING_CONTENT;
		this.contentError = error;
	}

	public void setSummaryKo(String summaryKo) { this.summaryKo = summaryKo; }
	public void setPatchNote(boolean patchNote) { this.isPatchNote = patchNote; }
	public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

	public Long getId() { return id; }
	public Game getGame() { return game; }
	public String getVersion() { return version; }
	public String getTitle() { return title; }
	public Instant getPublishedAt() { return publishedAt; }
	public String getRawContent() { return rawContent; }
	public ContentSource getContentSource() { return contentSource; }
	public ContentStatus getContentStatus() { return contentStatus; }
	public String getContentError() { return contentError; }
	public String getSummaryKo() { return summaryKo; }
	public boolean isPatchNote() { return isPatchNote; }
	public String getSourceUrl() { return sourceUrl; }
}
