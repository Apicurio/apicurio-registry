package io.apicurio.registry.model;

import jakarta.validation.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public class GroupId {

    private static final Pattern VALID_PATTERN = Pattern.compile(".{1,512}");

    private static final String DEFAULT_STRING = "default";

    private static final String DEFAULT_RAW_GROUP_ID = "__$GROUPID$__"; // TODO: Consider using "default" as a default group ID.

    public static final GroupId DEFAULT = new GroupId(DEFAULT_RAW_GROUP_ID);

    private final String rawGroupId;


    public GroupId(String rawGroupId) {
        if (!isValid(rawGroupId)) {
            throw new ValidationException("Group ID '" + rawGroupId + "' is invalid. " +
                    "It must have length 1..512 (inclusive).");
        }
        this.rawGroupId = rawGroupId == null || DEFAULT_STRING.equalsIgnoreCase(rawGroupId) ? DEFAULT_RAW_GROUP_ID : rawGroupId;
    }


    public boolean isDefaultGroup() {
        return DEFAULT.getRawGroupId().equals(rawGroupId);
    }


    public String getRawGroupIdWithDefaultString() {
        return isDefaultGroup() ? DEFAULT_STRING : rawGroupId;
    }


    public String getRawGroupIdWithNull() {
        return isDefaultGroup() ? null : rawGroupId;
    }


    @Override
    public String toString() {
        return getRawGroupIdWithDefaultString();
    }


    public static boolean isValid(String rawGroupId) {
        return rawGroupId == null || VALID_PATTERN.matcher(rawGroupId).matches();
    }
}
