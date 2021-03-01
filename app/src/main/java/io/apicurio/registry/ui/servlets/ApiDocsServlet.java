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

package io.apicurio.registry.ui.servlets;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * A simple servlet that forwards the request to the apidocs.html file.
 * @author eric.wittmann@gmail.com
 */
public class ApiDocsServlet extends GenericServlet {

    private static final long serialVersionUID = 4259630009438256847L;

    private static final Set<String> rootPaths = new HashSet<>();
    static {
        rootPaths.add("/apis");
        rootPaths.add("/apis/");
    }

    private static final boolean isRootPath(String servletPath) {
        return rootPaths.contains(servletPath);
    }

    /**
     * @see javax.servlet.GenericServlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) req;
        String servletPath = request.getServletPath();
        if (isRootPath(servletPath)) {
            req.getRequestDispatcher("/apidocs/index.html").forward(req, res); //$NON-NLS-1$
        } else {
            req.getRequestDispatcher("/apidocs/apidocs.html").forward(req, res); //$NON-NLS-1$
        }
    }

}
