package io.apicurio.registry.ui.servlets;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A simple servlet that forwards the request to the apidocs.html file.
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
     * @see jakarta.servlet.GenericServlet#service(jakarta.servlet.ServletRequest,
     *      jakarta.servlet.ServletResponse)
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
