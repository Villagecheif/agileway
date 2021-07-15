package com.jn.agileway.feign.codec;

import com.jn.langx.text.StringTemplates;
import com.jn.langx.util.io.IOs;
import com.jn.langx.util.struct.Holder;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FeignRestRespBodyException extends RuntimeException {
    private static final Logger logger = LoggerFactory.getLogger(FeignRestRespBodyException.class);
    private String methodKey;
    private Response response;
    private Holder<String> responseBody;

    public FeignRestRespBodyException(String methodKey, Response response) {
        this.methodKey = methodKey;
        this.response = response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public String getResponseBody() {
        if (responseBody == null) {
            responseBody = new Holder<String>();
            try {
                String str = IOs.readAsString(response.body().asReader());
                responseBody.set(str);
                return str;
            } catch (Throwable ex) {
                logger.error(ex.getMessage(), ex);
                return null;
            }
        } else {
            return responseBody.get();
        }
    }


    public int getStatusCode() {
        return response.status();
    }


    public String getMethodKey() {
        return methodKey;
    }

    public Response originalResponse() {
        return this.response;
    }

    @Override
    public String getMessage() {
        return StringTemplates.formatWithPlaceholder("status {} reading {}; content: {}", getStatusCode(), methodKey, responseBody.get());
    }
}
