package com.tim.supportBot.service

import com.tim.supportBot.entity.ChatSession
import com.tim.supportBot.entity.SimpleUser
import com.tim.supportBot.repository.ChatSessionRepository
import com.tim.supportBot.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service containing the core business logic for managing users, sessions and the waiting queue.
 * This service is designed to be stateless except for the in‑memory waiting queue.
 */
@Service
class SupportService(
    private val userRepository: UserRepository,
    private val chatSessionRepository: ChatSessionRepository
) {
    /**
     * Queue for customers waiting for an operator.  Customers remain here until an operator becomes available
     * who supports the customer’s chosen language.
     */
    private val waitingQueue: MutableList<SimpleUser> = mutableListOf()



    /**
     * Save or update a user from a Telegram update.  If the user exists, update the name.
     */
    @Transactional
    fun findOrCreateUser(telegramId: Long, name: String, isOperator: Boolean = false): SimpleUser {
        val existing = userRepository.findByTelegramId(telegramId)
        return if (existing != null) {
            // update name if it has changed
            if (existing.name != name) {
                existing.name = name
                userRepository.save(existing)
            }
            existing
        } else {
            val user = SimpleUser(
                telegramId = telegramId,
                name = name,
                isOperator = isOperator,
                // Operators can set supported languages later via another mechanism
                supportedLanguages = if (isOperator) mutableSetOf("uz", "ru", "en") else mutableSetOf()
            )
            userRepository.save(user)
        }
    }



    /**
     * Persist the customer’s language choice and attempt to assign an operator.  Returns the assigned operator
     * or null if no operator was available (the customer will be placed on the waiting queue).
     */
    @Transactional
    fun selectLanguageForCustomer(customer: SimpleUser, language: String): SimpleUser? {
        val operator = userRepository.findFirstAvailableOperatorByLanguage("available", language)
        if (operator != null) {
            customer.selectedLanguage = language
            userRepository.save(customer)
            return assignOperatorOrQueue(customer)
        } else {
            return null
        }


    }

    /**
     * Try to assign an available operator to the given customer.  If an operator is found, a new chat session
     * is created and the operator status is updated to "busy".  If none is available, the customer is
     * added to the waiting queue and null is returned.
     */
    @Transactional
    fun assignOperatorOrQueue(customer: SimpleUser): SimpleUser? {
        val language = customer.selectedLanguage ?: return null

        val operator = userRepository.findFirstAvailableOperatorByLanguage("available", language)
        return if (operator != null) {
            operator.status = "busy"
            userRepository.save(operator)
            chatSessionRepository.save(ChatSession(customer = customer, operator = operator))
            operator
        } else {
            // Add to queue if not already queued
            if (!waitingQueue.contains(customer)) {
                waitingQueue.add(customer)
            }
            null
        }
    }

    @Transactional
    fun getFirstCustomerInWaitingQueue(): SimpleUser? {
        return waitingQueue.firstOrNull()
    }

    @Transactional
    fun getFirstAvailableOperator(customer: SimpleUser): SimpleUser? {
        val language = customer.selectedLanguage ?: return null
        val operator = userRepository.findFirstAvailableOperatorByLanguage("available", language)
        return operator

    }
    @Transactional
    fun makeOperatorBusy(user: SimpleUser): SimpleUser? {
        return run {
            user.status = "busy"
            userRepository.save(user)
            user
        }

    }
    @Transactional
    fun makeOperatorAvailable(user: SimpleUser): SimpleUser? {
        return run {
            user.status = "available"
            userRepository.save(user)
            user
        }
    }

    /**
     * Mark the session as closed and free the operator.  After closing, the operator will be assigned the next
     * waiting customer (if any) that matches their supported languages.
     */
    @Transactional
    fun endSession(session: ChatSession) {
        if (session.status != "active") {
            return
        }else {
            session.status = "closed"
            chatSessionRepository.save(session)
            // free the operator and assign new customer
            freeOperatorAndAssignNext(session.operator)
        }

        chatSessionRepository.save(session)
        // free the operator and assign new customer
        freeOperatorAndAssignNext(session.operator)
    }

    /**
     * Free an operator by setting their status to available and assign the next waiting customer that matches
     * one of their supported languages.  If a customer is assigned the session is created and the operator
     * becomes busy again.
     */
    @Transactional
    fun freeOperatorAndAssignNext(operator: SimpleUser) {
        operator.status = "available"
        userRepository.save(operator)
        // Find next waiting customer with matching language
        val nextCustomerIndex = waitingQueue.indexOfFirst { c ->
            c.selectedLanguage != null && operator.supportedLanguages.contains(c.selectedLanguage)
        }
        if (nextCustomerIndex != -1) {
            val customer = waitingQueue.removeAt(nextCustomerIndex)
            operator.status = "busy"
            userRepository.save(operator)
            chatSessionRepository.save(ChatSession(customer = customer, operator = operator))
        }
    }

    /**
     * Get the active session for a user.  If the user is an operator, returns the session where they act as operator;
     * if the user is a customer, returns the session where they act as customer.
     */
    fun getActiveSessionForUser(user: SimpleUser): ChatSession? {
        return if (user.isOperator) {
            chatSessionRepository.findByOperatorAndStatus(user, "active").firstOrNull()
        } else {
            chatSessionRepository.findByCustomerAndStatus(user, "active").firstOrNull()
        }
    }
}