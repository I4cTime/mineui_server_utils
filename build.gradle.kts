// Root project: shared coordinates only. Each module owns its own plugins and
// dependencies so ForgeGradle (forge) and the Bukkit API (paper) stay isolated.
allprojects {
    group = providers.gradleProperty("modGroup").get()
    version = providers.gradleProperty("modVersion").get()
}
