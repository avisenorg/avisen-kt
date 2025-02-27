package org.avisen.blockchain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.avisen.crypto.hash
import org.avisen.crypto.hexStringToByteArray
import org.avisen.crypto.toPublicKey
import org.avisen.crypto.verifySignature
import org.avisen.storage.Storage
import org.avisen.storage.StoreArticle
import org.avisen.storage.StoreBlock
import org.avisen.storage.StoreTransactionData
import java.time.Instant

/**
 * The chain must be initialized with a genesis block.
 */
data class Blockchain(
    val storage: Storage,
    private val unprocessedArticles: MutableList<Article> = mutableListOf(),
) {
    val unprocessedCount get() = unprocessedArticles.size

    fun getBlock(hash: String): Block? {
        return storage.getBlock(hash)?.toBlock()
    }

    /**
     * Returns a set of blocks from the blockchain by page number and size.
     * If size is null, use the default of 10.
     *
     * If fromHeight is specified, the blocks are returned by pages of 10 in descending order by height.
     */
    fun chain(page: Int, size: Int?, sort: String?, fromHeight: UInt?): List<Block> {

        return if (page < 0) {
            emptyList()
        } else {
            if (fromHeight == null) {
                storage.blocks(page, size ?: 10, sort).toBlocks()
            } else {
                storage.blocksFromHeight(fromHeight, page).toBlocks()
            }
        }
    }

    fun getArticle(signature: String): Article? {
        return storage.article(signature)?.toArticle()
    }

    fun processArticle(article: Article): ProcessedArticle {
        // First verify the Article's signature
        val verified = verifySignature(
            article.publisherKey.toPublicKey(),
            article.byline + article.headline + article.section + article.content + article.date,
            article.signature.hexStringToByteArray()
        )

        if (!verified) return ProcessedArticle(false, null)

        unprocessedArticles.add(article)

        /*
         * if the unprocessed article count is 10 or greater,
         * mint the articles into a Block and add it to the chain.
         */
        if (unprocessedArticles.size >= 10) {
            val timestamp = Instant.now().toEpochMilli()
            val latestBlock = storage.latestBlock()
            val previousHash = latestBlock!!.hash

            val newBlock = Block(
                previousHash,
                TransactionData(unprocessedArticles),
                timestamp,
                latestBlock.height + 1u
            )

            storage.storeBlock(newBlock.toStore())

            unprocessedArticles.clear()

            return ProcessedArticle(true, newBlock)
        }
        return ProcessedArticle(true, null)
    }

    /**
     * When adding a block from another node, verify and process it.
     * If it fails to verify, do not add it to the chain.
     */
    fun processBlock(block: Block): Boolean {
        val latestBlock = storage.latestBlock()

        if (latestBlock != null) {
            if (block.height <= storage.chainSize().toUInt() - 1u) return false

            if (block.previousHash != latestBlock.hash) return false

            if (block.timestamp <= latestBlock.timestamp) return false
        }

        storage.storeBlock(block.toStore())
        return true
    }
}

@Serializable
data class Block(
    val previousHash: String,
    // Data represents articles that have been minted into a json string
    val data: TransactionData,
    val timestamp: Long,
    val height: UInt,
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault val hash: String = hash(previousHash + timestamp + data),
) {
    companion object {
        fun genesis(): Block {
            return Block(
                "",
                TransactionData(listOf()),
                Instant.now().toEpochMilli(),
                0u,
            )
        }
    }
}

@Serializable
data class TransactionData(
    val articles: List<Article>,
)

@Serializable
data class Article(
    /**
     * The public key corresponding to the publisher of the article
     */
    val publisherKey: String,
    val byline: String,
    val headline: String,
    /**
     * The news section for the article (this may be different per publisher)
     */
    val section: String,
    /**
     * The html content of the article
     */
    val content: String,
    /**
     * The date in format YYYY-MM-dd
     */
    val date: String,
    /**
     * The ECDSA signature of the Article (byline + headline + section + content + date)
     */
    val signature: String,
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault val id: String = hash(publisherKey + byline + headline + section + content + date),
) {

}

data class ProcessedArticle(
    val processed: Boolean,
    val block: Block?,
)

fun Block.toStore() = StoreBlock(hash, previousHash, data.toStoreTransactionData(), timestamp, height)
fun StoreBlock.toBlock() = Block(previousHash, data.toTransactionData(), timestamp, height, hash)
fun List<StoreBlock>.toBlocks() = map { it.toBlock() }
fun TransactionData.toStoreTransactionData() = StoreTransactionData(
    articles.map { it.toStoreArticle() }
)
fun StoreTransactionData.toTransactionData() = TransactionData(articles.map { it.toArticle() })

fun Article.toStoreArticle() = StoreArticle(
    id,
    publisherKey,
    byline,
    headline,
    section,
    content,
    date,
    signature,
)

fun StoreArticle.toArticle() = Article(
    publisherKey,
    byline,
    headline,
    section,
    content,
    date,
    signature,
    id,
)
