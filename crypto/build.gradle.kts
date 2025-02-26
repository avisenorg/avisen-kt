plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    val bouncyCastle: String by project
    val kotlinxSerialization: String by project
    val kotest: String by project
    val logback: String by project
    val mockk: String by project

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerialization")
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastle")

    implementation("ch.qos.logback:logback-classic:$logback")

    testImplementation("io.kotest:kotest-runner-junit5:$kotest")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
    testImplementation("io.mockk:mockk:$mockk")
}
