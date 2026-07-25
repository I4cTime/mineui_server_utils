pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net")
    }
}

rootProject.name = "mineui-server-utils"

include("core", "forge", "paper")
