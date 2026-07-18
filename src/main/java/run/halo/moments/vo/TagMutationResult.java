package run.halo.moments.vo;

import lombok.Builder;
import lombok.Value;

/**
 * Result of a tag rename or delete mutation.
 */
@Value
@Builder
public class TagMutationResult {

    /**
     * Number of moments successfully updated.
     */
    int affected;

    /**
     * Number of moments that failed to update.
     */
    int failed;
}