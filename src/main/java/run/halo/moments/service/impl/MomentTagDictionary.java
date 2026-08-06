package run.halo.moments.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Persist predefined moment tags in a ConfigMap so tags can exist even when
 * no moment currently references them (supports "create tag" in tag management).
 */
@Component
@RequiredArgsConstructor
public class MomentTagDictionary {

    public static final String CONFIG_MAP_NAME = "plugin-moments-tag-dictionary";
    public static final String DATA_KEY = "tags";

    private final ReactiveExtensionClient client;

    /**
     * List all dictionary tags in insertion order.
     */
    public Mono<Set<String>> list() {
        return fetchConfigMap()
            .map(this::readTags)
            .defaultIfEmpty(new LinkedHashSet<>());
    }

    /**
     * Add a tag into the dictionary if it does not already exist (case-insensitive).
     *
     * @return true if a new tag was added, false if it already existed.
     */
    public Mono<Boolean> add(String tagName) {
        var normalized = normalize(tagName);
        if (normalized == null) {
            return Mono.just(false);
        }
        return ensureConfigMap()
            .flatMap(configMap -> {
                var tags = readTags(configMap);
                boolean exists = tags.stream()
                    .anyMatch(existing -> existing.equalsIgnoreCase(normalized));
                if (exists) {
                    return Mono.just(false);
                }
                tags.add(normalized);
                writeTags(configMap, tags);
                return client.update(configMap).thenReturn(true);
            });
    }

    /**
     * Rename a tag in the dictionary. No-op when old name is absent.
     */
    public Mono<Void> rename(String oldName, String newName) {
        var oldNormalized = normalize(oldName);
        var newNormalized = normalize(newName);
        if (oldNormalized == null || newNormalized == null) {
            return Mono.empty();
        }
        return ensureConfigMap()
            .flatMap(configMap -> {
                var tags = readTags(configMap);
                boolean changed = false;
                Set<String> next = new LinkedHashSet<>();
                for (String tag : tags) {
                    if (tag.equalsIgnoreCase(oldNormalized)) {
                        next.add(newNormalized);
                        changed = true;
                    } else if (!tag.equalsIgnoreCase(newNormalized)) {
                        next.add(tag);
                    }
                }
                if (!changed) {
                    return Mono.empty();
                }
                writeTags(configMap, next);
                return client.update(configMap).then();
            });
    }

    /**
     * Remove a tag from the dictionary. No-op when absent.
     */
    public Mono<Void> remove(String tagName) {
        var normalized = normalize(tagName);
        if (normalized == null) {
            return Mono.empty();
        }
        return ensureConfigMap()
            .flatMap(configMap -> {
                var tags = readTags(configMap);
                boolean removed = tags.removeIf(tag -> tag.equalsIgnoreCase(normalized));
                if (!removed) {
                    return Mono.empty();
                }
                writeTags(configMap, tags);
                return client.update(configMap).then();
            });
    }

    private Mono<ConfigMap> fetchConfigMap() {
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME);
    }

    private Mono<ConfigMap> ensureConfigMap() {
        return fetchConfigMap()
            .switchIfEmpty(Mono.defer(() -> {
                var configMap = new ConfigMap();
                var metadata = new Metadata();
                metadata.setName(CONFIG_MAP_NAME);
                configMap.setMetadata(metadata);
                writeTags(configMap, new LinkedHashSet<>());
                return client.create(configMap);
            }));
    }

    private Set<String> readTags(ConfigMap configMap) {
        var data = configMap.getData();
        if (data == null || StringUtils.isBlank(data.get(DATA_KEY))) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> parsed = JsonUtils.jsonToObject(data.get(DATA_KEY),
                new TypeReference<List<String>>() {
                });
            var result = new LinkedHashSet<String>();
            if (parsed != null) {
                parsed.stream()
                    .map(this::normalize)
                    .filter(Objects::nonNull)
                    .forEach(result::add);
            }
            return result;
        } catch (Exception ignored) {
            return new LinkedHashSet<>();
        }
    }

    private void writeTags(ConfigMap configMap, Set<String> tags) {
        if (configMap.getData() == null) {
            configMap.setData(new java.util.HashMap<>());
        }
        configMap.getData().put(DATA_KEY, JsonUtils.objectToJson(tags));
    }

    private String normalize(String tagName) {
        if (tagName == null) {
            return null;
        }
        var trimmed = tagName.trim().replaceAll("^#+", "");
        return StringUtils.isBlank(trimmed) ? null : trimmed;
    }
}
