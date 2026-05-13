package org.example.wallet.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.example.wallet.api.response.ErrorResponse;

import java.time.Instant;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred.",
                        Instant.now()))
                .build();
    }
}
