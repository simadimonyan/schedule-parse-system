package app.controller.exceptions;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictExceptions(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("409", e.getMessage()));
    }

    /** Гонка двух одинаковых запросов, которую поймал уникальный индекс */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflictExceptions(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("409", "Запись уже существует"));
    }

}
