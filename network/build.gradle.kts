plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    val kotlinxSerialization: String by project
    val kotest: String by project
    val ktor: String by project
    val logback: String by project
    val mockk: String by project

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerialization")

    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-cio:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")

    implementation("ch.qos.logback:logback-classic:$logback")

    implementation(project(":blockchain"))
    implementation(project(":storage"))

    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.mockk:mockk:$mockk")

}
