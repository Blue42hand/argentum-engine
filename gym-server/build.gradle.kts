plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

dependencies {
    // HTTP transport for the gym. The gym module is transport-agnostic
    // — all game logic lives there; this module is a thin Spring shell.
    implementation(project(":gym"))
    implementation(project(":rules-engine"))
    implementation(project(":mtg-sdk"))
    implementation(project(":mtg-sets"))

    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.springBootStarterWeb)
    implementation(libs.springdocOpenapi)
    implementation(kotlin("reflect"))

    testImplementation(libs.springBootStarterTest)
    testImplementation(libs.kotestRunner)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestExtensionsSpring)
}

springBoot {
    mainClass.set("com.wingedsheep.gym.server.GymServerApplicationKt")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    mainClass.set("com.wingedsheep.gym.server.GymServerApplicationKt")
}

tasks.register<JavaExec>("commanderGymDeckCoverage") {
    notCompatibleWithConfigurationCache("Consumes external deck paths supplied at execution time")
    group = "application"
    description = "Report deck compatibility against the full Gym CardRegistry"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.wingedsheep.gym.server.tools.DeckCoverageCliKt")

    doFirst {
        val files = providers.gradleProperty("deckFiles").orNull
            ?.split(';')
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        require(files.isNotEmpty()) {
            "Pass -PdeckFiles='/path/deck-a.txt;/path/deck-b.txt'"
        }
        setArgs(files)
        providers.gradleProperty("coverageOutput").orNull?.let {
            args("--output", it)
        }
        providers.gradleProperty("registryNamesOutput").orNull?.let {
            args("--registry-names-output", it)
        }
    }
}
