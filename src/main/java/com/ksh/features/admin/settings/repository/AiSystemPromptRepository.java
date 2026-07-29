package com.ksh.features.admin.settings.repository;

import com.ksh.entities.AiSystemPrompt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code ai_system_prompts} table.
 *
 * <p>Ordering is alphabetical by name. There is no fallback chain here — a prompt is
 * always picked explicitly by name — so the catalog is sorted the way a human scans a
 * list rather than in any execution order.
 */
@Repository
public interface AiSystemPromptRepository extends JpaRepository<AiSystemPrompt, Long> {

    /** Serializes parity-style enabled-state toggles for one prompt row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AiSystemPrompt p WHERE p.id = :id")
    Optional<AiSystemPrompt> findByIdForUpdate(@Param("id") Long id);

    /**
     * Returns every prompt, enabled or not, sorted for display.
     *
     * @return all prompts ordered by name ascending
     */
    List<AiSystemPrompt> findAllByOrderByNameAsc();

    /**
     * Returns only the enabled prompts, for the pickers that will consume this catalog.
     *
     * @return enabled prompts ordered by name ascending
     */
    List<AiSystemPrompt> findByEnabledTrueOrderByNameAsc();

    /**
     * Looks up a prompt by its unique name — used to surface a duplicate name as an
     * inline field error before the database unique key rejects the insert.
     *
     * @param name the prompt name to look for
     * @return the matching prompt, or empty when the name is free
     */
    Optional<AiSystemPrompt> findByName(String name);

    /** Returns an enabled prompt by its stable feature key. */
    Optional<AiSystemPrompt> findByNameAndEnabledTrue(String name);
}
