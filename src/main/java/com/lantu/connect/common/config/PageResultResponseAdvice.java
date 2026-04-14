package com.lantu.connect.common.config;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 分页响应处理
 * 为分页接口添加 X-Total-Count 响应头
 *
 * @author 王帝
 * @date 2026-03-23
 */
@ControllerAdvice
public class PageResultResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body instanceof R<?> r && r.getData() instanceof PageResult<?> pageResult) {
            if (response instanceof ServletServerHttpResponse servletResponse) {
                HttpServletResponse httpResponse = servletResponse.getServletResponse();
                httpResponse.setHeader(TOTAL_COUNT_HEADER, String.valueOf(pageResult.getTotal()));
            }
        }
        return body;
    }
}
