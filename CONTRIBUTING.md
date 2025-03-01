# Contributing to `avisen-kt`

### Repository
The repository is split into several packages:
* app: the main logic for starting the application, serving the JSON API, etc.
* blockchain: everything to do with just the blockchain.
* crypto: cryptography utilities.
* network: everything to do with the networking layer and nodes.
* storage: the interface/API for working with the storage layer.
* sql: an implementation of the `storage` API for a PostgreSQL database.

#### Dependencies
Dependency versions are centralized in `gradle.properties`.
New dependencies should be added to this list in alphabetical order.

Avisen requires an installation of Java 21. The Temurin SDK is recommended.

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

### Running locally
Run `make up-db` to spin up just the dbs or `make up` to spin up the dbs and a publisher node.

Three nodes are configured to run locally if you use IntelliJ.
The run configurations should be automatically added to your IDE.
Start the apps in order (App 1, then App 2, etc.). All three each require a database.
The `docker-compose.yml` includes configuration for three databases if you want to use docker.
