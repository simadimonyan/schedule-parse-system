package app.controller.exceptions;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import app.repository.models.dto.api.errors.ErrorResponse;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

    /** Не прошла проверка запроса: не тот тип, нет обязательного поля, конец раньше начала. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("400", e.getMessage()));
    }

    /**
     * Операция противоречит состоянию данных — например, удаление активной версии.
     *
     * <p>409, а не 400: с запросом всё в порядке, он просто неуместен сейчас. Клиенту это
     * говорит, что стоит поменять состояние и повторить, а не переписывать запрос.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("409", e.getMessage()));
    }

    /**
     * Не хватило права на операцию — отказ {@code @PreAuthorize}.
     *
     * <p>Без этого обработчика Spring отдаёт свою страницу ошибки, а клиент ждёт от сервиса
     * единый {@code ErrorResponse}: раньше сюда приходил отказ административного токена,
     * который контроллеры бросали руками.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AuthorizationDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("403", e.getMessage()));
    }

}
