package run.halo.moments.service;

import java.util.List;
import reactor.core.publisher.Mono;
import run.halo.moments.MomentQuery;
import run.halo.moments.vo.MomentTagVo;
import run.halo.moments.vo.TagMutationResult;

/**
 * Service for moment tag aggregation and mutation.
 */
public interface MomentTagService {

    /**
     * List all tags with moment counts for the given query scope.
     */
    Mono<List<MomentTagVo>> listTagStats(MomentQuery query);

    /**
     * Rename a tag across all moments that reference it.
     */
    Mono<TagMutationResult> renameTag(String oldName, String newName);

    /**
     * Delete a tag from all moments that reference it.
     */
    Mono<TagMutationResult> deleteTag(String name);
}