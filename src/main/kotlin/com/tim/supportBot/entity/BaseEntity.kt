package com.tim.supportBot.entity

import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.jpa.domain.support.AuditingEntityListener

import java.util.Date


@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
open class BaseEntity (

    //id: Long?
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    //created date: Date?
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    //modified date: Date?
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,

    //created by: String?
    @CreatedBy var createdBy: String? = null,
    //last modified by: String?
    @LastModifiedBy var lastModifiedBy: String? = null,
    //deleted: Boolean = false
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false

)