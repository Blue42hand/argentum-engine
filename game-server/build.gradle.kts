plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    alias(libs.plugins.kover)
}

val nativeRevision = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    inputs.property("nativeRevision", nativeRevision)
    manifest.attributes("Argentum-Revision" to nativeRevision.get())
}

dependencies {
    implementation(project(":rules-engine"))
    implementation(project(":mtg-sdk"))
    implementation(project(":mtg-sdk-tooling"))
    implementation(project(":mtg-sets"))
    implementation(project(":mtg-search"))
    implementation(project(":ai"))
    // Argentum Assay — Oracle text -> CardDefinition, for the Scenario Builder's custom-card
    // sandbox only (dev-gated; see AssayCardService). Assay stays an auditor: nothing here loads
    // the card corpus through it, and a compiled card never leaves the session that asked for it.
    implementation(project(":oracle-assay"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.springBootWeb)
    implementation(libs.springBootStarterDataRedis)
    // Accounts / persistence: PostgreSQL via Spring Data JDBC + Flyway, magic-link email.
    // All gated behind `accounts.enabled` — the DataSource/Flyway/JDBC auto-config is excluded
    // by default so the server still boots (and tests run) with no database present.
    implementation(libs.springBootStarterDataJdbc)
    implementation(libs.springBootStarterMail)
    implementation(libs.springBootFlyway)
    implementation(libs.flywayCore)
    implementation(libs.flywayPostgresql)
    runtimeOnly(libs.postgresql)
    // Optional external controller artifact, built against this exact server revision.
    // Spring discovers its AutoConfiguration.imports inside the packaged bootJar.
    providers.gradleProperty("aiControllerAdapterJar").orNull?.let { path ->
        val adapter = rootProject.file(path)
        require(adapter.isFile && adapter.extension == "jar") {
            "aiControllerAdapterJar must name an existing adapter JAR"
        }
        runtimeOnly(files(adapter))
    }
    implementation(libs.springdocOpenapi)
    implementation(kotlin("reflect"))

    // Engine scenario harness (ScenarioTestBase, GameTestDriver, TestCards) — the canonical
    // home for the static-board scenario builder; game-server's ScenarioTestBase is a shim over it.
    testImplementation(testFixtures(project(":rules-engine")))
    testImplementation(libs.springBootStarterTest)
    testImplementation(libs.kotestRunner)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestExtensionsSpring)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.mockk)
    // Real-Postgres integration tests for the JDBC repositories + Flyway migration.
    // Self-skips when Docker is unavailable (see PostgresAccountsRepositoryTest).
    testImplementation(libs.testcontainersPostgresql)
    testImplementation(libs.testcontainersJunit)
}
