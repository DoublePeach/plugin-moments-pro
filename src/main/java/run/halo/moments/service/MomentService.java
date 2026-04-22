package run.halo.moments.service;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.moments.ListedMoment;
import run.halo.moments.Moment;
import run.halo.moments.MomentQuery;

/**
 * Service for {@link run.halo.moments.Moment}.
 *
 * @author LIlGG
 * @since 1.0.0
 */
public interface MomentService {
    Mono<ListResult<ListedMoment>> listMoment(MomentQuery query);

    Mono<Moment> create(Moment moment);

    Flux<String> listAllTags(MomentQuery query);

    Mono<ListedMoment> findMomentByName(String name);

    Mono<Moment> getByUsername(String momentName, String username);

    Mono<Moment> updateBy(Moment moment);

    Mono<Moment> deleteBy(Moment moment);

    /**
     * Pin or unpin a moment. When pinning, a new pinOrder will be assigned so the
     * pinned moment floats to the very top.
     */
    Mono<Moment> setPinned(String name, boolean pinned);

    /**
     * Reorder pinned moments in the given order. The first name in the list will
     * become the top pinned moment.
     */
    Mono<Void> reorderPinned(List<String> orderedNames);

    /**
     * Batch delete moments by name.
     *
     * @return the number of moments actually deleted.
     */
    Mono<Long> deleteBatch(List<String> names);

    /**
     * Batch approve moments by name.
     *
     * @return the number of moments actually updated.
     */
    Mono<Long> approveBatch(List<String> names);

    /**
     * Batch update visibility.
     *
     * @return the number of moments actually updated.
     */
    Mono<Long> setVisibleBatch(List<String> names, Moment.MomentVisible visible);
}
