package com.tylerkindy.betrayal.db

import io.kotest.core.TestConfiguration
import io.kotest.core.listeners.ProjectListener
import io.kotest.core.spec.AutoScan
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import java.io.File

@AutoScan
object SetupDatabaseProjectListener : ProjectListener {
    override suspend fun beforeProject() {
        Database.registerJdbcDriver(
            "jdbc:tc:postgresql",
            "org.postgresql.Driver",
            PostgreSQLDialect.dialectName
        )
        Database.connect(
            "jdbc:tc:postgresql:12:///betrayal-test?TC_DAEMON=true"
        )
    }
}

val migrationsSql: List<String> = File(
    SetupDatabaseProjectListener.javaClass.getResource(
        "/migrations.sql"
    )!!.toURI()
).readText().split("\n\n")

fun TestConfiguration.useDatabase() {
    beforeEach {
        transaction {
            SchemaUtils.createSchema(Schema("public"))
            migrationsSql.forEach { TransactionManager.current().exec(it) }
        }
    }
    afterEach {
        transaction {
            SchemaUtils.dropSchema(Schema("public"), cascade = true)
        }
    }
}
