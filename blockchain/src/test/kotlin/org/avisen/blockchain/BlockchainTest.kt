package org.avisen.blockchain

import org.avisen.crypto.generateKeyPair
import org.avisen.crypto.getString
import org.avisen.crypto.setupSecurity
import org.avisen.crypto.sign
import org.avisen.crypto.toHexString
import org.avisen.storage.Storage
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.avisen.crypto.toPrivateKey
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate

class BlockchainTest : DescribeSpec({
    setupSecurity()

    val publisherPublicKey = "MEkwEwYHKoZIzj0CAQYIKoZIzj0DAQEDMgAEje4IzKZB+pqvn5iT9QLgX97HtigQYQ6D5BXVWh8vBfvGPCzDtmSOhsji753dPciU"
    val publisherSigningKey = "MHsCAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQEEYTBfAgEBBBitZRQG15sGSSyBKRBZ96XDkx+t03p3P4ugCgYIKoZIzj0DAQGhNAMyAASN7gjMpkH6mq+fmJP1AuBf3se2KBBhDoPkFdVaHy8F+8Y8LMO2ZI6GyOLvnd09yJQ="

    describe("Blockchain") {
        describe("getBlock") {
            it("should return a Block if found") {
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))

                val randomBlock = randomBlock("", 1u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey)
                every { storage.getBlock(any()) } returns randomBlock.toStore()

                blockchain.getBlock(randomBlock.hash) shouldBe randomBlock
            }
        }

        describe("chain") {
            it("should return a list of 10 blocks") {
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))

                every { storage.blocks(any(), any(), any()) } returns listOf(
                    Block.genesis(publisherPublicKey, publisherSigningKey).toStore(),
                    randomBlock("", 1u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 2u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 3u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 4u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 5u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 6u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 7u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 8u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 9u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                )

                blockchain.chain(0, null, null, null) shouldHaveSize 10
            }

            it("should return a list of 5 blocks when size of 5 is selected") {
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))

                every { storage.blocks(0, any(), any()) } returns listOf(
                    Block.genesis(publisherPublicKey, publisherSigningKey).toStore(),
                    randomBlock("", 1u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 2u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 3u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 4u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                )

                every { storage.blocks(1, any(), any()) } returns listOf(
                    randomBlock("", 5u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 6u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 7u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 8u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                    randomBlock("", 9u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore(),
                )

                blockchain.chain(0, 5, null, null) shouldHaveSize 5
                blockchain.chain(1, 5, null, null) shouldHaveSize 5
            }
        }

        describe("processArticle") {

            it("should accept valid article") {
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                val publisherKeyPair = generateKeyPair().shouldNotBeNull()

                val goodArticle = randomArticle(publisherKeyPair)

                // Adding one article should not create a block
                blockchain.processArticle(goodArticle).processed shouldBe true
            }

            describe("maximum amount of articles") {
                it("should mint new block") {
                    val genesisBlock = Block.genesis(publisherPublicKey, publisherSigningKey)
                    val storage = mockk<Storage>()
                    val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                    val publisherKeyPair = generateKeyPair().shouldNotBeNull()

                    every { storage.latestBlock() } returns genesisBlock.toStore()
                    every { storage.storeBlock(any()) } returns Unit

                    // First add the maximum amount of articles
                    repeat(9) {
                        val processArticle = blockchain.processArticle(randomArticle(publisherKeyPair))
                        processArticle.processed shouldBe true
                        processArticle.block.shouldBeNull()
                    }

                    val newBlock = blockchain.processArticle(randomArticle(publisherKeyPair)).block.shouldNotBeNull()

                    newBlock.previousHash shouldBe genesisBlock.hash
                    newBlock.height shouldBe 1u

                    blockchain.unprocessedArticlesCount shouldBe 0
                }

                it("should add unprocessed publisher to block") {
                    val genesisBlock = Block.genesis(publisherPublicKey, publisherSigningKey)
                    val storage = mockk<Storage>()
                    val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey), unprocessedPublishers = mutableSetOf("test2"))
                    val publisherKeyPair = generateKeyPair().shouldNotBeNull()

                    every { storage.latestBlock() } returns genesisBlock.toStore()
                    every { storage.storeBlock(any()) } returns Unit

                    // First add the maximum amount of articles
                    repeat(9) {
                        val processArticle = blockchain.processArticle(randomArticle(publisherKeyPair))
                        processArticle.processed shouldBe true
                        processArticle.block.shouldBeNull()
                    }

                    val newBlock = blockchain.processArticle(randomArticle(publisherKeyPair)).block.shouldNotBeNull()

                    newBlock.previousHash shouldBe genesisBlock.hash
                    newBlock.height shouldBe 1u

                    blockchain.unprocessedArticlesCount shouldBe 0
                    blockchain.unprocessedPublishersCount shouldBe 0
                }
            }
        }

        describe("processBlock") {
            it("should accept block with valid height and matching previousHash") {
                val genesisBlock = Block.genesis(publisherPublicKey, publisherSigningKey)
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                val goodBlock = randomBlock(
                    genesisBlock.hash,
                    genesisBlock.height + 1u,
                    genesisBlock.timestamp + 1,
                    TransactionData(
                        emptyList(), setOf(publisherPublicKey)
                    ),
                    publisherPublicKey,
                    publisherSigningKey,
                )

                every { storage.latestBlock() } returns genesisBlock.toStore()
                every { storage.chainSize() } returns 1
                every { storage.storeBlock(any()) } returns Unit

                blockchain.processBlock(goodBlock) shouldBe true
            }

            it("should reject block with invalid height") {
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                val badBlock = randomBlock("", 0u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey)

                every { storage.latestBlock() } returns randomBlock("", 0u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore()
                every { storage.chainSize() } returns 1
                blockchain.processBlock(badBlock) shouldBe false
            }

            it("should reject block without matching previousHash") {
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                val badBlock = randomBlock("", 1u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey)

                every { storage.latestBlock() } returns randomBlock("", 0u, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey).toStore()
                every { storage.chainSize() } returns 0

                blockchain.processBlock(badBlock) shouldBe false
            }

            it("should reject block with timestamp in the past") {
                val genesisBlock = Block.genesis(publisherPublicKey, publisherSigningKey)
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                val goodBlock = randomBlock(genesisBlock.hash, genesisBlock.height + 1u, genesisBlock.timestamp + 1, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey)

                every { storage.latestBlock() } returns genesisBlock.toStore()
                every { storage.chainSize() } returns 1
                every { storage.storeBlock(any()) } returns Unit

                blockchain.processBlock(goodBlock) shouldBe true

                every { storage.latestBlock() } returns goodBlock.toStore()
                every { storage.chainSize() } returns 2

                val badBlock = randomBlock(goodBlock.hash, goodBlock.height + 1u, goodBlock.timestamp - 1, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey)
                blockchain.processBlock(badBlock) shouldBe false

                verify(exactly = 1) { storage.storeBlock(any()) }
            }

            it("should reject block with invalid signature") {
                val genesisBlock = Block.genesis(publisherPublicKey, publisherSigningKey)
                val storage = mockk<Storage>()
                val blockchain = Blockchain(storage, Pair(publisherSigningKey, publisherPublicKey))
                val goodBlock = randomBlock(genesisBlock.hash, genesisBlock.height + 1u, genesisBlock.timestamp + 1, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey)

                every { storage.latestBlock() } returns genesisBlock.toStore()
                every { storage.chainSize() } returns 1
                every { storage.storeBlock(any()) } returns Unit

                blockchain.processBlock(goodBlock) shouldBe true

                every { storage.latestBlock() } returns goodBlock.toStore()
                every { storage.chainSize() } returns 2

                val badBlock = randomBlock(goodBlock.hash, goodBlock.height + 1u, goodBlock.timestamp + 1, publisherKey = publisherPublicKey, publisherSigningKey = publisherSigningKey, signature = "invalid")
                blockchain.processBlock(badBlock) shouldBe false

                verify(exactly = 1) { storage.storeBlock(any()) }
            }
        }
    }

    describe("genesisBlock") {
        it("should have no previous hash and 0 height") {
            val genesisBlock = Block.genesis(publisherPublicKey, publisherSigningKey)

            genesisBlock.previousHash.shouldBeEmpty()
            genesisBlock.height shouldBe 0u
            genesisBlock.data shouldBe TransactionData(emptyList(), setOf(publisherPublicKey))
        }
    }
})

fun randomBlock(
    previousHash: String,
    height: UInt,
    timestamp: Long = Instant.now().toEpochMilli(),
    data: TransactionData = TransactionData(
        emptyList(), emptySet()
    ),
    publisherKey: String = "",
    publisherSigningKey: String = "",
    signature: String? = null,
) = Block(
    publisherKey = publisherKey,
    signature = signature ?: sign(publisherSigningKey.toPrivateKey(), previousHash + data + timestamp + height).toHexString(),
    previousHash = previousHash,
    data = data,
    timestamp = timestamp,
    height = height,
)

fun randomArticle(publisherKeyPair: Pair<PrivateKey, PublicKey>): Article {
    val byline = "byline"
    val headline = "headline"
    val section = "section"
    val content = "content"
    val date = LocalDate.now().toString()

    val signature = sign(publisherKeyPair.first, byline + headline +section + content + date)

    return Article(
        authorKey = publisherKeyPair.second.getString(),
        byline,
        headline,
        section,
        content,
        date,
        signature.toHexString(),
    )
}
