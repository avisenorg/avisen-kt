package org.avisen.storage

import java.time.Instant

interface Storage {
    // Blockchain
    fun storeBlock(block: StoreBlock)
    fun getBlock(hash: String): StoreBlock?
    fun latestBlock(): StoreBlock?
    fun chainSize(): Long
    fun blocks(page: Int, size: Int, sort: String?): List<StoreBlock>
    fun blocksFromHeight(height: UInt, page: Int): List<StoreBlock>

    // Network
    fun addNetworkPeer(peer: StoreNode)
    fun getNetworkPeer(address: String): StoreNode?
    fun peers(): List<StoreNode>
}

data class StoreBlock(
    val hash: String,
    val previousHash: String,
    val data: String,
    val timestamp: Long,
    val height: UInt,
    val createDate: Instant = Instant.now()
)

data class StoreNode(
    val address: String,
    val type: String,
)
