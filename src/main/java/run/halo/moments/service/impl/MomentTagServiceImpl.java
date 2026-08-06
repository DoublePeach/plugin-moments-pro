package run.halo.moments.service.impl;

import static run.halo.app.extension.index.query.QueryFactory.equal;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.moments.Moment;
import run.halo.moments.MomentQuery;
import run.halo.moments.service.MomentTagService;
import run.halo.moments.util.MomentTagContentUtils;
import run.halo.moments.vo.MomentTagVo;
import run.halo.moments.vo.TagMutationResult;

/**
 * Default implementation for {@link MomentTagService}.
 *
 * <p>Tags live on {@code Moment.spec.tags}. A ConfigMap-backed dictionary also
 * keeps predefined tags that may temporarily have zero moments, so the tag
 * management page can support create / rename / delete as a complete CRUD.
 */
@Component
@RequiredArgsConstructor
public class MomentTagServiceImpl implements MomentTagService {

    private final ReactiveExtensionClient client;
    private final MomentTagDictionary tagDictionary;

    @Override
    public Mono<List<MomentTagVo>> listTagStats(MomentQuery query) {
        Mono<Map<String, Integer>> usageMono = client
            .listAll(Moment.class, query.toListOptions(), Sort.by("metadata.name").descending())
            .reduce(new HashMap<String, Integer>(), (counts, moment) -> {
                var tags = moment.getSpec().getTags();
                if (tags != null) {
                    for (String tag : tags) {
                        if (StringUtils.isNotBlank(tag)) {
                            counts.merge(tag, 1, Integer::sum);
                        }
                    }
                }
                return counts;
            });

        return Mono.zip(usageMono, tagDictionary.list())
            .map(tuple -> {
                Map<String, Integer> usage = tuple.getT1();
                Set<String> dictionary = tuple.getT2();
                Map<String, MomentTagVo> merged = new HashMap<>();
                usage.forEach((name, count) -> merged.put(name, MomentTagVo.builder()
                    .name(name)
                    .momentCount(count)
                    .permalink(buildConsoleTagPermalink(name))
                    .build()));
                for (String name : dictionary) {
                    merged.putIfAbsent(name, MomentTagVo.builder()
                        .name(name)
                        .momentCount(0)
                        .permalink(buildConsoleTagPermalink(name))
                        .build());
                }
                return merged.values().stream()
                    .sorted(Comparator
                        .comparing(MomentTagVo::getMomentCount, Comparator.reverseOrder())
                        .thenComparing(MomentTagVo::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            });
    }

    @Override
    public Mono<MomentTagVo> createTag(String name) {
        var normalized = normalize(name);
        if (normalized == null) {
            return Mono.error(new ServerWebInputException("标签名称不能为空"));
        }
        // Reject when the tag already exists either in dictionary or on any moment.
        var usedOptions = new ListOptions();
        usedOptions.setFieldSelector(FieldSelector.of(equal("spec.tags", normalized)));
        Mono<Boolean> usedOnMoments = client
            .listAll(Moment.class, usedOptions, Sort.unsorted())
            .hasElements();
        return Mono.zip(tagDictionary.list(), usedOnMoments)
            .flatMap(tuple -> {
                boolean inDictionary = tuple.getT1().stream()
                    .anyMatch(tag -> tag.equalsIgnoreCase(normalized));
                if (inDictionary || Boolean.TRUE.equals(tuple.getT2())) {
                    return Mono.error(new ServerWebInputException("标签已存在：" + normalized));
                }
                return tagDictionary.add(normalized)
                    .thenReturn(MomentTagVo.builder()
                        .name(normalized)
                        .momentCount(0)
                        .permalink(buildConsoleTagPermalink(normalized))
                        .build());
            });
    }

    @Override
    public Mono<TagMutationResult> renameTag(String oldName, String newName) {
        var oldNormalized = normalize(oldName);
        var newNormalized = normalize(newName);
        if (oldNormalized == null || newNormalized == null) {
            return Mono.just(TagMutationResult.builder().build());
        }
        if (StringUtils.equals(oldNormalized, newNormalized)) {
            return Mono.just(TagMutationResult.builder().build());
        }
        return mutateMomentsWithTag(oldNormalized,
            moment -> applyRename(moment, oldNormalized, newNormalized))
            .flatMap(result -> tagDictionary.rename(oldNormalized, newNormalized)
                .thenReturn(result));
    }

    @Override
    public Mono<TagMutationResult> deleteTag(String name) {
        var normalized = normalize(name);
        if (normalized == null) {
            return Mono.just(TagMutationResult.builder().build());
        }
        return mutateMomentsWithTag(normalized, moment -> applyDelete(moment, normalized))
            .flatMap(result -> tagDictionary.remove(normalized).thenReturn(result));
    }

    private Mono<TagMutationResult> mutateMomentsWithTag(String tagName,
        java.util.function.Consumer<Moment> mutator) {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(FieldSelector.of(equal("spec.tags", tagName)));
        var affected = new AtomicInteger();
        var failed = new AtomicInteger();
        return client.listAll(Moment.class, listOptions, Sort.unsorted())
            .concatMap(moment -> {
                mutator.accept(moment);
                return client.update(moment)
                    .doOnSuccess(ignored -> affected.incrementAndGet())
                    .onErrorResume(error -> {
                        failed.incrementAndGet();
                        return Mono.empty();
                    });
            })
            .then(Mono.fromSupplier(() -> TagMutationResult.builder()
                .affected(affected.get())
                .failed(failed.get())
                .build()));
    }

    private void applyRename(Moment moment, String oldName, String newName) {
        var spec = moment.getSpec();
        Set<String> tags = spec.getTags();
        if (tags != null) {
            Set<String> nextTags = new HashSet<>();
            for (String tag : tags) {
                nextTags.add(StringUtils.equals(tag, oldName) ? newName : tag);
            }
            spec.setTags(nextTags);
        }
        var content = spec.getContent();
        if (content != null) {
            MomentTagContentUtils.renameTagInContent(content, oldName, newName);
        }
    }

    private void applyDelete(Moment moment, String tagName) {
        var spec = moment.getSpec();
        Set<String> tags = spec.getTags();
        if (tags != null) {
            Set<String> nextTags = new HashSet<>(tags);
            nextTags.remove(tagName);
            spec.setTags(nextTags);
        }
        var content = spec.getContent();
        if (content != null) {
            MomentTagContentUtils.removeTagFromContent(content, tagName);
        }
    }

    private static String buildConsoleTagPermalink(String tagName) {
        return "/moments?tag=" + UriUtils.encode(Objects.requireNonNull(tagName),
            StandardCharsets.UTF_8);
    }

    private String normalize(String tagName) {
        if (tagName == null) {
            return null;
        }
        var trimmed = tagName.trim().replaceAll("^#+", "");
        return StringUtils.isBlank(trimmed) ? null : trimmed;
    }
}
