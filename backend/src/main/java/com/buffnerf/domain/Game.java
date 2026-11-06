package com.buffnerf.domain;

import jakarta.persistence.*;

@Entity
public class Game {

	public enum Source { LOL, STEAM }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Enumerated(EnumType.STRING)
	private Source source;

	private String externalId;

	protected Game() {
	}

	public Game(String name, Source source, String externalId) {
		this.name = name;
		this.source = source;
		this.externalId = externalId;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public Source getSource() { return source; }
	public String getExternalId() { return externalId; }
}
