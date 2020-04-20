/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class DiffUtil {

    /**
     * added/removed
     *
     * @return true if both objects are present
     */
    public static boolean diffAddedRemoved(DiffContext ctx, Object original, Object updated,
                                           DiffType addedType, DiffType removedType) {
        if (original == null && updated != null) {
            ctx.addDifference(addedType, original, updated);
        } else if (original != null && updated == null) {
            ctx.addDifference(removedType, original, updated);
        } else {
            return original != null;
        }
        return false;
    }

    public static <T> void diffSetChanged(DiffContext ctx, Set<T> original, Set<T> updated,
                                          DiffType addedType, DiffType removedType, DiffType changedType,
                                          DiffType addedMemberType, DiffType removedMemberType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)) {
            boolean changed = false;
            Set<?> copyUpdated = new HashSet<>(updated);
            for (Object originalMember : original) {
                if (updated.contains(originalMember)) {
                    // OK
                    copyUpdated.remove(originalMember);
                } else {
                    ctx.addDifference(removedMemberType, originalMember, null);
                    changed = true;
                }
            }
            for (Object updatedMemberRemaining : copyUpdated) {
                ctx.addDifference(addedMemberType, null, updatedMemberRemaining);
                changed = true;
            }
            if (changed)
                ctx.addDifference(changedType, original, updated);
        }
    }


    /**
     * @return true if both objects are present
     */
    public static boolean diffSubschemaAddedRemoved(DiffContext ctx, Object original, Object updated,
                                                    DiffType addedType, DiffType removedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)) {
            return true;
            // TODO Remove the explicit EmptySchema checks?
            // TODO Consider change to empty schema as backwards compatible
//            if (original instanceof EmptySchema && (!(updated instanceof EmptySchemaWrapper))) {
//                ctx.addDifference(addedType, original, updated);
//            } else if ((!(original instanceof EmptySchema)) && updated instanceof EmptySchemaWrapper) {
//                ctx.addDifference(removedType, original, updated);
//            } else {
//                return !(original instanceof EmptySchema);
//            }
        }
        return false;
    }

    /**
     * added/removed/increased/decreased
     *
     * @return true if the integers are defined and equal
     */
    public static boolean diffInteger(DiffContext ctx, Integer original, Integer updated,
                                      DiffType addedType, DiffType removedType,
                                      DiffType increasedType, DiffType decreasedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)) {
            if (original < updated) {
                ctx.addDifference(increasedType, original, updated);
            } else if (original > updated) {
                ctx.addDifference(decreasedType, original, updated);
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * added/removed/increased/decreased
     */
    public static void diffNumber(DiffContext ctx, Number original, Number updated,
                                  DiffType addedType, DiffType removedType,
                                  DiffType increasedType, DiffType decreasedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)) {
            BigDecimal o = new BigDecimal(original.toString()); // Not pretty but it works:/
            BigDecimal u = new BigDecimal(updated.toString());
            if (o.compareTo(u) < 0) {
                ctx.addDifference(increasedType, original, updated);
            }
            if (o.compareTo(u) > 0) {
                ctx.addDifference(decreasedType, original, updated);
            }
        }
    }

    /**
     * added/removed/changed
     */
    public static void diffBoolean(DiffContext ctx, Boolean original, Boolean updated,
                                   DiffType addedType, DiffType removedType, DiffType changedType) {
        diffObject(ctx, original, updated, addedType, removedType, changedType);
    }

    /**
     * added/removed/changed
     */
    public static void diffString(DiffContext ctx, String original, String updated,
                                  DiffType addedType, DiffType removedType, DiffType changedType) {
        diffObject(ctx, original, updated, addedType, removedType, changedType);
    }

    /**
     * added/removed/changed (using equals)
     */
    public static void diffObject(DiffContext ctx, Object original, Object updated,
                                  DiffType addedType, DiffType removedType, DiffType changedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)
            && !original.equals(updated)) {
            ctx.addDifference(changedType, original, updated);
        }
    }

    /**
     * added/removed/changed (using ==)
     *
     * @return true if they are equal
     */
    public static boolean diffObjectIdentity(DiffContext ctx, Object original, Object updated,
                                             DiffType addedType, DiffType removedType, DiffType changedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)
            && original != updated) {
            ctx.addDifference(changedType, original, updated);
            return false;
        }
        return true;
    }

    /**
     * Use getter and return null if there is an exception.
     */
    public static <T> T getExceptionally(DiffContext ctx, Supplier<T> getter) {
        try {
            return getter.get();
        } catch (Exception ex) {
            ctx.log("Caught exception when getting exceptionally: " + ex + ". Returning null.");
            return null;
        }
    }
}
