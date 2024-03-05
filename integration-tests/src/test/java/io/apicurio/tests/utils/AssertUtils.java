package io.apicurio.tests.utils;

import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class AssertUtils {

    @SafeVarargs
    public static <T> T exactlyOne(Function<T, Boolean> condition, T... items) {
        List<T> selected = new ArrayList<>();
        for (T item : items) {
            if (condition.apply(item)) {
                selected.add(item);
            }
        }
        if (selected.size() == 1) {
            return selected.get(0);
        } else {
            throw new AssertionFailedError("None or more than one item fulfilled the condition: " + selected);
        }
    }


    @SafeVarargs
    public static <T> void none(Function<T, Boolean> condition, T... items) {
        List<T> selected = new ArrayList<>();
        for (T item : items) {
            if (condition.apply(item)) {
                selected.add(item);
            }
        }
        if (selected.size() != 0) {
            throw new AssertionFailedError("One or more items fulfilled the condition: " + selected);
        }
    }
}
