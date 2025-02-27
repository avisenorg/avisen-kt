package org.avisen.storage

import kotlinx.serialization.Serializable
import java.time.Instant

interface Storage {
    // Blockchain
    fun storeBlock(block: StoreBlock)
    fun getBlock(hash: String): StoreBlock?
    fun latestBlock(): StoreBlock?
    fun chainSize(): Long
    fun blocks(page: Int, size: Int, sort: String?): List<StoreBlock>
    fun blocksFromHeight(height: UInt, page: Int): List<StoreBlock>
    fun article(id: String): StoreArticle?
}

data class StoreBlock(
    val hash: String,
    val previousHash: String,
    val data: StoreTransactionData,
    val timestamp: Long,
    val height: UInt,
    val createDate: Instant = Instant.now(),
)

@Serializable
data class StoreTransactionData(
    val articles: List<StoreArticle>,
)

@Serializable
data class StoreArticle (
    val id: String,
    val publisherKey: String,
    val byline: String,
    val headline: String,
    val section: String,
    val content: String,
    val date: String,
    val signature: String,
)
