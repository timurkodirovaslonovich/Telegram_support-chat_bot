package com.tim.supportBot.entity

import jakarta.persistence.*

/**
 * Represents a person interacting with the bot.  A user may be a customer or an operator.
 *
 * @property telegramId the unique Telegram chat/user id. This field is unique per Telegram account.
 * @property name the user‑supplied nickname or username.
 * @property selectedLanguage the customer's chosen language (e.g. "uz", "ru", "en").  Operators ignore this.
 * @property supportedLanguages a set of language codes the operator can support.  Customers leave this empty.
 * @property isOperator whether this user is a support operator.
 * @property status the current availability status for operators ("available" or "busy").  Customers are always "available".
 */
@Entity
data class SimpleUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val telegramId: Long,



    var name: String? = null,

    var selectedLanguage: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_supported_languages", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "language")
    var supportedLanguages: MutableSet<String> = mutableSetOf(),

    var isOperator: Boolean = false,

    var status: String = "available"
)