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

package io.apicurio.registry.storage;

/**
 * @author Fabian Martinez
 */
public class GroupNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -5024749463194169679L;

    private final String groupId;

    public GroupNotFoundException(String groupId) {
        this.groupId = groupId;
    }

    public GroupNotFoundException(String groupId, Throwable cause) {
        super(cause);
        this.groupId = groupId;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No group '" + this.groupId + "' was found.";
    }

}
