package com.tylerkindy.betrayal

import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

fun connectToDatabase() {
    Database.connect(
        HikariDataSource().apply {
            jdbcUrl = System.getenv("JDBC_DATABASE_URL")
        }
    )
}
