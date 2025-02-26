package org.avisen.sql

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.avisen.storage.Storage
import org.avisen.storage.StoreBlock
import org.avisen.storage.StoreNode
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.dao.id.UIntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class Db(private val database: Database): Storage {
    override fun storeBlock(block: StoreBlock) {
        transaction(database) {
            Blocks.insert {
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
                    it[Blocks.hash],
                    it[Blocks.previousHash],
                    it[Blocks.data],
                    it[Blocks.timestamp],
                    it[Blocks.height],
                    it[Blocks.createDate],
                )
            }
    }

    override fun addNetworkPeer(peer: StoreNode): Unit = transaction(database) {
        NetworkPeers.insert {
            it[address] = peer.address
            it[type] = peer.type
        }
    }

    override fun getNetworkPeer(address: String) = transaction(database) {
        NetworkPeers.selectAll()
            .where { NetworkPeers.address eq address }
            .singleOrNull()
            ?.let {
                StoreNode(
                    it[NetworkPeers.address],
                    it[NetworkPeers.type],
                )
            }
    }

    override fun peers(): List<StoreNode> = transaction(database) {
        NetworkPeers.selectAll().map {
            StoreNode(
                it[NetworkPeers.address],
                it[NetworkPeers.type],
            )
        }
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

            Flyway.configure().dataSource(url, username, password).load().migrate()

            return Db(Database.connect(datasource))
        }
    }
}

object Blocks : UIntIdTable("block") {
    val hash: Column<String> = varchar("hash", 64)
    val previousHash: Column<String> = varchar("previous_hash", 64)
    val data: Column<String> = varchar("data", 20000)
    val timestamp: Column<Long> = long("timestamp")
    val height: Column<UInt> = uinteger("height")
    val createDate: Column<Instant> = timestamp("create_date")
}

object NetworkPeers: UIntIdTable("network_peer") {
    val address: Column<String> = varchar("address", 30)
    val type: Column<String> = varchar("type", 12)
}
