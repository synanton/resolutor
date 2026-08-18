package org.synanton.resolutor.adapter.web;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Translates common exceptions into RFC 7807 {@code application/problem+json} responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Map bean-validation failures to 400 with per-field error details. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    body.setTitle("Validation failed");
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    fe.getField()
                        + ": "
                        + (fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
            .collect(Collectors.toList());
    body.setDetail("Request payload failed validation");
    body.setProperty("errors", errors);
    return response(body);
  }

  /** Map unparseable path variables (for example a non-UUID id) to 400. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    body.setTitle("Malformed path variable");
    body.setDetail(ex.getName() + " could not be parsed as " + ex.getRequiredType());
    return response(body);
  }

  /** Map unreadable JSON bodies to 400. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    body.setTitle("Malformed request body");
    body.setDetail(rootCauseMessage(ex));
    return response(body);
  }

  /** Map domain illegal arguments to 404 when the message indicates a missing task, else 400. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    HttpStatus status =
        message.toLowerCase(java.util.Locale.ROOT).contains("not found")
            ? HttpStatus.NOT_FOUND
            : HttpStatus.BAD_REQUEST;
    ProblemDetail body = ProblemDetail.forStatus(status);
    body.setTitle(status == HttpStatus.NOT_FOUND ? "Resource not found" : "Bad request");
    body.setDetail(message);
    return response(body);
  }

  /** Map unmatched request paths to 404 instead of a generic 500. */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    body.setTitle("Resource not found");
    body.setDetail(ex.getResourcePath());
    return response(body);
  }

  /** Map an unexpected failure to a generic 500 problem document. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    body.setTitle("Internal server error");
    body.setDetail(ex.getClass().getSimpleName());
    return response(body);
  }

  private static ResponseEntity<ProblemDetail> response(ProblemDetail body) {
    return ResponseEntity.status(body.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body);
  }

  private static String rootCauseMessage(Throwable ex) {
    Throwable cause = ex;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    if (message != null) {
      return message;
    }
    String outer = ex.getMessage();
    return outer == null ? ex.getClass().getSimpleName() : outer;
  }
}
