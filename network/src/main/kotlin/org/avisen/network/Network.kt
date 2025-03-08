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
import java.net.URI
import java.net.URISyntaxException

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
data class NewPublisher(
    val publicKey: String,
    val signature: String,
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
        val validPeerUrl = validatePeerUrl(node.address)
        if (!validPeerUrl.first) {
            throw RuntimeException(validPeerUrl.second)
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

    suspend fun downloadPeers(fromAddress: String) {
        client.downloadPeers(fromAddress).forEach {
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

    // TODO since we have a list of nodes, we should be able to pick back up from another node if this bootnode goes down
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

/**
 * Validates that a peer URL has the correct format
 * @param url The URL to validate
 * @return Pair<Boolean, String> where first value indicates if URL is valid,
 *         and second value contains an error message if invalid
 */
fun validatePeerUrl(url: String): Pair<Boolean, String> {
    // Check if URL is empty
    if (url.isBlank()) {
        return Pair(false, "peer URL cannot be empty")
    }

    // Validate URL using the URI class
    try {
        val uri = URI(url)

        // Check protocol is non-null and either of http or https
        if (uri.scheme == null
            || !(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true))) {
            return Pair(false, "peer URL must use HTTP or HTTPS protocol")
        }

        // Check host is not null or empty
        if (uri.host == null || uri.host.isBlank()) {
            return Pair(false, "URL must contain a valid host")
        }

    } catch (e: URISyntaxException) {
        return Pair(false, "Invalid URL format for peer: ${e.message}")
    }

    return Pair(true, "Valid URL format for peer")
}

