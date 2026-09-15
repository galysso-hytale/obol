// Public API of Obol. Published standalone so third-party plugins can
// compile against it without depending on the implementation.
//
// Deliberate constraint: the value types and the facade (Obol, Coins,
// Wallet...) depend on the JDK alone, so they are unit-tested here with no
// server in the loop. The one exception is ObolUi, which draws coins into a
// page and therefore names the server's UICommandBuilder: the server jar is
// on the compile classpath only (compileOnly), never a runtime dependency,
// and nothing else in the module may touch it.
plugins {
    `java-library`
    `maven-publish`
}

group = project.property("group").toString()
version = project.property("version").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven {
        name = "Hytale Server Release"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    // For ObolUi only, see the header. Same coordinates the workspace plugin
    // resolves for core.
    compileOnly("com.hypixel.hytale:Server:${property("hytale_version")}")
}

// The reason this module exists without Hytale: value types and the facade
// are unit-tested here, with no server in the loop.
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.13.4")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
        // Standard doclet does not register these by default.
        tags(
            "apiNote:a:API Note:",
            "implSpec:a:Implementation Requirements:",
            "implNote:a:Implementation Note:",
        )
    }
}

// Covers jar, sourcesJar and javadocJar so every published artifact agrees
// with the `artifactId` below.
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("obol-api")
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "obol-api"
            from(components["java"])
        }
    }
}
