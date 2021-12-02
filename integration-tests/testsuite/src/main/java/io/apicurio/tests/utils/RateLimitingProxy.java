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

package io.apicurio.tests.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Fabian Martinez
 */
public class RateLimitingProxy extends LimitingProxy {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private int buckets;

    public RateLimitingProxy(int failAfterRequests, String destinationHost, int destinationPort) {
        super(destinationHost, destinationPort);
        // this will rate limit just based on total requests
        // that means that if buckets=3 the proxy will successfully redirect the first 3 requests and every request after that will be rejected with 429 status
        this.buckets = failAfterRequests;
    }

    @Override
    protected boolean allowed() {
        if (buckets > 0) {
            buckets--;
            return true;
        }
        return false;
    }
}
