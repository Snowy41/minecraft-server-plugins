rootProject.name = "minecraft-server"

include(
    "api-module",
    "core-plugin",
    "lobby-plugin",
    "partition"
    //"battleroyale-plugin",
    //"proxy-plugin"
)

// Map the partition folder to PartitionPlugin module
project(":partition").name = "PartitionPlugin"