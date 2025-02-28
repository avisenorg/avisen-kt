package org.avisen.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import org.avisen.storage.Storage
import org.avisen.storage.StoreBlock
import org.avisen.storage.StoreTransactionData
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.dao.id.UIntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class Db(private val database: Database): Storage {
    override fun storeBlock(block: StoreBlock) {
        transaction(database) {
            Blocks.insert {
                it[publisherKey] = block.publisherKey
                it[signature] = block.signature
                it[hash] = block.hash
                it[previousHash] = block.previousHash
                it[data] = block.data
                it[timestamp] = block.timestamp
                it[height] = block.height
                it[createDate] = block.createDate
            }
        }
    }

    override fun getBlock(hash: String): StoreBlock? = transaction(database) {
        Blocks.selectAll().where { Blocks.hash eq hash }.singleOrNull()?.let {
            StoreBlock(
                it[Blocks.publisherKey],
                it[Blocks.signature],
                it[Blocks.hash],
                it[Blocks.previousHash],
                it[Blocks.data],
                it[Blocks.timestamp],
                it[Blocks.height],
                it[Blocks.createDate],
            )
        }
    }

    override fun latestBlock(): StoreBlock? = transaction(database) {
       Blocks.selectAll()
           .orderBy(Blocks.height to SortOrder.DESC)
           .firstOrNull()?.let {
               StoreBlock(
                   it[Blocks.publisherKey],
                   it[Blocks.signature],
                   it[Blocks.hash],
                   it[Blocks.previousHash],
                   it[Blocks.data],
                   it[Blocks.timestamp],
                   it[Blocks.height],
                   it[Blocks.createDate],
               )
           }
    }

    override fun chainSize(): Long = transaction(database) {
        Blocks.selectAll().count()
    }

    override fun blocks(page: Int, size: Int, sort: String?): List<StoreBlock> = transaction(database) {
        val sortOrder = if (sort == null) SortOrder.ASC else SortOrder.valueOf(sort)

        Blocks.selectAll()
            .orderBy(Blocks.height to sortOrder)
            .limit(size)
            .offset((page * size).toLong())
            .map {
                StoreBlock(
                    it[Blocks.publisherKey],
                    it[Blocks.signature],
                    it[Blocks.hash],
                    it[Blocks.previousHash],
                    it[Blocks.data],
                    it[Blocks.timestamp],
                    it[Blocks.height],
                    it[Blocks.createDate],
                )
            }
    }

    override fun blocksFromHeight(height: UInt, page: Int): List<StoreBlock> = transaction(database) {
        Blocks.selectAll()
            .where { Blocks.height greater height }
            .orderBy(Blocks.height to SortOrder.ASC)
            .limit(10)
            .offset((10 * page).toLong())
            .map {
                StoreBlock(
                    it[Blocks.publisherKey],
                    it[Blocks.signature],
                    it[Blocks.hash],
                    it[Blocks.previousHash],
                    it[Blocks.data],
                    it[Blocks.timestamp],
                    it[Blocks.height],
                    it[Blocks.createDate],
                )
            }
    }

    override fun article(id: String) = transaction(database) {
        // Makes sure the id field doesn't have any sql injection attacks
        val isId = "([A-Za-z0-9]){64}".toRegex().matches(id)

        if (!isId) return@transaction null

        val results = Blocks.selectAll()
            .where { Blocks.data.contains("{ \"articles\": [ { \"id\": \"$id\" } ] }") }
            .map {
                it[Blocks.data].articles.firstOrNull()
            }

        results.firstOrNull()
    }

    companion object {
        fun init(url: String, username: String, password: String): Db {
            val datasource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = url
                maximumPoolSize = 3
                this.username = username
                this.password = password
                // Default Exposed settings
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            })

            Flyway.configure().validateMigrationNaming(true).dataSource(url, username, password).load().migrate()

            return Db(Database.connect(datasource))
        }
    }
}

val format = Json { prettyPrint = false }

object Blocks : UIntIdTable("block") {
    val publisherKey: Column<String> = varchar("publisher_key", 100)
    val signature: Column<String> = varchar("signature", 76)
    val hash: Column<String> = varchar("hash", 64)
    val previousHash: Column<String> = varchar("previous_hash", 64)
    val data: Column<StoreTransactionData> = jsonb("data", format)
    val timestamp: Column<Long> = long("timestamp")
    val height: Column<UInt> = uinteger("height")
    val createDate: Column<Instant> = timestamp("create_date")
}
