package com.tim.supportBot.repository

import com.tim.supportBot.entity.ChatSession
import com.tim.supportBot.entity.SimpleUser
import org.springframework.data.jpa.repository.JpaRepository

/**
 * JPA repository for ChatSession entities.
 */
interface ChatSessionRepository : JpaRepository<ChatSession, Long> {

    /**
     * Find an active session for a customer.
     */
    fun findByCustomerAndStatus(customer: SimpleUser, status: String): List<ChatSession>

    /**
     * Find an active session for an operator.
     */
    fun findByOperatorAndStatus(operator: SimpleUser, status: String): List<ChatSession>
}