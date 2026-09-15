// Obol Lootbag: an add-on of Obol, a loadable Hytale plugin of its own
// that depends on Obol at runtime (manifest `Dependencies`).
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

dependencies {
    // The API types, to compile against. Never bundled: Obol provides them at
    // runtime, and a second copy of Coins would make ClassCastExceptions.
    compileOnly(project(":api"))
    // Obol, for this project's own server (`:addons:lootbag:runServer`).
    // vineImplementation rather than vineMod: in dev this plugin's classes
    // sit on the server's classpath (system class loader), which cannot see
    // a plugin loaded from a jar in run/mods. vineImplementation puts Obol
    // on that same classpath (and in run/mods, which the server then
    // skips as already registered). Not transitive, so obol-api.jar (which
    // has no manifest) is not staged along.
    if (standaloneRun) {
        vineImplementation(project(":core")) { isTransitive = false }
    }
}

// LootLaw is plain JDK code plus Obol's Coins: it is unit-tested here, with
// no server in the loop, like the `api` and `core` modules.
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.13.4")
            dependencies {
                implementation(project(":api"))
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
    includesPack = property("includes_pack").toString().toBoolean()
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
// src/main/resources, so lootbag.json (the config, written with its defaults
// on first start) lands there. Never ship it.
tasks.named<ProcessResources>("processResources") {
    exclude("lootbag.json", "lootbag.json.bak")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
}
