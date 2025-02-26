package org.avisen.network

import org.avisen.blockchain.Block
import org.avisen.storage.Storage
import org.avisen.storage.StoreNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class NetworkTest: DescribeSpec({
    describe("network") {
        describe("addPeer") {
            it("should add node to list of peers") {
                val networkClient = mockk<NetworkClient>()
                val storage = mockk<Storage>()
                val network = Network(networkClient, storage)

                every { storage.addNetworkPeer(any()) } returns Unit
                every { storage.getNetworkPeer(any()) } returns null

                network.addPeer(Node("test", NodeType.REPLICA), false)

                coVerify(exactly = 1) { storage.getNetworkPeer(any()) }
                coVerify(exactly = 1) { storage.addNetworkPeer(any()) }
            }

            it("should not add duplicate peer") {
                val networkClient = mockk<NetworkClient>()
                val storage = mockk<Storage>()
                val network = Network(networkClient, storage)

                every { storage.addNetworkPeer(any()) } returns Unit
                every { storage.getNetworkPeer(any()) } returns StoreNode("test", NodeType.REPLICA.name)

                network.addPeer(Node("test", NodeType.REPLICA), false)
                network.addPeer(Node("test", NodeType.REPLICA), false)

                coVerify(exactly = 2) { storage.getNetworkPeer(any()) }
                coVerify(exactly = 0) { storage.addNetworkPeer(any()) }
            }

            it("should broadcast new peer") {
                val networkClient = mockk<NetworkClient>()
                val storage = mockk<Storage>()
                val network = Network(networkClient, storage)

                coEvery { networkClient.broadcastPeer(any(), any()) } returns Unit
                every { storage.addNetworkPeer(any()) } returns Unit
                every { storage.getNetworkPeer(any()) } returns null
                every { storage.peers() } returns listOf(StoreNode("test2", NodeType.REPLICA.name))

                network.addPeer(Node("first", NodeType.REPLICA), false)
                network.addPeer(Node("test", NodeType.REPLICA), true)

                coVerify(exactly = 1) { networkClient.broadcastPeer(any(), any()) }
                coVerify(exactly = 2) { storage.getNetworkPeer(any()) }
            }
        }

        describe("broadcastBlock") {
            it("should broadcast block to every peer") {
                val networkClient = mockk<NetworkClient>()
                val storage = mockk<Storage>()
                val network = Network(networkClient, storage)

                coEvery { networkClient.broadcastBlock(any(), any()) } returns Unit
                every { storage.getNetworkPeer(any()) } returns null
                every { storage.addNetworkPeer(any()) } returns Unit
                every { storage.peers() } returns listOf(StoreNode("first", NodeType.REPLICA.name), StoreNode("test", NodeType.REPLICA.name))

                network.addPeer(Node("first", NodeType.REPLICA), false)
                network.addPeer(Node("second", NodeType.REPLICA), false)

                network.broadcastBlock(Block("prevHash", "data", Instant.now().toEpochMilli(), 1u))

                coVerify(exactly = 2) { networkClient.broadcastBlock(any(), any()) }
            }
        }
    }
})
