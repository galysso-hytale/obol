plugins {
    id("com.azuredoom.hytale-workspace") version "1.+"
}

hytaleWorkspace {
    // The loadable Hytale plugins: Obol itself and its add-ons. `api` is a
    // plain library module.
    modProjects = listOf(":core", ":addons:purse", ":addons:lootbag")
    hostProject = ":core"

    // Shared defaults inherited by every `hytaleTools` project.
    manifestGroup = property("manifest_group").toString()
    hytaleVersion = property("hytale_version").toString()
    patchline = property("patchline").toString()
}
