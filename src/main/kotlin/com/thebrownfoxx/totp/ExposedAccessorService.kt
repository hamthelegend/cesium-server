package com.thebrownfoxx.totp

import com.thebrownfoxx.models.totp.EncryptedBase32
import com.thebrownfoxx.models.totp.SavedAccessor
import com.thebrownfoxx.models.totp.UnsavedAccessor
import com.thebrownfoxx.totp.logic.encrypt
import com.thebrownfoxx.totp.logic.generateTotpSecret
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedAccessorService(database: Database): AccessorService {
    object Accessors: Table() {
        val id = integer("id").autoIncrement()
        val name = varchar("name", length = 50)
        val totpSecret = varchar("totp_secret", length = 255)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Accessors)
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    override suspend fun getAll(): List<SavedAccessor> = dbQuery {
        Accessors.selectAll()
            .map {
                SavedAccessor(
                    id = it[Accessors.id],
                    name = it[Accessors.name],
                    totpSecret = EncryptedBase32(it[Accessors.totpSecret]),
                )
            }
    }

    override suspend fun get(id: Int): SavedAccessor? = dbQuery {
        Accessors.select { Accessors.id eq id }
            .map {
                SavedAccessor(
                    id = it[Accessors.id],
                    name = it[Accessors.name],
                    totpSecret = EncryptedBase32(it[Accessors.totpSecret]),
                )
            }
            .singleOrNull()
    }

    override suspend fun add(accessor: UnsavedAccessor): SavedAccessor = dbQuery {
        val databaseAccessor = Accessors.insert {
            it[name] = accessor.name
            it[totpSecret] = accessor.totpSecret.value
        }
        SavedAccessor(
            id = databaseAccessor[Accessors.id],
            name = databaseAccessor[Accessors.name],
            totpSecret = EncryptedBase32(databaseAccessor[Accessors.totpSecret]),
        )
    }

    override suspend fun updateName(id: Int, name: String) {
        dbQuery {
            Accessors.update({ Accessors.id eq id }) {
                it[Accessors.name] = name
            }
        }
    }

    override suspend fun refreshTotpSecret(id: Int) {
        dbQuery {
            Accessors.update({ Accessors.id eq id }) {
                it[totpSecret] = generateTotpSecret().encrypt().value
            }
        }
    }

    override suspend fun delete(id: Int) {
        dbQuery {
            Accessors.deleteWhere { Accessors.id eq id }
        }
    }
}