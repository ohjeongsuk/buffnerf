package com.buffnerf.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PatchRepository extends JpaRepository<Patch, Long> {
	Optional<Patch> findByGameIdAndVersion(Long gameId, String version);

	@Query("select p.version from Patch p where p.game.id = :gameId")
	Set<String> findVersionsByGameId(@Param("gameId") Long gameId);

	@Query("select p from Patch p join fetch p.game where p.id = :id")
	Optional<Patch> findWithGame(@Param("id") Long id);

	List<Patch> findByContentStatus(Patch.ContentStatus status);

	Page<Patch> findByGameIdAndIsPatchNoteTrueOrderByIdDesc(Long gameId, Pageable pageable);

	List<Patch> findByGameIdAndIsPatchNoteTrueOrderByIdAsc(Long gameId);
}
