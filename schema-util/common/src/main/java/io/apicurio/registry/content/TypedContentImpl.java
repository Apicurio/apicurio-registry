package io.apicurio.registry.content;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TypedContentImpl implements TypedContent {
    private String contentType;
    private ContentHandle content;
}
