rootProject.name = "avisen-kt"

include(":app")
include(":blockchain")
include(":crypto")
include(":network")
include(":storage")
include(":sql")

pluginManagement {
    val kotlin: String by settings
    val ktor: String by settings

    plugins {
        kotlin("jvm") version kotlin apply false
        kotlin("plugin.serialization") version kotlin apply false
        id("io.ktor.plugin") version ktor apply false
    }
}
