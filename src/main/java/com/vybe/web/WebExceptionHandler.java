package com.vybe.web;

import com.vybe.exception.BadRequestException;
import com.vybe.exception.ForbiddenException;
import com.vybe.exception.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages = "com.vybe.web")
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView notFound(Exception e, HttpServletResponse response) {
        return page(404, "Страница не найдена", e instanceof NotFoundException ? e.getMessage()
                : "Такой страницы не существует.", response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ModelAndView forbidden(ForbiddenException e, HttpServletResponse response) {
        return page(403, "Доступ запрещён", e.getMessage(), response);
    }

    @ExceptionHandler(BadRequestException.class)
    public ModelAndView badRequest(BadRequestException e, HttpServletResponse response) {
        return page(400, "Некорректный запрос", e.getMessage(), response);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView other(Exception e, HttpServletResponse response) throws Exception {
        if (e instanceof AccessDeniedException) throw e;
        log.error("Unhandled error", e);
        return page(500, "Что-то пошло не так", "Мы уже разбираемся. Попробуйте обновить страницу.", response);
    }

    private ModelAndView page(int status, String title, String message, HttpServletResponse response) {
        response.setStatus(status);
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", status);
        mv.addObject("error", title);
        mv.addObject("message", message);
        return mv;
    }
}
