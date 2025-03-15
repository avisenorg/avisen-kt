package org.avisen.app

import org.avisen.blockchain.Article
import org.avisen.blockchain.Block
import org.avisen.blockchain.Blockchain
import org.avisen.crypto.KeyPairString
import org.avisen.crypto.Signature
import org.avisen.crypto.SigningPayload
import org.avisen.crypto.generateKeyPair
import org.avisen.crypto.getString
import org.avisen.crypto.setupSecurity
import org.avisen.crypto.sign
import org.avisen.crypto.toHexString
import org.avisen.crypto.toPrivateKey
import org.avisen.network.Info
import org.avisen.network.Network
import org.avisen.network.NetworkWebClient
import org.avisen.network.Node
import org.avisen.network.NodeInfo
import org.avisen.network.NodeType
import org.avisen.sql.Db
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.avisen.blockchain.Publisher
import org.avisen.crypto.hash
import org.avisen.network.NewPublisher

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    /**
     * Environment Variables
     *
     * Optional variables use propertyOrNull() while required properties use property()
     */
    val selfAddress = environment.config.property("node.address").getString()
    val bootNode = environment.config.propertyOrNull("node.bootNode")?.getString()
    val nodeMode = environment.config.property("node.mode").getString()
    val networkId = environment.config.property("node.networkId").getString()
    val publisherSigningKey = environment.config.propertyOrNull("node.publisher.signingKey")?.getString()
    val publisherPublicKey = environment.config.propertyOrNull("node.publisher.publicKey")?.getString()

    // When running locally with docker, sometimes the application can beat the database when starting
    if (networkId == "local") {
        // Wait for the db to come up
        Thread.sleep(2000)
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
        })
    }

    install(DefaultHeaders) {
        header("X-Network-ID", networkId)
    }

    val HeaderValidatorPlugin = createApplicationPlugin("HeaderValidatorPlugin") {
        on(CallSetup) { call ->
            if (!(call.request.uri.contains("status") || call.request.uri.contains("util") || call.request.uri.contains("docs"))) {
                if (!call.request.headers.contains("X-Network-ID")) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Required X-Network-ID is missing"))
                    return@on
                }

                val networkIdHeader = call.request.headers["X-Network-ID"]
                if (networkIdHeader != networkId) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Network ID $networkIdHeader does not match expected network id"))
                    return@on
                }
            }
        }
    }

    install(HeaderValidatorPlugin)

    setupSecurity()

    val nodeInfo = NodeInfo(
        address = selfAddress,
        type = NodeType.valueOf(nodeMode)
    )

    environment.log.info("Starting up node as ${nodeInfo.type}")

    if (nodeInfo.type != NodeType.UTILITY) {
        val network = Network(NetworkWebClient(networkId))
        if (nodeInfo.type == NodeType.PUBLISHER && publisherSigningKey == null) {
            throw RuntimeException("A Publisher Signing Key is required when starting a node in PUBLISHER mode.")
        }

        if (nodeInfo.type == NodeType.PUBLISHER && publisherPublicKey == null) {
            throw RuntimeException("A Publisher Public Key is required when starting a node in PUBLISHER mode.")
        }

        if (nodeInfo.type == NodeType.REPLICA && bootNode == null) {
            throw RuntimeException("A boot node is required when starting a node in REPLICA mode.")
        }

        val blockchain = Blockchain(storage(), Pair(publisherSigningKey ?: "", publisherPublicKey ?: ""))
        // If a boot node has been specified,
        // get the blockchain, transactions, and network from it
        if (bootNode != null) {
            environment.log.info("Starting with network id: $networkId")

            environment.log.info("Boot node address found. Starting boot process.")

            environment.log.info("Starting download of network peers from boot node...")
            runBlocking { network.downloadPeers(bootNode) }

            // Also get the boot node's info
            runBlocking { network.downloadPeerInfo(bootNode) }

            environment.log.info("Done downloading peers.")

            if (blockchain.chain(0, null, null, null).isEmpty()) {
                environment.log.info("Downloading full blockchain...")
                var page = 0

                var newBlocks = runBlocking { network.downloadBlocks(bootNode, page, null).toMutableList() }
                while(newBlocks.isNotEmpty()) {
                    newBlocks.forEach { blockchain.processBlock(it) }
                    page++
                    newBlocks = runBlocking { network.downloadBlocks(bootNode, page, null).toMutableList() }
                }
                environment.log.info("Full blockchain downloaded from boot node.")
            } else {
                environment.log.info("Blockchain already downloaded.")
                environment.log.info("Checking for missing blocks...")
                val latestBlockFromBootNode = runBlocking { network.getLatestBlock(bootNode) }

                if (latestBlockFromBootNode != null) {
                    val latestBlockInChain = blockchain.chain(0, 1, "DESC", null).singleOrNull()

                    if (latestBlockFromBootNode.height > latestBlockInChain!!.height) {
                        environment.log.info("Out-of-date blockchain detected. Downloading latest blocks...")
                        var page = 0

                        var newBlocks = runBlocking { network.downloadBlocks(bootNode, page, null).toMutableList() }
                        while(newBlocks.isNotEmpty()) {
                            newBlocks.forEach { blockchain.processBlock(it) }
                            page++
                            newBlocks = runBlocking { network.downloadBlocks(bootNode, page, null).toMutableList() }
                        }
                    } else {
                        environment.log.info("Blockchain up-to-date.")
                    }
                }

            }

            environment.log.info("Adding self to the network...")
            // Update the boot node with the new peer
            runBlocking { network.updatePeer(bootNode, nodeInfo.toNode()) }
        } else {
            environment.log.info("No boot node address found.")
            if (blockchain.chain(0, null, null, null).isEmpty()) {
                environment.log.info("Beginning genesis...")

                blockchain.processBlock(Block.genesis(publisherPublicKey!!, publisherSigningKey!!))
            } else {
                environment.log.info("Blockchain already detected.")
            }
        }

        routing {
            route("/network") {
                /**
                 * Gets the full network object.
                 *
                 * Mostly used to see all peers in the network.
                 */
                get {
                    call.respond(network.peers())
                }

                route("/node") {

                    /**
                     * Add a node to the network.
                     * If url param 'broadcast' is true,
                     * send the node to all peers too.
                     */
                    post {

                        val broadcast = call.parameters["broadcast"]?.toBooleanStrictOrNull()

                        val node = call.receive<Node>()

                        network.addPeer(node, broadcast)

                        call.response.status(HttpStatusCode.Created)
                    }

                    // Only publishers can add publishers to the network
                    if (nodeInfo.type == NodeType.PUBLISHER) {
                        route("/publisher") {
                            post {
                                val newPublisher = call.receive<NewPublisher>()

                                val result = blockchain.acceptPublisher(Publisher(newPublisher.publicKey), newPublisher.signature)

                                if (result) {
                                   call.response.status(HttpStatusCode.OK)
                                } else {
                                    call.response.status(HttpStatusCode.BadRequest)
                                }
                            }
                        }
                    }
                }
            }

            route("/blockchain") {

                /**
                 * Gets a section of the blockchain with url params page and size
                 */
                get {
                    val page = call.parameters["page"]?.toIntOrNull()
                    val size = call.parameters["size"]?.toIntOrNull()
                    val sort = call.parameters["sort"]
                    val fromHeight = call.parameters["fromHeight"]?.toUIntOrNull()

                    if (page == null || page < 0 || (size != null && size < 1)) {
                        call.response.status(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val subchain = blockchain.chain(page, size, sort, fromHeight)

                    call.respond(HttpStatusCode.OK, subchain)
                }

                route("/article/{articleId}") {
                    get {
                        val id = call.parameters["articleId"]!!

                        val article = blockchain.getArticle(id)

                        if (article != null) {
                            call.respond(HttpStatusCode.OK, article)
                        } else {
                            call.response.status(HttpStatusCode.NotFound)
                        }
                    }
                }

                route("/block") {
                    post {
                        val newBlock = call.receive<Block>()

                        val processed = blockchain.processBlock(newBlock)

                        if (processed) {
                            call.respond(HttpStatusCode.Created)
                        } else {
                            call.respond(HttpStatusCode.BadRequest)
                        }
                    }

                    get("/{hash}") {
                        val hash = call.parameters["hash"]

                        if (hash.isNullOrBlank()) {
                            call.response.status(HttpStatusCode.BadRequest)
                            return@get
                        }

                        val block = blockchain.getBlock(hash)

                        if (block == null) {
                            call.response.status(HttpStatusCode.NotFound)
                        } else {
                            call.respond(HttpStatusCode.OK, block)
                        }
                    }
                }
            }

            route("/article") {
                /**
                 * Processes the article. If enough articles are processed, mint a new block.
                 * TODO notify the network of the new article if a block was not minted.
                 * Only a node running in PUBLISHER mode should be able to accept an unprocessed article.
                 * TODO only a PUBLISHER node that is the selected broadcaster should be able to broadcast the new block.
                 */
                post {

                    if (nodeInfo.type == NodeType.REPLICA) {
                        call.response.status(HttpStatusCode.Forbidden)
                        return@post
                    }

                    val article = call.receive<Article>()

                    if (article.authorKey == ""
                        || article.byline == ""
                        || article.headline == ""
                        || article.section == ""
                        || article.contentHash == ""
                        || article.date == ""
                        || article.signature == "") {
                        call.response.status(HttpStatusCode.BadRequest)
                        return@post
                    }

                    val processedArticle = blockchain.processArticle(article)

                    if (!processedArticle.processed) {
                        call.response.status(HttpStatusCode.BadRequest)
                        return@post
                    }

                    val newBlock = processedArticle.block
                    if (newBlock != null) {
                        call.application.engine.environment.log.info("Minted a new block: $newBlock")

                        // Broadcast the new block to peers
                        network.broadcastBlock(newBlock)

                        call.response.status(HttpStatusCode.Created)
                    } else {
                        call.application.engine.environment.log.info("Processed a new article")
                        call.response.status(HttpStatusCode.OK)
                    }
                }
            }
        }
    }

    environment.log.info("Completed node startup.")

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        openAPI(path="docs/api", swaggerFile = "openapi/documentation.yml")

        get("/status") {
            call.respond(Info(networkId, nodeInfo))
        }

        route("/util") {
            route("/crypto") {
                route("hash") {
                    get {
                        val contentToHash = call.receive<ContentToHash>()

                        call.respond(HashedContent(hash(contentToHash.content)))
                    }
                }

                route("/key-pair") {
                    get {
                        val keyPair = generateKeyPair()

                        if (keyPair == null) {
                            call.response.status(HttpStatusCode.InternalServerError)
                            return@get
                        }

                        call.respond(HttpStatusCode.Created, KeyPairString(keyPair.first.getString(), keyPair.second.getString()))
                    }
                }

                route("sign") {
                    get {
                        val signingPayload = call.receive<SigningPayload>()

                        val privateKey = signingPayload.privateKey.toPrivateKey()

                        val signature = sign(privateKey, signingPayload.data).toHexString()

                        call.respond(HttpStatusCode.Created, Signature(signature))
                    }
                }
            }
        }
    }
}

fun Application.storage(): Db {
    val url = environment.config.property("db.url").getString()
    val username = environment.config.property("db.user").getString()
    val password = environment.config.property("db.pass").getString()

    return Db.init(url, username, password)
}

@Serializable
data class ContentToHash(
    val content: String,
)

@Serializable
data class HashedContent(
    val content: String,
)
