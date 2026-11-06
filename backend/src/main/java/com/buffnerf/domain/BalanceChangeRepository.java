package com.buffnerf.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BalanceChangeRepository extends JpaRepository<BalanceChange, Long> {
	List<BalanceChange> findByPatchId(Long patchId);

	@Query("select b from BalanceChange b where b.patch.id in :patchIds")
	List<BalanceChange> findByPatchIds(@Param("patchIds") Collection<Long> patchIds);

	@Query("""
			select b from BalanceChange b join fetch b.patch p
			where p.game.id = :gameId and b.characterName = :key and p.isPatchNote = true
			order by p.id, b.id""")
	List<BalanceChange> timeline(@Param("gameId") Long gameId, @Param("key") String key);

	@Query("""
			select b.characterName, count(distinct b.patch.id) from BalanceChange b
			where b.patch.game.id = :gameId and b.patch.isPatchNote = true
			group by b.characterName order by b.characterName""")
	List<Object[]> characterPatchCounts(@Param("gameId") Long gameId);

	void deleteByPatchIdAndSource(Long patchId, BalanceChange.Source source);
}
