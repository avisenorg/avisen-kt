# avisen-kt

![example workflow](https://github.com/avisenorg/avisen-kt/actions/workflows/build.yml/badge.svg)

Kotlin implementation of the Avisen blockchain protocol used to secure news articles from censorship, tampering, and edits.

# Docs

## Network
### Terminology
* Node: a member of a network.
* Genesis node: the first member of the network. Creates the genesis block.
* Donor node: a node that is used to get the blockchain and the list of nodes in the network.
* Publisher node: a node in the network that has permission to mint blocks.
* Proof of Authority: the consensus mechanism for Avisen. Only certain nodes can mint new blocks.
* Transaction: a single news article with its metadata.

### Starting up for the first time
The application should be provided with a donor node url address from an existing network.
If a donor node is provided, then the app's blockchain and network will be populated from the donor.
If a donor node is not provided, the application assumes it is the genesis node and will start its own network/blockchain.

## Storage
The blockchain is backed by a PostgreSQL database. Running a postgres DB is required for running a node.

### Database Migrations
The storage layer uses Flyway for database migrations. The migrations can be found in /sql/src/main/kotlin/resources/db/migration.

## Development

### Project
A roadmap can be found here: https://github.com/orgs/avisenorg/projects/1/views/1

The roadmap is organized into several categories:
* blockchain: changes that affect the behavior of the blockchain
* network: changes that affect the behavior of the network
* consensus: changes that affect the consensus algorithm(s)
* meta: changes that do not fit into the previous categories (tests, library upgrades, documentation updates, etc.)

### Repository
The repository is split into several packages:
* app: the main logic for starting the application, serving the JSON API, etc.
* blockchain: everything to do with just the blockchain.
* crypto: cryptography utilities.
* network: everything to do with the networking layer and nodes.
* storage: the interface/API for working with the storage layer.
* sql: an implementation of the `storage` API for a PostgreSQL database.

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

### REST API Endpoints
Avisen is a design-first REST API. 
When making changes to the REST API, make sure changes are also made to /app/src/main/resources/openapi/documentation.yml.

#### Dependencies
Dependency versions are centralized in `gradle.properties`. 
New dependencies should be added to this list in alphabetical order.

Avisen requires an installation of Java 21. The Temurin SDK is recommended.

### Running locally
Three nodes are configured to run locally if you use IntelliJ. 
The run configurations should be automatically added to your IDE.
Start the apps in order (App 1, then App 2, etc.). 
The `docker-compose.yml` includes configuration for three databases if you want to use docker.

## Environment Variables
Examples of environment variable usage can be found in /idea/runConfigurations.

* `DB_URL`: the url corresponding to the backing PostgreSQL database. There should only be one database per node.
* `DB_USER`: the username when connecting to the database. This should not be the database user.
* `DB_PASS`: the password for the database user.
* `NODE_ADDRESS`: the url address (host, port, etc.) for accessing the node. Broadcast to the other nodes in the network. Required. This should be a static IP address or url.
* `NODE_DONOR`: the url address (host, port, etc.) for accessing another node to populate the blockchain and network pool. If not populated, the node will run in genesis mode. (See Network/Starting up for the first time)
* `NODE_MODE`: the mode to run the node in:
  * `PUBLISHER`: a node that has the ability to publish transactions (articles), mint blocks, and broadcast new blocks to the network.
  * `REPLICA`: a node that is a full replica of the network and blockchain. Receives updates from publisher nodes.
  * `UTILITY`: a node that is running without a blockchain, network, or database. Useful for generating key-pairs and working with Avisen's local utilities. Most REST API endpoints are disabled in this mode. Database connection information is not required in this mode.
* `PORT`: the given port for the app to run on. Defaults to 8081 if not provided.
* `PUBLISHER_SIGNING_KEY`: A private ECDSA key used for signing blocks. Required if running a node in `PUBLISHER` mode.
* `PUBLISHER_PUBLIC_KEY`: A public ECDSA key used as property of a block. Required if running a node in `PUBLISHER` mode.
