package io.apicurio.registry.references;

import io.apicurio.registry.model.GAV;

import java.util.Collection;
import java.util.Map;

/**
 * Loads artifact versions for {@link ReferenceGraphWalker}, one level of the reference graph at a time.
 * <p>
 * {@link ReferenceGraphWalker} walks breadth-first: at each level it already knows every coordinate it
 * needs next, so it asks for all of them in a single call instead of one query per reference. A SQL-backed
 * loader can turn this into one batched metadata query plus one batched content query per level, which is
 * the whole reason the walker is breadth-first rather than the depth-first recursion it replaces (see
 * {@code DESIGN.md} in the project's tracking folder — walker 4's requirement drove this).
 * <p>
 * A coordinate the implementation does not find (no such artifact version) is simply left out of the
 * returned map — it is not an error. The walker records it as <i>missing</i>, and it is up to the caller of
 * the walker to decide whether a missing version should fail the walk or just be skipped (requirement:
 * walkers 7 and 8 fail on a missing version; walkers 1, 4 and 6 skip it). The loader itself never decides
 * this.
 * <p>
 * A real error (a database error, for instance, as opposed to "not found") should be thrown, not swallowed.
 * The walker does not catch anything a loader throws — see {@link ReferenceGraphWalker}'s class Javadoc.
 * <p>
 * A loader that only knows how to fetch one coordinate at a time — such as the {@code Function<ArtifactReferenceDto,
 * ContentWrapperDto>} loaders the pre-#10634 entry points took — can still implement this interface with a
 * simple loop; it just forgoes the batching benefit. See the adapters in {@link ReferenceGraphWalker} used by
 * {@code RegistryContentUtils}'s thin wrapper methods.
 */
@FunctionalInterface
public interface ReferenceLoader {

    /**
     * Loads every artifact version named in {@code coordinates}. The returned map may be smaller than
     * {@code coordinates} — a coordinate with no entry in the result is missing, not an error.
     *
     * @param coordinates the coordinates to load; never contains a coordinate already seen earlier in the
     *            same walk
     * @return the artifact versions found, keyed by their own coordinates
     */
    Map<GAV, ReferenceNode> loadBatch(Collection<GAV> coordinates);
}
