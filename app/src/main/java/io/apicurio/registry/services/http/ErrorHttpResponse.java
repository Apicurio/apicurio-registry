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

package io.apicurio.registry.services.http;

import javax.ws.rs.core.Response;

import io.apicurio.registry.rest.v2.beans.Error;

/**
 * @author Fabian Martinez
 */
public class ErrorHttpResponse {

    private int status;
    private Error error;
    private Response jaxrsResponse;

    public ErrorHttpResponse(int status, Error error, Response jaxrsResponse) {
        this.status = status;
        this.error = error;
        this.jaxrsResponse = jaxrsResponse;
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return the error
     */
    public Error getError() {
        return error;
    }

    /**
     * @return the jaxrsResponse
     */
    public Response getJaxrsResponse() {
        return jaxrsResponse;
    }

}
