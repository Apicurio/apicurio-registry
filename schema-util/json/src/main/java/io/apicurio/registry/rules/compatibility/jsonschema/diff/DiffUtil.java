/*
 * Copyright 2020 Red Hat
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

import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.Schema;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;
import static java.util.Objects.requireNonNull;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
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
     *
     * @return true if the numbers are the same
     */
    public static boolean diffNumber(DiffContext ctx, Number original, Number updated,
                                     DiffType addedType, DiffType removedType,
                                     DiffType increasedType, DiffType decreasedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)) {
            BigDecimal o = new BigDecimal(original.toString()); // Not pretty but it works:/
            BigDecimal u = new BigDecimal(updated.toString());
            if (o.compareTo(u) < 0) {
                ctx.addDifference(increasedType, original, updated);
            } else if (o.compareTo(u) > 0) {
                ctx.addDifference(decreasedType, original, updated);
            } else {
                return true;
            }
        }
        return false;
    }

    public static void diffNumberOriginalMultipleOfUpdated(DiffContext ctx, Number original, Number updated,
                                                           DiffType multipleOfType, DiffType notMultipleOfType) {
        requireNonNull(original);
        requireNonNull(updated);
        BigDecimal o = new BigDecimal(original.toString()); // Not pretty but it works:/
        BigDecimal u = new BigDecimal(updated.toString());
        if (o.remainder(u).equals(BigDecimal.ZERO)) {
            ctx.addDifference(multipleOfType, original, updated);
        } else {
            ctx.addDifference(notMultipleOfType, original, updated);
        }

    }

    /**
     *
     */
    public static boolean diffBooleanTransition(DiffContext ctx, Boolean original, Boolean updated, Boolean defaultValue,
                                             DiffType changeFalseToTrue, DiffType changeTrueToFalse, DiffType unchanged) {
        if (original == null)
            original = defaultValue;
        if (updated == null)
            updated = defaultValue;
        if (original && !updated) {
            ctx.addDifference(changeTrueToFalse, original, updated);
        } else if (!original && updated) {
            ctx.addDifference(changeFalseToTrue, original, updated);
        } else {
            ctx.addDifference(unchanged, original, updated);
            return true;
        }
        return false;
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
     * added/removed/changed (using equals), with a default value specified
     */
    public static void diffObjectDefault(DiffContext ctx, Object original, Object updated, Object defaultValue,
                                         DiffType addedType, DiffType removedType, DiffType changedType) {
        if (Objects.equals(defaultValue, original))
            original = null;
        if (Objects.equals(defaultValue, updated))
            updated = null;
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
    public static boolean diffObjectIdentity(DiffContext ctx, Object original, Object updated, Object target,
                                             DiffType addedType, DiffType removedType, DiffType extendedType,
                                             DiffType narrowedType, DiffType changedType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)
                && original != updated) {
            if (updated == target) {
                ctx.addDifference(extendedType, original, updated);
            } else if (original == target) {
                ctx.addDifference(narrowedType, original, updated);
            } else {
                ctx.addDifference(changedType, original, updated);
            }
            return false;
        }
        return true;
    }

    public static void diffSubSchemasAdded(DiffContext ctx, List<SchemaWrapper> addedSchemas,
                                           boolean originalPermitsAdditional, SchemaWrapper originalSchemaOfAdditional,
                                           boolean updatedPermitsAdditional, DiffType extendedType,
                                           DiffType narrowedType, DiffType changedType) {
        if (!originalPermitsAdditional) {
            // original schema: additional = false
            ctx.addDifference(extendedType, null, addedSchemas);
        } else {
            if (originalSchemaOfAdditional == null) {
                // original schema: additional = true
                ctx.addDifference(narrowedType, true, addedSchemas);
            } else {
                // original schema: additional = schema
                if (!updatedPermitsAdditional &&
                        areListOfSchemasCompatible(ctx, addedSchemas, originalSchemaOfAdditional, false)) {
                    ctx.addDifference(narrowedType, originalSchemaOfAdditional, addedSchemas);
                } else if (updatedPermitsAdditional &&
                        areListOfSchemasCompatible(ctx, addedSchemas, originalSchemaOfAdditional, true)) {
                    ctx.addDifference(extendedType, originalSchemaOfAdditional, addedSchemas);
                } else {
                    ctx.addDifference(changedType, originalSchemaOfAdditional, addedSchemas);
                }
            }
        }
    }

    public static void diffSubSchemasRemoved(DiffContext ctx, List<SchemaWrapper> removedSchemas,
                                             boolean updatedPermitsAdditional, SchemaWrapper updatedSchemaOfAdditional,
                                             boolean originalPermitsAdditional, DiffType narrowedType,
                                             DiffType extendedType, DiffType changedType) {
        if (!updatedPermitsAdditional) {
            // updated schema: additional = false
            ctx.addDifference(narrowedType, removedSchemas, null);
        } else {
            if (updatedSchemaOfAdditional == null) {
                // updated schema: additional = true
                ctx.addDifference(extendedType, removedSchemas, true);
            } else {
                // updated schema: additional = schema
                if (!originalPermitsAdditional &&
                        areListOfSchemasCompatible(ctx, removedSchemas, updatedSchemaOfAdditional, false)) {
                    ctx.addDifference(extendedType, removedSchemas, updatedSchemaOfAdditional);
                } else if (originalPermitsAdditional &&
                        areListOfSchemasCompatible(ctx, removedSchemas, updatedSchemaOfAdditional, true)) {
                    ctx.addDifference(narrowedType, removedSchemas, updatedSchemaOfAdditional);
                } else {
                    ctx.addDifference(changedType, removedSchemas, updatedSchemaOfAdditional);
                }
            }
        }
    }

    public static void diffSchemaOrTrue(DiffContext ctx, Schema original, Schema updated, DiffType bothType,
                                        DiffType extendedType, DiffType narrowedType, DiffType noneType) {
        if (original != null && updated == null) {
            // schema => true
            ctx.addDifference(extendedType, original, updated);
        } else if (original == null && updated != null) {
            // true => schema
            ctx.addDifference(narrowedType, original, updated);
        } else if (updated != null && original != null) {
            // schema => schema
            compareSchemaWhenExist(ctx, original, updated, bothType, extendedType, narrowedType, noneType);
        }
    }

    public static void compareSchema(DiffContext ctx, Schema original, Schema updated,
                                     DiffType addedType, DiffType removedType,
                                     DiffType bothType,
                                     DiffType backwardNotForwardType,
                                     DiffType forwardNotBackwardType,
                                     DiffType noneType) {
        if (diffAddedRemoved(ctx, original, updated, addedType, removedType)) {
            compareSchemaWhenExist(ctx, original, updated, bothType, backwardNotForwardType, forwardNotBackwardType,
                    noneType);
        }
    }

    public static void compareSchemaWhenExist(DiffContext ctx, Schema original, Schema updated, DiffType bothType,
                                               DiffType backwardType, DiffType forwardType, DiffType noneType) {
        boolean backward = isSchemaCompatible(ctx, original, updated, true);
        boolean forward = isSchemaCompatible(ctx, original, updated, false);

        if (backward && forward) {
            ctx.addDifference(bothType, original, updated);
        }
        if (backward && !forward) {
            ctx.addDifference(backwardType, original, updated);
        }
        if (!backward && forward) {
            ctx.addDifference(forwardType, original, updated);
        }
        if (!backward && !forward) {
            ctx.addDifference(noneType, original, updated);
        }
    }

    public static boolean areListOfSchemasCompatible(DiffContext ctx, List<SchemaWrapper> itemSchemas, SchemaWrapper additionalSchema,
                                                     boolean notReverse) {
        for (SchemaWrapper itemSchema: itemSchemas) {
            if (!isSchemaCompatible(ctx, itemSchema.getWrapped(), additionalSchema.getWrapped(), notReverse)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSchemaCompatible(DiffContext ctx, Schema original, Schema updated, boolean backward) {
        DiffContext rootCtx = DiffContext.createRootContext("", ctx.visited);
        if (backward) {
            new SchemaDiffVisitor(rootCtx, original).visit(wrap(updated));
        } else {
            new SchemaDiffVisitor(rootCtx, updated).visit(wrap(original));
        }
        return rootCtx.foundAllDifferencesAreCompatible();
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
