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

package io.apicurio.registry.rest.client.request;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class Parameters {

    public static String LIMIT = "limit";
    public static String OFFSET = "offset";
    public static String SORT_ORDER = "order";
    public static String ORDER_BY = "orderby";
    public static String CANONICAL = "canonical";
    public static String NAME = "name";
    public static String GROUP = "group";
    public static String DESCRIPTION = "description";
    public static String PROPERTIES = "properties";
    public static String LABELS = "labels";
    public static String IF_EXISTS = "ifExists";
    public static String GLOBAL_ID = "globalId";
    public static String CONTENT_ID = "contentId";
    public static final String DEREFERENCE = "dereference";
}
