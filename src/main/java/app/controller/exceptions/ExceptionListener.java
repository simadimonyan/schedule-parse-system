package app.controller.exceptions;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import app.repository.models.dto.api.errors.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionListener {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponse> handleServiceExceptions(ServiceException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("500", e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleServiceExceptions(EntityNotFoundException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("404", e.getMessage()));
    }

}
