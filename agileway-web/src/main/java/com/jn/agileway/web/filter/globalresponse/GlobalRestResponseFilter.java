package com.jn.agileway.web.filter.globalresponse;

import com.jn.agileway.web.filter.OncePerRequestFilter;
import com.jn.agileway.web.rest.GlobalRestHandlers;
import com.jn.agileway.web.servlet.Servlets;
import com.jn.langx.http.rest.RestRespBody;
import com.jn.langx.util.io.Charsets;
import com.jn.langx.util.logging.Loggers;
import com.jn.langx.util.reflect.Reflects;
import org.slf4j.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 配置 该 filter的url pattern时，只能配置在那些 restful api上，不然会出现意想不到的彩蛋
 */
public class GlobalRestResponseFilter extends OncePerRequestFilter {
    private static final Logger logger = Loggers.getLogger(GlobalRestResponseFilter.class);

    private GlobalFilterRestExceptionHandler exceptionHandler;
    private GlobalFilterRestResponseHandler restResponseBodyHandler;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        logger.info("Initial the global rest response filter");
    }

    public void setExceptionHandler(GlobalFilterRestExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void setRestResponseBodyHandler(GlobalFilterRestResponseHandler restResponseBodyHandler) {
        this.restResponseBodyHandler = restResponseBodyHandler;
    }

    @Override
    public void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
            RestRespBody restRespBody = null;
            try {
                chain.doFilter(request, response);
            } catch (Exception ex) {
                if (exceptionHandler != null) {
                    restRespBody = exceptionHandler.handle(req, resp, doFilterMethod, ex);
                }
            } finally {
                //rest response body 是否已写过
                Boolean responseBodyWritten = (Boolean) request.getAttribute(GlobalRestHandlers.GLOBAL_REST_RESPONSE_HAD_WRITTEN);
                if ((responseBodyWritten == null || !responseBodyWritten) && !response.isCommitted()) {
                    if (restResponseBodyHandler != null) {
                        restRespBody = restResponseBodyHandler.handle(req, resp, doFilterMethod, restRespBody);
                    }
                    if (restRespBody != null) {
                        Map<String, Object> finalBody = restResponseBodyHandler.toMap(req, resp, doFilterMethod, restRespBody);

                        resp.setStatus(restRespBody.getStatusCode());
                        response.setContentType(GlobalRestHandlers.RESPONSE_CONTENT_TYPE_JSON_UTF8);
                        response.setCharacterEncoding(Charsets.UTF_8.name());
                        request.setAttribute(GlobalRestHandlers.GLOBAL_REST_RESPONSE_HAD_WRITTEN, true);

                        response.resetBuffer();
                        String json = restResponseBodyHandler.getContext().getJsonFactory().get().toJson(finalBody);
                        Servlets.writeToResponse(resp, GlobalRestHandlers.RESPONSE_CONTENT_TYPE_JSON_UTF8, json);
                    }
                }
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public static final Method doFilterMethod = Reflects.getPublicMethod(GlobalRestResponseFilter.class, "doFilter", ServletRequest.class, ServletResponse.class, FilterChain.class);
}
