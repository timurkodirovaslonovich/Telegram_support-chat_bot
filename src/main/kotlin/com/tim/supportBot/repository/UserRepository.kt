package com.tim.supportBot.repository

import com.tim.supportBot.entity.SimpleUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * JPA repository for accessing SimpleUser entities.
 */
interface UserRepository : JpaRepository<SimpleUser, Long> {

    /**
     * Find a user by their Telegram id.
     */
    fun findByTelegramId(telegramId: Long): SimpleUser?

    /**
     * Find the first available operator who supports the given language.
     *
     * @param status the operator's status, expected to be "available" or "busy"
     * @param language the language that must be contained in the supportedLanguages set
     */
    @Query(
        "SELECT u FROM SimpleUser u JOIN u.supportedLanguages l " +
                "WHERE u.isOperator = true AND u.status = :status AND l = :language ORDER BY u.id ASC"
    )
    fun findFirstAvailableOperatorByLanguage(status: String, language: String): SimpleUser?
}