rootProject.name = "minecraft-server"

include(
    "api-module",
    "core-plugin",
    "lobby-plugin",
    "partition",
    //"battleroyale-plugin",
    //"proxy-plugin"
)

// Map the partition folder to PartitionPlugin module
project(":partition").name = "PartitionPlugin"
include("npc-plugin")
include("social-plugin")
include("social-plugin-velocity")
include("battleroyale")
include("battleroyale-lobby-plugin")
include("GameLobbyPlugin")
include("game-lobby-plugin")
include("structure-plugin")