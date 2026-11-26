plugins {
    kotlin("jvm")
}

group = "com.astracode"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.2")
}

tasks.test {
    useJUnitPlatform()
}
