package com.yourserver.battleroyale.redis;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * CloudNet 4.0 Service Name Detection - FINAL WORKING VERSION
 *
 * Uses CloudNet's ServiceEnvironment to get the current service info.
 */
public class CloudNetServiceDetector {

    private final Plugin plugin;

    public CloudNetServiceDetector(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects CloudNet service name using CloudNet 4.0 ServiceEnvironment API.
     *
     * @return Service name (e.g., "BattleRoyale-1") or fallback
     */
    @NotNull
    public String detectServiceName() {
        // Try CloudNet API first
        String name = tryCloudNetServiceEnvironment();
        if (name != null && !name.isEmpty()) {
            plugin.getLogger().info("✓ CloudNet service detected: " + name);
            return name;
        }

        // Fallback to system properties
        name = System.getProperty("cloudnet.wrapper.serviceInfo.name");
        if (name != null && !name.isEmpty()) {
            plugin.getLogger().info("✓ CloudNet service from property: " + name);
            return name;
        }

        // Last resort fallback
        name = plugin.getServer().getName();
        plugin.getLogger().warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        plugin.getLogger().warning("⚠ CLOUDNET API FAILED!");
        plugin.getLogger().warning("⚠ Using fallback: " + name);
        plugin.getLogger().warning("⚠ This will break lobby integration!");
        plugin.getLogger().warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return name;
    }

    /**
     * Uses CloudNet 4.0 ServiceEnvironment to get current service info.
     * This is the CORRECT way to get service info in CloudNet 4.0.
     */
    private String tryCloudNetServiceEnvironment() {
        try {
            // Get ServiceEnvironment class
            Class<?> serviceEnvClass = Class.forName("eu.cloudnetservice.wrapper.configuration.WrapperConfiguration");

            // Get the instance() static method
            Method instanceMethod = serviceEnvClass.getMethod("instance");
            instanceMethod.setAccessible(true);
            Object wrapperConfig = instanceMethod.invoke(null);

            if (wrapperConfig == null) {
                plugin.getLogger().warning("WrapperConfiguration instance is null");
                return tryAlternativeMethod();
            }

            // Get serviceConfiguration()
            Method serviceConfigMethod = wrapperConfig.getClass().getMethod("serviceConfiguration");
            serviceConfigMethod.setAccessible(true);
            Object serviceConfig = serviceConfigMethod.invoke(wrapperConfig);

            if (serviceConfig == null) {
                plugin.getLogger().warning("ServiceConfiguration is null");
                return tryAlternativeMethod();
            }

            // Get serviceId()
            Method serviceIdMethod = serviceConfig.getClass().getMethod("serviceId");
            serviceIdMethod.setAccessible(true);
            Object serviceId = serviceIdMethod.invoke(serviceConfig);

            if (serviceId == null) {
                plugin.getLogger().warning("ServiceId is null");
                return tryAlternativeMethod();
            }

            // Get name()
            Method nameMethod = serviceId.getClass().getMethod("name");
            nameMethod.setAccessible(true);
            String name = (String) nameMethod.invoke(serviceId);

            plugin.getLogger().info("CloudNet ServiceEnvironment: " + name);
            return name;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().fine("WrapperConfiguration not found, trying alternative...");
            return tryAlternativeMethod();
        } catch (Exception e) {
            plugin.getLogger().warning("ServiceEnvironment API error: " + e.getMessage());
            return tryAlternativeMethod();
        }
    }

    /**
     * Alternative method: Read from wrapper config file.
     * CloudNet stores service info in .wrapper/wrapper.json
     */
    private String tryAlternativeMethod() {
        try {
            // Try to read wrapper.json
            java.io.File wrapperFile = new java.io.File(".wrapper/wrapper.json");
            if (wrapperFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(wrapperFile.toPath()));

                // CloudNet 4.0 stores: taskName + nameSplitter + taskServiceId
                // Example: "BattleRoyale" + "-" + 1 = "BattleRoyale-1"

                String taskName = extractJsonField(content, "taskName");
                String nameSplitter = extractJsonField(content, "nameSplitter");
                String taskServiceId = extractJsonField(content, "taskServiceId");

                if (taskName != null && nameSplitter != null && taskServiceId != null) {
                    String serviceName = taskName + nameSplitter + taskServiceId;
                    plugin.getLogger().info("✓ CloudNet service constructed: " + serviceName);
                    plugin.getLogger().info("  (taskName=" + taskName + ", splitter=" + nameSplitter + ", id=" + taskServiceId + ")");
                    return serviceName;
                }

                plugin.getLogger().warning("Could not construct service name from wrapper.json");
                plugin.getLogger().warning("  taskName=" + taskName);
                plugin.getLogger().warning("  nameSplitter=" + nameSplitter);
                plugin.getLogger().warning("  taskServiceId=" + taskServiceId);

            } else {
                plugin.getLogger().warning("wrapper.json not found at: " + wrapperFile.getAbsolutePath());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read wrapper.json: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Extract a JSON field value (handles both strings and numbers).
     */
    private String extractJsonField(String json, String fieldName) {
        try {
            String searchKey = "\"" + fieldName + "\"";
            int index = json.indexOf(searchKey);
            if (index > 0) {
                int colonIndex = json.indexOf(":", index);

                // Skip whitespace after colon
                int valueStart = colonIndex + 1;
                while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                    valueStart++;
                }

                // Check if it's a quoted string
                if (json.charAt(valueStart) == '"') {
                    int startQuote = valueStart;
                    int endQuote = json.indexOf("\"", startQuote + 1);
                    if (endQuote > startQuote) {
                        return json.substring(startQuote + 1, endQuote);
                    }
                } else {
                    // It's a number or boolean - extract until comma, brace, or bracket
                    int valueEnd = valueStart;
                    while (valueEnd < json.length()) {
                        char c = json.charAt(valueEnd);
                        if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                            break;
                        }
                        valueEnd++;
                    }
                    return json.substring(valueStart, valueEnd).trim();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Logs all detected CloudNet properties for debugging.
     */
    public void debugProperties() {
        plugin.getLogger().info("━━━━━━━━ CLOUDNET DEBUG ━━━━━━━━");
        plugin.getLogger().info("Detection Methods:");

        String method1 = tryCloudNetServiceEnvironment();
        plugin.getLogger().info("  ServiceEnvironment: " + (method1 != null ? method1 : "null"));

        String method2 = tryAlternativeMethod();
        plugin.getLogger().info("  wrapper.json: " + (method2 != null ? method2 : "null"));

        String method3 = System.getProperty("cloudnet.wrapper.serviceInfo.name");
        plugin.getLogger().info("  System Property: " + (method3 != null ? method3 : "null"));

        plugin.getLogger().info("Server Name Fallback: " + plugin.getServer().getName());

        // Check if wrapper.json exists
        java.io.File wrapperFile = new java.io.File(".wrapper/wrapper.json");
        plugin.getLogger().info("wrapper.json exists: " + wrapperFile.exists());
        if (wrapperFile.exists()) {
            plugin.getLogger().info("wrapper.json path: " + wrapperFile.getAbsolutePath());
        }

        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Checks if running in CloudNet environment.
     */
    public boolean isCloudNetEnvironment() {
        // Check if wrapper config exists
        return new java.io.File(".wrapper/wrapper.json").exists();
    }
}