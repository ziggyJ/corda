package net.corda.node.services.persistence

import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.currentDBSession
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

class DBManualMigration(val database: CordaPersistence) {

    @Entity
    @Table(name = "node_migration_checks")
    class DBMigrationCheck(
            @Id
            @Column(name = "version")
            var version: String = "",

            @Id
            @Column(name = "migration_check_done")
            var migrationCheckDone: Boolean = false
    )

    fun isMigrationCheckDone(version: String): Boolean {
        return database.transaction {
            val session = currentDBSession()
            val result = session.find(DBMigrationCheck::class.java, version)
            result?.migrationCheckDone ?: true
        }
    }

    fun migrationComplete(version: String) {
        database.transaction {
            val session = currentDBSession()
            session.find(DBMigrationCheck::class.java, version)?.migrationCheckDone = true
        }
    }
}