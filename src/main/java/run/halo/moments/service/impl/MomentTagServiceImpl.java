package run.halo.moments.service.impl;

import static run.halo.app.extension.index.query.QueryFactory.all;
import static run.halo.app.extension.index.query.QueryFactory.equal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
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
 */
@Component
@RequiredArgsConstructor
public class MomentTagServiceImpl implements MomentTagService {

    private final ReactiveExtensionClient client;

    @Override
    public Mono<List<MomentTagVo>> listTagStats(MomentQuery query) {
        return client.listAll(Moment.class, query.toListOptions(),
                Sort.by("metadata.name").descending())
            .flatMapIterable(moment -> {
                var tags = moment.getSpec().getTags();
                if (tags == null || tags.isEmpty()) {
                    return List.<TagMomentPair>of();
                }
                return tags.stream()
                    .map(tag -> new TagMomentPair(tag, moment.getMetadata().getName()))
                    .toList();
            })
            .groupBy(TagMomentPair::tagName)
            .flatMap(groupedFlux -> groupedFlux.count()
                .defaultIfEmpty(0L)
                .map(count -> MomentTagVo.builder()
                    .name(groupedFlux.key())
                    .momentCount(count.intValue())
                    .permalink(buildConsoleTagPermalink(groupedFlux.key()))
                    .build()))
            .collectList();
    }

    @Override
    public Mono<TagMutationResult> renameTag(String oldName, String newName) {
        if (StringUtils.isAnyBlank(oldName, newName)) {
            return Mono.just(TagMutationResult.builder().build());
        }
        if (StringUtils.equals(oldName, newName)) {
            return Mono.just(TagMutationResult.builder().build());
        }
        return mutateMomentsWithTag(oldName, moment -> applyRename(moment, oldName, newName));
    }

    @Override
    public Mono<TagMutationResult> deleteTag(String name) {
        if (StringUtils.isBlank(name)) {
            return Mono.just(TagMutationResult.builder().build());
        }
        return mutateMomentsWithTag(name, moment -> applyDelete(moment, name));
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

    private record TagMomentPair(String tagName, String momentName) {
    }
}