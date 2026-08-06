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
     * Includes dictionary tags that currently have zero moments.
     */
    Mono<List<MomentTagVo>> listTagStats(MomentQuery query);

    /**
     * Create a predefined tag in the tag dictionary so it can be selected
     * even before any moment references it.
     *
     * @return the created tag stats (momentCount is usually 0)
     */
    Mono<MomentTagVo> createTag(String name);

    /**
     * Rename a tag across all moments that reference it, and update the dictionary.
     */
    Mono<TagMutationResult> renameTag(String oldName, String newName);

    /**
     * Delete a tag from all moments that reference it, and remove it from the dictionary.
     */
    Mono<TagMutationResult> deleteTag(String name);
}