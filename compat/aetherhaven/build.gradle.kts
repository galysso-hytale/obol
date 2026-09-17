// Obol Aetherhaven: a compatibility mod, a loadable Hytale plugin of its own
// that depends on Obol and on Aetherhaven at runtime (manifest
// `Dependencies`) and on the lootbag add-on when it is there (manifest
// `OptionalDependencies`). It registers Obol as Aetherhaven's economy.
plugins {
    java
    id("com.azuredoom.hytale-tools")
}

group = project.property("group").toString()
version = project.property("version").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

repositories {
    mavenCentral()
}

// `runAllMods` stages every workspace project from its class output, Obol
// included, and would stage Obol twice if this project also brought its jar.
val standaloneRun = gradle.startParameter.taskNames.none {
    it.endsWith("runAllMods") || it.endsWith("stageAllModAssets")
}

// Aetherhaven's jar, built in the sibling checkout with `sh ./gradlew jar`
// (`-Paetherhaven_jar=/path/to.jar` or the same key in
// ~/.gradle/gradle.properties points elsewhere). Needed until the economy
// SPI is in a published Aetherhaven release.
val aetherhavenJar = (findProperty("aetherhaven_jar")?.toString()?.takeIf { it.isNotBlank() }?.let { file(it) }
    ?: rootProject.file("../Aetherhaven/build/libs/Aetherhaven-3.2.0.jar"))

dependencies {
    // The API types, to compile against. Never bundled: Obol provides them at
    // runtime, and a second copy of Coins would make ClassCastExceptions.
    compileOnly(project(":api"))
    // The lootbag's public API (LootbagItem, LootLaw, Rarity), same rule:
    // only loot/LootbagLoot names these types, and they load from the
    // lootbag's own jar at runtime, or never load at all.
    compileOnly(project(":addons:lootbag"))
    // Aetherhaven's economy SPI, same rule again.
    compileOnly(files(aetherhavenJar))
    // Obol and Aetherhaven, for this project's own server
    // (`:compat:aetherhaven:runServer`). vineImplementation rather than
    // vineMod: in dev this plugin's classes sit on the server's classpath
    // (system class loader), which cannot see a plugin loaded from a jar in
    // run/mods. vineImplementation puts both on that same classpath. Not
    // transitive, so obol-api.jar (which has no manifest) is not staged.
    if (standaloneRun) {
        vineImplementation(project(":core")) { isTransitive = false }
        vineImplementation(project(":addons:lootbag")) { isTransitive = false }
        if (aetherhavenJar.isFile) {
            vineImplementation(files(aetherhavenJar))
        }
    }
}

// Rate and ObolGoldAccount are plain JDK code over Obol's API: unit-tested
// here with a fake backend, no server running.
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.13.4")
            dependencies {
                implementation(project(":api"))
                implementation(files(aetherhavenJar))
                implementation("com.hypixel.hytale:Server:0.+")
            }
        }
    }
}

hytaleTools {
    // hytaleVersion / patchline / manifestGroup are inherited from hytaleWorkspace.
    javaVersion = property("java_version").toString().toInt()
    manifestServerVersion = property("manifestServerVersion").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = false
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()

    // Same machine-specific override as core: see core/build.gradle.kts.
    findProperty("hytaleHomeOverride")?.toString()?.takeIf { it.isNotBlank() }?.let {
        hytaleHomeOverride = it
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions)
        .addStringOption("Xdoclint:-missing", "-quiet")
}

// In dev the server's data directory for this plugin is a link to
// src/main/resources, so config.json (written with its defaults on first
// start) lands there. Never ship it.
tasks.named<ProcessResources>("processResources") {
    exclude("config.json", "config.json.bak")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
}
