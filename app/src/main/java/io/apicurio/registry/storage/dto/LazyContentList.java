/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.storage.dto;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

public class LazyContentList implements List<ContentHandle> {

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
    public ContentHandle get(int index) {
        //Not the best solution, works for now...
        return storage.getArtifactByContentId(contentIds.get(index)).getContent();
    }

    @Override
    public ContentHandle set(int index, ContentHandle element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, ContentHandle element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentHandle remove(int index) {
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
    public ListIterator<ContentHandle> listIterator() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ListIterator<ContentHandle> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public List<ContentHandle> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Iterator<ContentHandle> iterator() {
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
    public boolean add(ContentHandle contentHandle) {
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
    public boolean addAll(@NotNull Collection<? extends ContentHandle> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends ContentHandle> c) {
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
    public Spliterator<ContentHandle> spliterator() {
        //prevent streaming on this list
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(Consumer<? super ContentHandle> action) {
        for (Long contentId : contentIds) {
            ContentHandle retrievedContent = storage.getArtifactByContentId(contentId).getContent();
            action.accept(retrievedContent);
        }
    }

    public List<Long> getContentIds() {
        return contentIds;
    }

    public ContentHandle getContentById(long contentId) {
        if (contentIds.contains(contentId)) {
            return storage.getArtifactByContentId(contentId).getContent();
        } else {
            throw new NoSuchElementException(String.format("No content found with id %d", contentId));
        }
    }

    private static class LazyContentListIterator implements Iterator<ContentHandle> {

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
        public ContentHandle next() {
            Long nextContentId = contentIdsIterator.next();
            return lazyContentList.getContentById(nextContentId);
        }

        @Override
        public void remove() {
            contentIdsIterator.remove();
        }
    }
}