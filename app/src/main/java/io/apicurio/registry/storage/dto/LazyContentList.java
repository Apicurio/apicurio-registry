package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.RegistryStorage;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

public class LazyContentList implements List<TypedContent> {

    private final RegistryStorage storage;
    private final List<Long> contentIds;

    public LazyContentList(RegistryStorage storage, List<Long> contentIds) {
        this.storage = storage;
        this.contentIds = contentIds;
    }

    @Override
    public int size() {
        return contentIds.size();
    }

    @Override
    public boolean isEmpty() {
        return contentIds.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypedContent get(int index) {
        // Not the best solution, works for now...
        return toTypedContent(storage.getContentById(contentIds.get(index)));
    }

    @Override
    public TypedContent set(int index, TypedContent element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, TypedContent element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypedContent remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ListIterator<TypedContent> listIterator() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ListIterator<TypedContent> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public List<TypedContent> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Iterator<TypedContent> iterator() {
        return new LazyContentListIterator(this, contentIds.iterator());
    }

    @NotNull
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(TypedContent contentHandle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends TypedContent> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends TypedContent> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Spliterator<TypedContent> spliterator() {
        // prevent streaming on this list
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(Consumer<? super TypedContent> action) {
        for (Long contentId : contentIds) {
            TypedContent retrievedContent = toTypedContent(storage.getContentById(contentId));
            action.accept(retrievedContent);
        }
    }

    public List<Long> getContentIds() {
        return contentIds;
    }

    public TypedContent getContentById(long contentId) {
        if (contentIds.contains(contentId)) {
            return toTypedContent(storage.getContentById(contentId));
        } else {
            throw new NoSuchElementException(String.format("No content found with id %d", contentId));
        }
    }

    private static class LazyContentListIterator implements Iterator<TypedContent> {

        private final LazyContentList lazyContentList;
        private final Iterator<Long> contentIdsIterator;

        private LazyContentListIterator(LazyContentList lazyContentList, Iterator<Long> contentIdsIterator) {
            this.lazyContentList = lazyContentList;
            this.contentIdsIterator = contentIdsIterator;
        }

        @Override
        public boolean hasNext() {
            return contentIdsIterator.hasNext();
        }

        @Override
        public TypedContent next() {
            Long nextContentId = contentIdsIterator.next();
            return lazyContentList.getContentById(nextContentId);
        }

        @Override
        public void remove() {
            contentIdsIterator.remove();
        }
    }

    private static TypedContent toTypedContent(ContentWrapperDto dto) {
        return TypedContent.create(dto.getContent(), dto.getContentType());
    }
}