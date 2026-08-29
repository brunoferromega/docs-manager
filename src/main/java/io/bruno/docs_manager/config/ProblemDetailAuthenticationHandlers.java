package io.bruno.docs_manager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

/**
 * Keeps 401/403 responses in the same {@code application/problem+json} shape the rest of the API
 * uses; the defaults return an empty body.
 */
final class ProblemDetailAuthenticationHandlers {

    private ProblemDetailAuthenticationHandlers() {}

    static AuthenticationEntryPoint entryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                write(objectMapper, request, response, HttpStatus.UNAUTHORIZED, "Authentication required",
                        describe(exception));
    }

    static AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                write(objectMapper, request, response, HttpStatus.FORBIDDEN, "Access denied",
                        describe(exception));
    }

    private static String describe(Exception exception) {
        return exception instanceof AuthenticationException || exception instanceof AccessDeniedException
                ? exception.getMessage()
                : "Request could not be authorised";
    }

    private static void write(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail)
            throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
