package com.vybe.api;

import com.vybe.dto.ApiDtos.Message;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.ForbiddenException;
import com.vybe.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "com.vybe.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Message> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Message(e.getMessage()));
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<Message> forbidden(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Message(e.getMessage()));
    }

    @ExceptionHandler({BadRequestException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<Message> badRequest(Exception e) {
        String msg = e instanceof BadRequestException ? e.getMessage() : "Некорректные данные запроса";
        return ResponseEntity.badRequest().body(new Message(msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Message> invalid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(new Message(fe == null ? "Некорректные данные" : fe.getDefaultMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Message> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new Message("Файл слишком большой (максимум 2 ГБ)"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Message> other(Exception e) {
        log.error("API error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Message("Внутренняя ошибка сервера"));
    }
}
