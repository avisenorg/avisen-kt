package org.avisen.blockchain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.avisen.crypto.hash
import org.avisen.crypto.hexStringToByteArray
import org.avisen.crypto.sign
import org.avisen.crypto.toHexString
import org.avisen.crypto.toPrivateKey
import org.avisen.crypto.toPublicKey
import org.avisen.crypto.verifySignature
import org.avisen.storage.Storage
import org.avisen.storage.StoreArticle
import org.avisen.storage.StoreBlock
import org.avisen.storage.StorePublisher
import org.avisen.storage.StoreTransactionData
import java.time.Instant

/**
 * The chain must be initialized with a genesis block.
 */
data class Blockchain(
    val storage: Storage,
    // private, public
    val publisherKeys: Pair<String, String>,
    private val unprocessedArticles: MutableList<Article> = mutableListOf(),
    private val unprocessedPublishers: MutableSet<Publisher> = mutableSetOf(),
) {
    val unprocessedArticlesCount get() = unprocessedArticles.size
    val unprocessedPublishersCount get() = unprocessedPublishers.size

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
            article.authorKey.toPublicKey(),
            article.byline + article.headline + article.section + article.contentHash + article.date,
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

            val data = TransactionData(unprocessedArticles.toList(), processPublishers(latestBlock.data.publishers.toPublishers()))
            val newHeight = latestBlock.height + 1u
            val newBlock = Block(
                publisherKeys.second,
                sign(publisherKeys.first.toPrivateKey(), previousHash + data + timestamp + newHeight).toHexString(),
                previousHash,
                data,
                timestamp,
                latestBlock.height + 1u
            )

            storage.storeBlock(newBlock.toStore())

            unprocessedArticles.clear()
            unprocessedPublishers.clear()

            return ProcessedArticle(true, newBlock)
        }
        return ProcessedArticle(true, null)
    }

    /**
     * @param publisher the publisher to add to the list of publishers
     * @param signature the signature created by the currently running node
     */
    fun acceptPublisher(publisher: Publisher, signature: String): Boolean {

        if (!verifySignature(publisherKeys.second.toPublicKey(), publisher.publicKey, signature.hexStringToByteArray())) return false

        return unprocessedPublishers.add(publisher)
    }

    private fun processPublishers(existingPublishers: Set<Publisher>): Set<Publisher> {
        return existingPublishers + unprocessedPublishers
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

            if (latestBlock.data.publishers.none { it.publicKey == block.publisherKey }) return false

            if (!verifySignature(block.publisherKey.toPublicKey(), block.previousHash + block.data + block.timestamp + block.height, block.signature.hexStringToByteArray())) return false
        }

        storage.storeBlock(block.toStore())
        return true
    }
}

@Serializable
data class Block(
    val publisherKey: String,
    val signature: String,
    val previousHash: String,
    // Data represents articles that have been minted into a json string
    val data: TransactionData,
    val timestamp: Long,
    val height: UInt,
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault val hash: String = hash(previousHash + timestamp + data),
) {
    companion object {
        fun genesis(genesisPublisherKey: String = "", genesisSigningKey: String = ""): Block {
            val data = TransactionData(listOf(), setOf(Publisher(genesisPublisherKey)))
            val timestamp = Instant.now().toEpochMilli()
            val height = 0u
            return Block(
                genesisPublisherKey,
                sign(genesisSigningKey.toPrivateKey(), "" + data + timestamp + height).toHexString(),
                "",
                data,
                timestamp,
                height,
            )
        }
    }
}

@Serializable
data class TransactionData(
    val articles: List<Article>,
    val publishers: Set<Publisher>,
)

@Serializable
data class Article(
    /**
     * The public key corresponding to the publisher of the article
     */
    val authorKey: String,
    val byline: String,
    val headline: String,
    /**
     * The news section for the article (this may be different per publisher)
     */
    val section: String,
    /**
     * The content hash of the article
     */
    val contentHash: String,
    /**
     * The date in format YYYY-MM-dd
     */
    val date: String,
    /**
     * The ECDSA signature of the Article (byline + headline + section + content + date)
     */
    val signature: String,
    @OptIn(ExperimentalSerializationApi::class) @EncodeDefault val id: String = hash(authorKey + byline + headline + section + contentHash + date),
)

@Serializable
data class Publisher(
    val publicKey: String,
)

data class ProcessedArticle(
    val processed: Boolean,
    val block: Block?,
)

fun Block.toStore() = StoreBlock(publisherKey, signature, hash, previousHash, data.toStoreTransactionData(), timestamp, height)
fun StoreBlock.toBlock() = Block(publisherKey, signature, previousHash, data.toTransactionData(), timestamp, height, hash)
fun List<StoreBlock>.toBlocks() = map { it.toBlock() }
fun TransactionData.toStoreTransactionData() = StoreTransactionData(
    articles.map { it.toStoreArticle() },
    publishers.map { it.toStorePublisher() }.toSet(),
)
fun StoreTransactionData.toTransactionData() = TransactionData(articles.map { it.toArticle() }, publishers.map { it.toPublisher()}.toSet())
fun StorePublisher.toPublisher() = Publisher(publicKey)
fun Set<StorePublisher>.toPublishers() = map { it.toPublisher() }.toSet()
fun Publisher.toStorePublisher() = StorePublisher(publicKey)
fun Article.toStoreArticle() = StoreArticle(
    id,
    authorKey,
    byline,
    headline,
    section,
    contentHash,
    date,
    signature,
)

fun StoreArticle.toArticle() = Article(
    publisherKey,
    byline,
    headline,
    section,
    contentHash,
    date,
    signature,
    id,
)
