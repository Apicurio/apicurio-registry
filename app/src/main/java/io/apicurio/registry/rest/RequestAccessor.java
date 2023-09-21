/*
 * Copyright 2023 Red Hat Inc
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

package io.apicurio.registry.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class RequestAccessor {
    
    private static final ThreadLocal<HttpServletRequest> local = new ThreadLocal<>();

    public HttpServletRequest getRequest() {
        return local.get();
    }
    
    public void setRequest(HttpServletRequest request) {
        local.set(request);
    }
    
    public void clearRequest(HttpServletRequest request) {
        local.set(null);
    }

}
