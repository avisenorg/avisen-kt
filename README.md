# avisen-kt

![build badge](https://github.com/avisenorg/avisen-kt/actions/workflows/build.yml/badge.svg)

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

# Running `avisen-kt`

A docker image can be found at [Docker Hub](https://hub.docker.com/repository/docker/avisen/client-kt/general).

## Starting up for the first time

Before starting the image, start or connect to a PostgreSQL 17 database.

You will need to run the following queries for the user that `avisen-kt` will use to connect to the database with:
```sql
GRANT ALL PRIVILEGES ON DATABASE YOUR_DATABASE TO YOUR_USERNAME;

GRANT ALL ON SCHEMA public TO YOUR_USERNAME;
```
This ensures the database user has access to select and insert blocks, and control database migrations.

The application should be provided with a donor node url address from an existing network.
If a donor node is provided, then the app's blockchain and network will be populated from the donor.
If a donor node is not provided, the application assumes it is the genesis node and will start its own network/blockchain.

### Running a Publisher node

If you are planning on running a node in publisher mode, you will need to generate ECDSA public and private keys and pass them as environment variables.
`avisen-kt` includes utilities to do this. First start the node in `UTILITY` mode, and navigate to `YOUR_URL/util/crypto/key-pair`. 
A keypair will be generated for you.
> It is recommended to generate key pairs only on your local machine, to avoid sending the keys over the internet. 
> Use caution when generating these keypairs or using `avisen-kt` to create signatures.

### Environment Variables
Examples of environment variable usage can be found in /idea/runConfigurations.

* `DB_URL`: the url corresponding to the backing PostgreSQL database. There should only be one database per node. Not required in `UTILITY` mode.
* `DB_USER`: the username when connecting to the database. This should not be the database user. Not required in `UTILITY` mode.
* `DB_PASS`: the password for the database user. Not required in `UTILITY` mode.
* `NODE_ADDRESS`: the url address (host, port, etc.) for accessing the node. Broadcast to the other nodes in the network. Required. This should be a static IP address or url. 
* `NODE_DONOR`: the url address (host, port, etc.) for accessing another node to populate the blockchain and network pool. If not populated, the node will run in genesis mode. (See Network/Starting up for the first time) Not required in `UTILITY` mode.
* `NODE_MODE`: the mode to run the node in:
  * `PUBLISHER`: a node that has the ability to publish transactions (articles), mint blocks, and broadcast new blocks to the network.
  * `REPLICA`: a node that is a full replica of the network and blockchain. Receives updates from publisher nodes.
  * `UTILITY`: a node that is running without a blockchain, network, or database. Useful for generating key-pairs and working with Avisen's local utilities. Most REST API endpoints are disabled in this mode. Database connection information is not required in this mode.
* `NETWORK_ID`: a shared id that prevents cross-network activity. Not required in `UTILITY` mode.
* `PORT`: the given port for the app to run on. Defaults to 8081 if not provided.
* `PUBLISHER_SIGNING_KEY`: A private ECDSA key used for signing blocks. Required if running a node in `PUBLISHER` mode. Not required in `UTILITY` mode.
* `PUBLISHER_PUBLIC_KEY`: A public ECDSA key used as property of a block. Required if running a node in `PUBLISHER` mode. Not required in `UTILITY` mode.

### Losing access to a network
If your node goes down or fails to start after a bad deployment, `avisen-kt` will automatically attempt to download the latest additions to the blockchain on startup.
