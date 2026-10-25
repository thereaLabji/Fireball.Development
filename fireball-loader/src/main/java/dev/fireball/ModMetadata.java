package dev.fireball;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata describing a mod.
 */
public final class ModMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> authors;
    private final String entrypoint;
    private final List<String> depends;

    public ModMetadata(String id, String name, String version, String description,
                       List<String> authors, String entrypoint, List<String> depends) {
        this.id = Objects.requireNonNull(id, "mod id");
        this.name = name == null || name.isBlank() ? id : name;
        this.version = version == null || version.isBlank() ? "1.0.0" : version;
        this.description = description == null ? "" : description;
        this.authors = authors == null ? Collections.emptyList() : List.copyOf(authors);
        this.entrypoint = entrypoint;
        this.depends = depends == null ? Collections.emptyList() : List.copyOf(depends);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public List<String> getAuthors() { return authors; }
    public String getEntrypoint() { return entrypoint; }
    public List<String> getDepends() { return depends; }

    @Override
    public String toString() {
        return name + " (" + id + ") v" + version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private String version = "1.0.0";
        private String description = "";
        private List<String> authors = List.of();
        private String entrypoint;
        private List<String> depends = List.of();

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder authors(String... authors) { this.authors = Arrays.asList(authors); return this; }
        public Builder authors(List<String> authors) { this.authors = authors; return this; }
        public Builder entrypoint(String entrypoint) { this.entrypoint = entrypoint; return this; }
        public Builder depends(String... depends) { this.depends = Arrays.asList(depends); return this; }
        public Builder depends(List<String> depends) { this.depends = depends; return this; }

        public ModMetadata build() {
            return new ModMetadata(id, name, version, description, authors, entrypoint, depends);
        }
    }
}
