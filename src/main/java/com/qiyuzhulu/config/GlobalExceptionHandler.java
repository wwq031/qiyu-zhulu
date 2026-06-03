package com.qiyuzhulu.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理 — 所有未捕获异常统一转为结构化JSON报错。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public Map<String, Object> handleAll(Exception ex, HttpServletRequest request) {
        String errorId = "E" + Instant.now().toEpochMilli() % 100000;
        log.error("[{}] {} {} — {}", errorId, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "未知服务器错误");
        body.put("error_id", errorId);
        body.put("type", ex.getClass().getSimpleName());
        body.put("path", request.getRequestURI());
        return body;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "缺少必要参数: " + ex.getParameterName());
        body.put("type", "MissingParameter");
        body.put("path", request.getRequestURI());
        return body;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleBadArg(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("参数错误 — {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "BadArgument");
        body.put("path", request.getRequestURI());
        return body;
    }
}
