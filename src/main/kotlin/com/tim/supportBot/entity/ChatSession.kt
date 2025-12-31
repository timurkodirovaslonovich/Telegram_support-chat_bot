package com.tim.supportBot.entity

import jakarta.persistence.*

/**
 * Represents an active or closed support session between a customer and an operator.
 * When a session is created both customer and operator fields are required.
 * The status can be "active" or "closed".
 */
@Entity
data class ChatSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    var customer: SimpleUser,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "operator_id")
    var operator: SimpleUser,


    var status: String = "active"
)