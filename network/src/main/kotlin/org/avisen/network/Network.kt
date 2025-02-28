package org.avisen.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.avisen.blockchain.Block

@Serializable
data class Node(
    val address: String,
    val type: NodeType,
)

enum class NodeType {
    PUBLISHER,
    REPLICA,
    UTILITY,
}

/**
 * Information of the current application
 */
@Serializable
data class Info(
    val networkId: String,
    val node: NodeInfo,
)

/**
 * Information for the current application's node
 */
@Serializable
data class NodeInfo(
    val address: String,
    val type: NodeType,
) {
    fun toNode() = Node(
        address,
        type,
    )
}

@Serializable
data class Publisher(
    val publicKey: String,
)

@Serializable
data class Network(
    @Transient private val client: NetworkClient = NetworkWebClient(""),
    private val peers: MutableList<Node> = mutableListOf(),
) {
    fun peers() = peers.toList()

    /**
     * Adds a node to the network.
     * If the node already exists, do not add it.
     *
     * A duplicate node has the same address.
     */
    private fun addPeer(participant: Node): Boolean {
        val duplicateNode = peers.firstOrNull { it.address == participant.address }
        if (duplicateNode != null) return false

        peers.add(participant)
        return true
    }

    /**
     * Adds a node to the network. Returns true if successfully added.
     * If the node already exists do not add it and return false.
     */
    suspend fun addPeer(node: Node, broadcast: Boolean?) {
        if (node.address.isBlank()) {
            throw RuntimeException("Address cannot be empty")
        }

        val added = addPeer(node)

        // Broadcast the addition of the node to the network
        if (broadcast == true && added) {
            peers
                .filter {
                    it.address != node.address
                }
                .forEach {
//                    log.info("Broadcasting new peer to: ${it.address}")
                    client.broadcastPeer(it.address, node)

                    // TODO track if the peer was unsuccessfully broadcast
                }
        }
    }

    suspend fun broadcastBlock(newBlock: Block) {
        peers.forEach {
            client.broadcastBlock(it.address, newBlock)
        }
    }

    suspend fun downloadPeers(donorNode: String) {
        client.downloadPeers(donorNode).forEach {
            peers.add(Node(it.address, it.type))
        }
    }

    suspend fun downloadPeerInfo(peer: String): Info {
        val peerInfo = client.downloadPeerInfo(peer)

        return peerInfo
    }

    suspend fun downloadBlocks(peer: String, page: Int, fromHeight: UInt?) = client.downloadBlocks(peer, page, fromHeight)

    suspend fun updatePeer(peer: String, node: Node) {
        client.updatePeer(peer, node)
    }

    suspend fun getLatestBlock(peer: String) = client.getLatestBlock(peer)
}

interface NetworkClient {
    suspend fun broadcastPeer(address: String, node: Node)
    suspend fun broadcastBlock(address: String, block: Block)
    suspend fun getLatestBlock(address: String): Block?
    suspend fun downloadPeers(address: String): List<Node>
    suspend fun downloadPeerInfo(address: String): Info
    suspend fun downloadBlocks(address: String, page: Int, fromHeight: UInt?): List<Block>
    suspend fun updatePeer(address: String, node: Node)
}

class NetworkWebClient(private val networkId: String): NetworkClient {
    private val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }

        defaultRequest {
            contentType(ContentType.Application.Json)
            headers.appendIfNameAbsent("X-Network-ID", networkId)
        }
    }

    override suspend fun broadcastPeer(address: String, node: Node) {
        client.post("$address/network/node?broadcast=true") {
            setBody(node)
        }
    }

    override suspend fun broadcastBlock(address: String, block: Block) {
        client.post("$address/blockchain/block") {
            setBody(block)
        }
    }

    override suspend fun getLatestBlock(address: String): Block? {
        val response = runBlocking { client.get("$address/blockchain?page=0&size=1&sort=DESC") }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to fetch latest block from address: $address")
        }

        return response.body<List<Block>>().singleOrNull()
    }

    override suspend fun downloadPeers(address: String): List<Node> {
        val response = runBlocking  { client.get("$address/network") }

        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to download peers from address: $address")
        }

        return response.body<List<Node>>()
    }

    override suspend fun downloadPeerInfo(address: String): Info {
        val response = runBlocking { client.get("$address/status") }
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to download peer info with address: $address")
        }

        return response.body<Info>()
    }

    override suspend fun downloadBlocks(address: String, page: Int, fromHeight: UInt?): List<Block> {

        return if (fromHeight == null) {
            downloadBlocks(address, page).toMutableList()

        } else {
            downloadBlocksFromHeight(address, page, fromHeight)
        }

    }

    override suspend fun updatePeer(address: String, node: Node) {
        val response = runBlocking { client.post("$address/network/node?broadcast=true") {
            setBody(node)
        } }
        if (response.status != HttpStatusCode.Created) {
            // TODO since we have a list of nodes, go down the list and keep trying other nodes
            throw RuntimeException("Failed to submit self to the network to address: $address")
        }
    }

    // TODO since we have a list of nodes, we should be able to pick back up from another node if this donor goes down
    private suspend fun downloadBlocks(address: String, page: Int): List<Block> {
        val response = client.get("$address/blockchain?page=$page&size=10")
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to download the blockchain from address: $address")
        }
        return response.body<List<Block>>()
    }

    private suspend fun downloadBlocksFromHeight(address: String, page: Int, fromHeight: UInt): List<Block> {
        val response = client.get("$address/blockchain?page=$page&size=10&fromHeight=$fromHeight")
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to download the blocks from address: $address with fromHeight: $fromHeight")
        }
        return response.body<List<Block>>()
    }
}
