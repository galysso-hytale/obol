// Obol Trade: an add-on of Obol, a loadable Hytale plugin of its own that
// depends on Obol at runtime (manifest `Dependencies`) and slots into Hail's
// menu when Hail is there (manifest `OptionalDependencies`).
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
    // hail-api, published from the Hail repository with
    // `./gradlew :api:publishToMavenLocal` (see README).
    mavenLocal()
}

// `runAllMods` stages every workspace project from its class output, Obol
// included, and would stage Obol twice if this project also brought its jar.
val standaloneRun = gradle.startParameter.taskNames.none {
    it.endsWith("runAllMods") || it.endsWith("stageAllModAssets")
}

// `-PwithoutHail` runs this project's own server without Hail, to exercise
// the fallback hook. Both configurations are worth a look after a change.
val withHail = !project.hasProperty("withoutHail")
val hailJar = rootProject.file("../hail/core/build/libs/Hail-0.1.0.jar")

dependencies {
    // The API types, to compile against. Never bundled: Obol provides them at
    // runtime, and a second copy of Coins would make ClassCastExceptions.
    compileOnly(project(":api"))
    // Hail's API, same rule: only HailBridge names these types, and the
    // classes come from Hail's own jar at runtime, or never load at all.
    compileOnly("dev.galysso.hail:hail-api:0.1.0")
    // Obol, for this project's own server (`:addons:trade:runServer`).
    // vineImplementation rather than vineMod: in dev this plugin's classes
    // sit on the server's classpath (system class loader), which cannot see
    // a plugin loaded from a jar in run/mods. vineImplementation puts Obol
    // on that same classpath (and in run/mods, which the server then
    // skips as already registered). Not transitive, so obol-api.jar (which
    // has no manifest) is not staged along.
    if (standaloneRun) {
        vineImplementation(project(":core")) { isTransitive = false }
        // Hail, for the same reason, from its jar in the sibling repository
        // (`./gradlew :core:jar` there). Skipped with -PwithoutHail.
        if (withHail && hailJar.isFile) {
            vineImplementation(files(hailJar))
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
// src/main/resources, so trade.json (the config, written with its defaults
// on first start) lands there. Never ship it.
tasks.named<ProcessResources>("processResources") {
    exclude("trade.json", "trade.json.bak")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
}
