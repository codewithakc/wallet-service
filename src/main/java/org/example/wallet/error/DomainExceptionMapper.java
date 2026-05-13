package org.example.wallet.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.example.wallet.api.response.ErrorResponse;

import java.time.Instant;

@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {
    @Override
    public Response toResponse(DomainException exception) {
        return Response.status(exception.getStatus())
                .entity(new ErrorResponse(exception.getErrorCode(), exception.getMessage(), Instant.now()))
                .build();
    }
}
