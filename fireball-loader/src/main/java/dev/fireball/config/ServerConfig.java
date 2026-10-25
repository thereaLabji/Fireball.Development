package dev.fireball.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fireball server configuration — format matches server.yml:
 *
 * version: 1.0.0
 * voltage:
 *   custom-links: ...
 *   java: ...
 *   resource: ...
 *   render: ...
 *   mc-version: 1.21.1
 */
public final class ServerConfig {
    public static final String FILE_NAME = "server.yml";
    public static final String CONFIG_VERSION = "1.0.0";

    // top-level
    private String version = CONFIG_VERSION;

    // voltage.custom-links
    private final Map<String, List<Map<String, String>>> customLinks = new LinkedHashMap<>();

    // voltage.java
    private int maxPlayers = 5;
    private int port = 25565;
    private String address = "0.0.0.0";
    private int proxy = -1;
    private String proxyAddress = "-1";
    private String motd = "A Fireball Server";

    // voltage.resource
    private boolean resourcePacksEnabled = true;

    // voltage.render
    private int renderDistance = 6;
    private int simulationDistance = 8;
    private int maxPackets = 8500;

    // voltage.mc-version
    private String mcVersion = "1.21.1";

    // ---- getters / setters used by the server ----

    public String getVersion() { return version; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    /** Bind address (voltage.java.address). */
    public String getServerIp() { return address; }
    public void setServerIp(String address) { this.address = address == null ? "0.0.0.0" : address; }

    public int getProxy() { return proxy; }
    public void setProxy(int proxy) { this.proxy = proxy; }

    public String getProxyAddress() { return proxyAddress; }
    public void setProxyAddress(String proxyAddress) { this.proxyAddress = proxyAddress; }

    public boolean isResourcePacksEnabled() { return resourcePacksEnabled; }
    public void setResourcePacksEnabled(boolean resourcePacksEnabled) { this.resourcePacksEnabled = resourcePacksEnabled; }

    public int getViewDistance() { return renderDistance; }
    public void setViewDistance(int renderDistance) { this.renderDistance = Math.max(2, renderDistance); }

    public int getSimulationDistance() { return simulationDistance; }
    public void setSimulationDistance(int simulationDistance) { this.simulationDistance = Math.max(5, simulationDistance); }

    public int getMaxPackets() { return maxPackets; }
    public void setMaxPackets(int maxPackets) { this.maxPackets = maxPackets; }

    public String getMcVersion() { return mcVersion; }
    public void setMcVersion(String mcVersion) { this.mcVersion = mcVersion; }

    /** MOTD derived for status ping (not in this YAML schema; fixed label). */
    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = (motd == null || motd.isBlank()) ? "A Fireball Server" : motd;
    }

    public Map<String, List<Map<String, String>>> getCustomLinks() {
        return customLinks;
    }

    public static ServerConfig loadOrCreate(Path serverDir) throws IOException {
        Path file = serverDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            ServerConfig defaults = new ServerConfig();
            defaults.initDefaultLinks();
            defaults.save(file);
            return defaults;
        }
        return load(file);
    }

    @SuppressWarnings("unchecked")
    public static ServerConfig load(Path file) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
        try (InputStream in = Files.newInputStream(file)) {
            Object raw = yaml.load(in);
            ServerConfig cfg = new ServerConfig();
            if (raw instanceof Map<?, ?> map) {
                cfg.applyRoot((Map<String, Object>) map);
            }
            return cfg;
        }
    }

    public void save(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);

        // Write with comments manually for clarity
        StringBuilder sb = new StringBuilder();
        sb.append("version: ").append(CONFIG_VERSION).append(" # DO NOT TOUCH; auto generated by the server.\n\n");
        sb.append("voltage:\n");
        sb.append("    custom-links:\n");
        if (customLinks.isEmpty()) {
            sb.append("        discord:\n");
            sb.append("            - TupSMP: \"https://www.discord.gg/JntNqC7BAS\"\n");
            sb.append("        bug-report:\n");
            sb.append("            - \"https://discord.com/channels/1543960766506467470/1545447569788899450\"\n");
        } else {
            for (Map.Entry<String, List<Map<String, String>>> e : customLinks.entrySet()) {
                sb.append("        ").append(e.getKey()).append(":\n");
                for (Map<String, String> item : e.getValue()) {
                    if (item.size() == 1) {
                        Map.Entry<String, String> only = item.entrySet().iterator().next();
                        if (only.getKey().isEmpty()) {
                            sb.append("            - \"").append(escape(only.getValue())).append("\"\n");
                        } else {
                            sb.append("            - ").append(only.getKey()).append(": \"")
                                    .append(escape(only.getValue())).append("\"\n");
                        }
                    } else {
                        for (Map.Entry<String, String> kv : item.entrySet()) {
                            sb.append("            - ").append(kv.getKey()).append(": \"")
                                    .append(escape(kv.getValue())).append("\"\n");
                        }
                    }
                }
            }
        }
        sb.append("    java:\n");
        sb.append("      max-players: ").append(maxPlayers).append(" # Max players\n");
        sb.append("      port: ").append(port).append(" # set your own port\n");
        sb.append("      address: ").append(address).append(" # set to localhost if only you are playing. Else, just let it stay\n");
        sb.append("      proxy: ").append(proxy).append(" # -1 and 0 are off, 1 is on\n");
        sb.append("      proxy-address: ").append(proxyAddress).append(" # -1 is None, else: address\n");
        sb.append("      motd: \"").append(escape(motd)).append("\" # server list message\n");
        sb.append("    resource:\n");
        sb.append("        resource-packs:\n");
        sb.append("            enabled: ").append(resourcePacksEnabled).append(" # enable resource packs\n");
        sb.append("    render:\n");
        sb.append("        render-distance: ").append(renderDistance).append(" # min: 2\n");
        sb.append("        simulation-distance: ").append(simulationDistance).append(" # min: 5\n");
        sb.append("        max-packets: ").append(maxPackets).append(" # -1 disables it, default 8500\n");
        sb.append("    mc-version: ").append(mcVersion).append(" # set your minecraft version here; Default: 1.21.1\n");

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private void initDefaultLinks() {
        customLinks.clear();
        List<Map<String, String>> discord = new ArrayList<>();
        Map<String, String> d = new LinkedHashMap<>();
        d.put("TupSMP", "https://www.discord.gg/JntNqC7BAS");
        discord.add(d);
        customLinks.put("discord", discord);

        List<Map<String, String>> bug = new ArrayList<>();
        Map<String, String> b = new LinkedHashMap<>();
        b.put("", "https://discord.com/channels/1543960766506467470/1545447569788899450");
        bug.add(b);
        customLinks.put("bug-report", bug);
    }

    @SuppressWarnings("unchecked")
    private void applyRoot(Map<String, Object> root) {
        if (root.containsKey("version")) {
            version = str(root.get("version"), CONFIG_VERSION);
        }
        Object voltageObj = root.get("voltage");
        if (!(voltageObj instanceof Map<?, ?> voltage)) {
            return;
        }
        Map<String, Object> v = (Map<String, Object>) voltage;

        // custom-links
        Object linksObj = v.get("custom-links");
        if (linksObj instanceof Map<?, ?> linksMap) {
            customLinks.clear();
            for (Map.Entry<?, ?> e : linksMap.entrySet()) {
                String key = String.valueOf(e.getKey());
                List<Map<String, String>> list = new ArrayList<>();
                if (e.getValue() instanceof List<?> items) {
                    for (Object item : items) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        if (item instanceof Map<?, ?> m) {
                            for (Map.Entry<?, ?> kv : m.entrySet()) {
                                entry.put(String.valueOf(kv.getKey()), String.valueOf(kv.getValue()));
                            }
                        } else if (item != null) {
                            entry.put("", String.valueOf(item));
                        }
                        list.add(entry);
                    }
                }
                customLinks.put(key, list);
            }
        }

        // java
        Object javaObj = v.get("java");
        if (javaObj instanceof Map<?, ?> javaMap) {
            Map<String, Object> j = (Map<String, Object>) javaMap;
            if (j.containsKey("max-players")) maxPlayers = integer(j.get("max-players"), maxPlayers);
            if (j.containsKey("port")) port = integer(j.get("port"), port);
            if (j.containsKey("address")) address = str(j.get("address"), address);
            if (j.containsKey("proxy")) proxy = integer(j.get("proxy"), proxy);
            if (j.containsKey("proxy-address")) proxyAddress = str(j.get("proxy-address"), proxyAddress);
            if (j.containsKey("motd")) motd = str(j.get("motd"), motd);
        }

        // resource
        Object resourceObj = v.get("resource");
        if (resourceObj instanceof Map<?, ?> resourceMap) {
            Object rp = ((Map<?, ?>) resourceMap).get("resource-packs");
            if (rp instanceof Map<?, ?> rpMap) {
                if (rpMap.containsKey("enabled")) {
                    resourcePacksEnabled = bool(rpMap.get("enabled"), resourcePacksEnabled);
                }
            }
        }

        // render
        Object renderObj = v.get("render");
        if (renderObj instanceof Map<?, ?> renderMap) {
            Map<String, Object> r = (Map<String, Object>) renderMap;
            if (r.containsKey("render-distance")) {
                renderDistance = Math.max(2, integer(r.get("render-distance"), renderDistance));
            }
            if (r.containsKey("simulation-distance")) {
                simulationDistance = Math.max(5, integer(r.get("simulation-distance"), simulationDistance));
            }
            if (r.containsKey("max-packets")) {
                maxPackets = integer(r.get("max-packets"), maxPackets);
            }
        }

        // Prefer mc-version; accept legacy typo mc-verson
        if (v.containsKey("mc-version")) {
            mcVersion = str(v.get("mc-version"), mcVersion);
        } else if (v.containsKey("mc-verson")) {
            mcVersion = str(v.get("mc-verson"), mcVersion);
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    private static int integer(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static boolean bool(Object v, boolean def) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }
}
