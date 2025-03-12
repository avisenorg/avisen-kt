package org.avisen.network

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.avisen.blockchain.Block
import org.avisen.blockchain.TransactionData
import java.io.IOException
import java.time.Instant

class NetworkTest: DescribeSpec({
    describe("network") {
        describe("addPeer") {
            it("should add node to list of peers") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                network.addPeer(Node("http://test.com", NodeType.REPLICA), false)

                peers shouldHaveSize 1
            }

            it("should not add duplicate peer") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                network.addPeer(Node("http://test.com", NodeType.REPLICA), false)
                network.addPeer(Node("http://test.com", NodeType.REPLICA), false)

                peers shouldHaveSize 1
            }

            it("should broadcast new peer") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                coEvery { networkClient.broadcastPeer(any(), any()) } returns Unit

                network.addPeer(Node("http://first.com", NodeType.REPLICA), false)
                network.addPeer(Node("http://test.com", NodeType.REPLICA), true)

                peers shouldHaveSize 2

                coVerify(exactly = 1) { networkClient.broadcastPeer(any(), any()) }
            }
        }

        describe("broadcastBlock") {
            it("should broadcast block to every peer") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                coEvery { networkClient.broadcastBlock(any(), any()) } returns Unit

                network.addPeer(Node("http://first.com", NodeType.REPLICA), false)
                network.addPeer(Node("http://second.com", NodeType.REPLICA), false)

                network.broadcastBlock(Block("pubKey", "signature", "prevHash", TransactionData(emptyList(), emptySet()), Instant.now().toEpochMilli(), 1u))

                coVerify(exactly = 2) { networkClient.broadcastBlock(any(), any()) }
            }
        }
        describe("validatePeerUrl") {
            it("should reject empty URL") {
                val (isValid, message) = validatePeerUrl("")
                isValid shouldBe false
                message shouldBe "peer URL cannot be empty"
            }

            it("should reject unsupported protocol") {
                val (isValid, message) = validatePeerUrl("ftp://example.com")
                isValid shouldBe false
                message shouldBe "peer URL must use HTTP or HTTPS protocol"
            }

            it("should reject URL without host") {
                val (isValid, message) = validatePeerUrl("http://")
                isValid shouldBe false
                message shouldContain "Expected authority"
            }

            it("should accept valid HTTP URL") {
                val (isValid, message) = validatePeerUrl("http://example.com")
                isValid shouldBe true
                message shouldBe "Valid URL format for peer"
            }

            it("should accept valid HTTPS URL") {
                val (isValid, message) = validatePeerUrl("https://example.com")
                isValid shouldBe true
                message shouldBe "Valid URL format for peer"
            }
        }
    }
})
