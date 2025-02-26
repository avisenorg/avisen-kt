plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    val exposed: String by project
    val flyway: String by project
    val hikari: String by project
    val kotest: String by project
    val logback: String by project
    val mockk: String by project

    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")
    implementation("com.zaxxer:HikariCP:$hikari")
    implementation("org.flywaydb:flyway-core:$flyway")
    implementation("org.flywaydb:flyway-database-postgresql:$flyway")

    implementation("ch.qos.logback:logback-classic:$logback")

    implementation(project(":storage"))

    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.mockk:mockk:$mockk")
}
