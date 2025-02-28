package org.avisen.network

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.avisen.blockchain.Block
import org.avisen.blockchain.TransactionData
import java.time.Instant

class NetworkTest: DescribeSpec({
    describe("network") {
        describe("addPeer") {
            it("should add node to list of peers") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                network.addPeer(Node("test", NodeType.REPLICA), false)

                peers shouldHaveSize 1
            }

            it("should not add duplicate peer") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                network.addPeer(Node("test", NodeType.REPLICA), false)
                network.addPeer(Node("test", NodeType.REPLICA), false)

                peers shouldHaveSize 1
            }

            it("should broadcast new peer") {
                val peers = mutableListOf<Node>()
                val networkClient = mockk<NetworkClient>()
                val network = Network(networkClient, peers)

                coEvery { networkClient.broadcastPeer(any(), any()) } returns Unit

                network.addPeer(Node("first", NodeType.REPLICA), false)
                network.addPeer(Node("test", NodeType.REPLICA), true)

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

                network.addPeer(Node("first", NodeType.REPLICA), false)
                network.addPeer(Node("second", NodeType.REPLICA), false)

                network.broadcastBlock(Block("pubKey", "signature", "prevHash", TransactionData(emptyList(), emptySet()), Instant.now().toEpochMilli(), 1u))

                coVerify(exactly = 2) { networkClient.broadcastBlock(any(), any()) }
            }
        }
    }
})
