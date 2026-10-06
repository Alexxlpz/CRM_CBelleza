package com.alexxlpz.crm_cbelleza.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Convierte los recursos inexistentes en una página 404 con el diseño de la aplicación. */
@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error", "No encontrado");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }
}
