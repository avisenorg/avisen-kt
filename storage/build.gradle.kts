plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    val kotlinxSerialization: String by project
    val kotest: String by project
    val logback: String by project
    val mockk: String by project

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerialization")

    implementation("ch.qos.logback:logback-classic:$logback")

    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.mockk:mockk:$mockk")
}
