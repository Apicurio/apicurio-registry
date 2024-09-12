package io.apicurio.registry.content;

public interface TypedContent {

    static TypedContent create(ContentHandle content, String contentType) {
        return TypedContentImpl.builder().contentType(contentType).content(content).build();
    }

    static TypedContent create(String content, String contentType) {
        return TypedContentImpl.builder().contentType(contentType).content(ContentHandle.create(content))
                .build();
    }

    ContentHandle getContent();

    String getContentType();
}
