import org.gradle.api.tasks.testing.logging.TestExceptionFormat

group = "io.simplematter"

val vertxVersion by extra("3.9.6")
val kotlinTestVersion by extra("3.2.1")
val junit5Version by extra("5.4.0")
val prometheusClientVersion by extra("0.10.0")

plugins {
    id("org.jetbrains.kotlin.jvm").version("1.3.70")
    id("com.github.johnrengelman.shadow").version("4.0.4")
    id("net.researchgate.release").version("2.8.1")
    application
}

repositories {
    jcenter()

    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlinx")
    }
}

dependencies {
    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

dependencies {
    //Core libs
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.2")
    implementation("ch.qos.logback:logback-classic:1.0.13")

    //Config
    implementation("io.github.config4k:config4k:0.4.1")

    //VertX
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-mqtt:$vertxVersion")

    //Monitoring
    implementation("io.prometheus:simpleclient:$prometheusClientVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusClientVersion")
    implementation("io.prometheus:simpleclient_vertx:$prometheusClientVersion")

    //Test tools
    testImplementation("io.kotlintest:kotlintest-runner-junit5:$kotlinTestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junit5Version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.1")
}

application {
    // Define the main class for the application.
    mainClassName = "io.simplematter.mqtt.load.MqttLoadSimulator"
}

tasks.compileKotlin {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-XXLanguage:+InlineClasses"
    }
}

tasks.compileTestKotlin {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        outputs.upToDateWhen { false } //always run tests
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.shadowJar {
    getArchiveClassifier().set("all")
//   classifier = "all"
}

release {
    pushReleaseVersionBranch = "release"
    buildTasks = listOf("shadowJar")
}

