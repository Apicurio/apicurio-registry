package io.apicurio.registry.content;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class TypedContentImpl implements TypedContent {

    private String contentType;

    private ContentHandle content;
}
