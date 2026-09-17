plugins {
    id("com.azuredoom.hytale-workspace") version "1.+"
}

hytaleWorkspace {
    // The loadable Hytale plugins: Obol itself, its add-ons and its compat
    // mods. `api` is a plain library module.
    modProjects = listOf(":core", ":addons:purse", ":addons:lootbag", ":addons:trade", ":compat:aetherhaven")
    hostProject = ":core"

    // Shared defaults inherited by every `hytaleTools` project.
    manifestGroup = property("manifest_group").toString()
    hytaleVersion = property("hytale_version").toString()
    patchline = property("patchline").toString()
}
