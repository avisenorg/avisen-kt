plugins {
    base
    kotlin("jvm") apply false
}

allprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
