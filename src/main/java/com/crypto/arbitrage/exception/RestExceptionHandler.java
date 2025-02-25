package com.crypto.arbitrage.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {
//  @ExceptionHandler(IllegalArgumentException.class)
//  protected ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
//    return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
//  }
//
//  @ExceptionHandler(NotAuthorizedException.class)
//  protected ResponseEntity<Object> handleNotAuthorizedException(NotAuthorizedException e) {
//    return buildErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
//  }
//
//  @ExceptionHandler(IllegalStateException.class)
//  protected ResponseEntity<Object> handleIllegalStateException(IllegalStateException e) {
//    return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
//  }
//
//  @ExceptionHandler(NotFoundException.class)
//  protected ResponseEntity<Object> handleNotFoundException(NotFoundException e) {
//    return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
//  }
//
//  @ExceptionHandler(InvalidDataAccessApiUsageException.class)
//  protected ResponseEntity<Object> handleInvalidDataAccessApiUsageException(
//      InvalidDataAccessApiUsageException e) {
//    return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
//  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
    Set<String> errorsSet = new HashSet<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String errorMessage = error.getDefaultMessage();
              errorsSet.add(errorMessage);
            });
    return buildErrorArrayResponse(errorsSet.toArray(new String[0]));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Object> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException ex) {
    log.warn("MissingServletRequestParameterException: {}", ex.getMessage());
    return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  private static ResponseEntity<Object> buildErrorResponse(HttpStatus httpStatus, String message) {
    ErrorResponse response =
        new ErrorResponse(httpStatus.value(), httpStatus.getReasonPhrase(), message);
    return ResponseEntity.status(httpStatus.value()).body(response);
  }

  private static ResponseEntity<Object> buildErrorArrayResponse(String[] message) {
    ErrorArrayResponse response =
        new ErrorArrayResponse(
            HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), message);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(response);
  }

  @Getter
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class ErrorResponse {
    private int status;
    private String error;
    private String message;
  }

  @Getter
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class ErrorArrayResponse {
    private int status;
    private String error;
    private String[] message;
  }
}
