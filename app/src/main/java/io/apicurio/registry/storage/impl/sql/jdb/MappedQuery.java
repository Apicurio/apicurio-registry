/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.impl.sql.jdb;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author eric.wittmann@gmail.com
 */
public interface MappedQuery<R> {

    public R one();

    public R first();

    public Optional<R> findOne();

    public Optional<R> findFirst();

    public Optional<R> findLast();

    public List<R> list();

    public Stream<R> stream();

}
