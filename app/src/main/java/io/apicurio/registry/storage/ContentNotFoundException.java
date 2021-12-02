/*
 * Copyright 2020 Red Hat
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
 * @author eric.wittmann@gmail.com
 */
public class ContentNotFoundException extends NotFoundException {
    
    private static final long serialVersionUID = -3640094007953927715L;
    
    private String contentIdentifier;

    /**
     * Constructor.
     * @param contentIdentifier
     */
    public ContentNotFoundException(String contentIdentifier) {
        this.setContentIdentifier(contentIdentifier);
    }

    /**
     * @return the contentIdentifier
     */
    public String getContentIdentifier() {
        return contentIdentifier;
    }

    /**
     * @param contentIdentifier the contentIdentifier to set
     */
    public void setContentIdentifier(String contentIdentifier) {
        this.contentIdentifier = contentIdentifier;
    }

    /**
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        return "No content with id/hash '" + this.contentIdentifier + "' was found.";
    }

}
