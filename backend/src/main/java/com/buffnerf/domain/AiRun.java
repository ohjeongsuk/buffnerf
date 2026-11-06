package com.buffnerf.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class AiRun {

	public enum Task { SUMMARY, EXTRACT, CLASSIFY }
	public enum Status { SUCCESS, FAILED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Patch patch;

	@Enumerated(EnumType.STRING)
	private Task task;

	private String model;
	private String promptVersion;
	private Integer inputTokens;
	private Integer outputTokens;

	@Enumerated(EnumType.STRING)
	private Status status;

	private Instant createdAt = Instant.now();

	protected AiRun() {
	}

	public AiRun(Patch patch, Task task, String model, String promptVersion,
			Integer inputTokens, Integer outputTokens, Status status) {
		this.patch = patch;
		this.task = task;
		this.model = model;
		this.promptVersion = promptVersion;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.status = status;
	}

	public Long getId() { return id; }
	public Patch getPatch() { return patch; }
	public Task getTask() { return task; }
	public String getModel() { return model; }
	public String getPromptVersion() { return promptVersion; }
	public Integer getInputTokens() { return inputTokens; }
	public Integer getOutputTokens() { return outputTokens; }
	public Status getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
}
