package com.jn.agileway.web.servlet;


import com.jn.langx.annotation.NonNull;
import com.jn.langx.annotation.Nullable;
import com.jn.langx.text.StringTemplates;
import com.jn.langx.util.*;
import com.jn.langx.util.collection.Collects;
import com.jn.langx.util.collection.Pipeline;
import com.jn.langx.util.collection.multivalue.LinkedMultiValueMap;
import com.jn.langx.util.collection.multivalue.MultiValueMap;
import com.jn.langx.util.function.Consumer;
import com.jn.langx.util.function.Function;
import com.jn.langx.util.function.Predicate;
import com.jn.langx.util.io.Charsets;
import com.jn.langx.util.io.IOs;
import com.jn.langx.util.io.file.FileIOMode;
import com.jn.langx.util.logging.Loggers;
import com.jn.langx.util.net.http.HttpHeaders;
import com.jn.langx.util.net.http.HttpMethod;
import com.jn.langx.util.net.http.HttpRange;
import com.jn.langx.util.net.mime.MediaType;
import org.slf4j.Logger;

import javax.servlet.FilterConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;

public class Servlets {
    private static final Logger logger = Loggers.getLogger(Servlets.class);

    public static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";
    public static final String X_FRAME_OPTIONS_DENY = "deny";
    public static final String X_FRAME_OPTIONS_SAME_ORIGIN = "sameorigin";
    public static final String ACCESS_DENIED_403 = "AGILEWAY_ECURITY_403_EXCEPTION";


    public static final String getUTF8ContentType(@NonNull String mediaType) {
        return getContentType(mediaType, "UTF-8");
    }

    public static final String getContentType(@NonNull String mediaType, @Nullable String encoding) {
        return mediaType + ";charset=" + encoding;
    }


    /***********************************************************************
     *      Filters
     ***********************************************************************/

    public static Map<String, String> extractFilterInitParameters(final FilterConfig filterConfig) {
        return Pipeline.<String>of(filterConfig.getInitParameterNames()).collect(Collects.toHashMap(new Function<String, String>() {
            @Override
            public String apply(String name) {
                return name;
            }
        }, new Function<String, String>() {
            @Override
            public String apply(String name) {
                return filterConfig.getInitParameter(name);
            }
        }, true));
    }


    /***********************************************************************
     *      Header
     ***********************************************************************/

    /**
     * Content-Length
     *
     * @param response
     * @return
     */
    public static long getContentLength(HttpServletResponse response) {
        return Objs.useValueIfNull(getContentLength(response, true), 0L);
    }

    public static Long getContentLength(HttpServletResponse response, boolean useZeroIfNull) {
        String contentLengthStr = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (useZeroIfNull) {
            contentLengthStr = Strings.useValueIfBlank(contentLengthStr, "0");
        }
        if (Emptys.isEmpty(contentLengthStr)) {
            return null;
        }
        return Long.parseLong(contentLengthStr);
    }

    public static HttpHeaders getRequestHeaders(HttpServletResponse response) {
        return new HttpHeaders(headersToMultiValueMap(response));
    }

    public static MultiValueMap<String, String> headersToMultiValueMap(final HttpServletResponse response) {
        final MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();

        Collects.forEach(response.getHeaderNames(), new Consumer<String>() {
            @Override
            public void accept(String headerName) {
                map.addAll(headerName, Pipeline.<String>of(response.getHeaders(headerName)).asList());
            }
        });
        return map;
    }

    public static HttpHeaders getRequestHeaders(HttpServletRequest request) {
        return new HttpHeaders(headersToMultiValueMap(request));
    }

    public static HttpMethod getMethod(HttpServletRequest request) {
        return HttpMethod.valueOf(request.getMethod());
    }

    public static MultiValueMap<String, String> headersToMultiValueMap(final HttpServletRequest request) {
        final MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();

        Collects.forEach(request.getHeaderNames(), new Consumer<String>() {
            @Override
            public void accept(String headerName) {
                map.addAll(headerName, Pipeline.<String>of(request.getHeaders(headerName)).asList());
            }
        });
        return map;
    }

    private static List<String> clientIpHeadersInProxy = Collects.newArrayList(
            "X-Real-IP", // nginx
            "Proxy-Client-IP", // apache http server
            "WL-Proxy-Client-IP", // WebLogic 服务代理
            "X-Forwarded-For", // squid
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    public static String getClientIP(final HttpServletRequest request) {
        String header = Collects.findFirst(clientIpHeadersInProxy, new Predicate<String>() {
            @Override
            public boolean test(String headerName) {
                String ips = request.getHeader(headerName);
                if (Emptys.isNotEmpty(ips) && !Strings.equalsIgnoreCase("unknown", ips)) {
                    return true;
                }
                return false;
            }
        });
        String ip = Strings.isEmpty(header) ? request.getRemoteAddr() : request.getHeader(header);
        if (Strings.isNotEmpty(ip)) {
            int index = ip.indexOf(",");
            if (index != -1) {
                return ip.substring(0, index);
            }
        }
        return ip;
    }


    /**
     * 下载
     */

    /**
     * 下载文件
     *
     * @param request
     * @param response
     * @param file
     * @param maxPacketSize
     * @param fileName
     * @throws IOException
     */
    public static void downloadFile(
            HttpServletRequest request,
            HttpServletResponse response,
            File file,
            int maxPacketSize,
            String fileName) throws IOException {

        fileName = Strings.useValueIfBlank(fileName, file.getName());
        Preconditions.checkNotEmpty(fileName);
        if (maxPacketSize == 0) {
            maxPacketSize = (int) DataSizes.ONE_MB;
        }
        if (maxPacketSize < 0) {
            maxPacketSize = Integer.MAX_VALUE;
        }
        HttpHeaders httpHeaders = getRequestHeaders(request);
        List<HttpRange> ranges = httpHeaders.getRange();
        long offset = 0;
        long end = file.length();
        if (Emptys.isNotEmpty(ranges)) {
            HttpRange range = ranges.get(0);
            offset = range.getRangeStart(0);
            end = range.getRangeEnd(file.length());
        }
        if (end == -1) {
            end = file.length();
        }
        if (end == file.length()) {
            end = end - 1;
        }
        if (offset >= end || end == -1) {
            response.sendError(400, StringTemplates.formatWithPlaceholder("Invalid http request header: Range:{}-{}", offset, end));
            return;
        }
        FileChannel channel = null;
        try {
            channel = new RandomAccessFile(file, FileIOMode.READ_ONLY.getIdentifier()).getChannel();
            long sizeOfWillRead = end - offset + 1;
            if (sizeOfWillRead > maxPacketSize) {
                sizeOfWillRead = maxPacketSize;
            }
            int size = Numbers.toInt(sizeOfWillRead);
            end = offset + size - 1;
            if (offset + size > file.length()) {
                end = file.length() - 1;
                size = Numbers.toInt(file.length() - offset);
            }
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, size);
            byte[] bytes = new byte[size];
            if (byteBuffer.hasRemaining()) {
                byteBuffer.get(bytes, 0, byteBuffer.remaining());
            }
            response.setCharacterEncoding(Charsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            response.setHeader(HttpHeaders.CONTENT_RANGE, StringTemplates.formatWithPlaceholder("bytes {}-{}/{}", offset, end, file.length()));
            String disposition = StringTemplates.formatWithPlaceholder("attachment;filename={}", new String(fileName.getBytes(Charsets.UTF_8), Charsets.ISO_8859_1));
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
            response.setHeader(HttpHeaders.CONTENT_LENGTH, "" + size);
            response.getOutputStream().write(bytes);
            response.setStatus(200);
        } catch (Throwable ex) {
            logger.error("Error occur when download file: {}", fileName);
            response.setStatus(500);
        } finally {
            IOs.close(channel);
        }
    }

    /**
     * @param request       请求
     * @param response      响应
     * @param bytes         完整的字节，如果是分段下载，这里也是总的，不是分段的，分段内容由方法内部来计算
     * @param maxPacketSize 下载包上限
     * @param fileName      文件名
     * @throws IOException
     */
    public static final void downloadBytes(
            HttpServletRequest request,
            HttpServletResponse response,
            @NonNull byte[] bytes,
            int maxPacketSize,
            @NonNull String fileName,
            @Nullable String contentType
    ) throws IOException {
        Preconditions.checkNotEmpty(fileName);
        if (maxPacketSize == 0) {
            maxPacketSize = (int) DataSizes.ONE_MB;
        }
        if (maxPacketSize < 0) {
            maxPacketSize = Integer.MAX_VALUE;
        }
        HttpHeaders httpHeaders = getRequestHeaders(request);
        List<HttpRange> ranges = httpHeaders.getRange();
        long offset = 0;
        long end = bytes.length - 1;
        if (Emptys.isNotEmpty(ranges)) {
            HttpRange range = ranges.get(0);
            offset = range.getRangeStart(0);
            end = range.getRangeEnd(bytes.length);
        }
        if (end == -1) {
            end = bytes.length;
        }
        if (end == bytes.length) {
            end = end - 1;
        }
        if (offset >= end || end == -1) {
            response.sendError(400, StringTemplates.formatWithPlaceholder("Invalid http request header: Range:{}-{}", offset, end));
            return;
        }
        try {
            long sizeOfWillRead = end - offset + 1;
            if (sizeOfWillRead > maxPacketSize) {
                sizeOfWillRead = maxPacketSize;
            }
            int size = Numbers.toInt(sizeOfWillRead);
            end = offset + size - 1;
            if (offset + size > bytes.length) {
                end = bytes.length - 1;
                size = Numbers.toInt(bytes.length - offset);
            }

            response.setCharacterEncoding(Charsets.UTF_8.name());
            contentType = Strings.useValueIfBlank(contentType, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setContentType(contentType);
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            response.setHeader(HttpHeaders.CONTENT_RANGE, StringTemplates.formatWithPlaceholder("bytes {}-{}/{}", offset, end, bytes.length));
            String disposition = StringTemplates.formatWithPlaceholder("attachment;filename={}", new String(fileName.getBytes(Charsets.UTF_8), Charsets.ISO_8859_1));
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
            response.setHeader(HttpHeaders.CONTENT_LENGTH, "" + size);
            response.getOutputStream().write(bytes, (int) offset, size);
            response.setStatus(200);
        } catch (Throwable ex) {
            logger.error("Error occur when download file: {}", fileName);
            response.setStatus(500);
        }
    }

    public static void writeToResponse(@NonNull HttpServletResponse response, @Nullable String contentType, @NonNull String content) throws IOException {
        writeToResponseUsingWriter(response, contentType, content);
    }

    public static void writeToResponseUsingOutputStream(@NonNull HttpServletResponse response, @Nullable String contentType, @NonNull String content) throws IOException {
        response.setCharacterEncoding(Charsets.UTF_8.name());
        ServletOutputStream writer = response.getOutputStream();
        if (Emptys.isNotEmpty(contentType)) {
            response.setContentType(contentType);
        }
        writer.write(content.getBytes(Charsets.UTF_8));
    }

    public static void writeToResponseUsingWriter(@NonNull HttpServletResponse response, @Nullable String contentType, @NonNull String content) throws IOException {
        response.setCharacterEncoding(Charsets.UTF_8.name());
        PrintWriter writer = response.getWriter();
        if (Emptys.isNotEmpty(contentType)) {
            response.setContentType(contentType);
        }
        writer.write(content);
    }

    public static Cookie getCookie(HttpServletRequest request, final String name) {
        return Pipeline.of(request.getCookies())
                .findFirst(new Predicate<Cookie>() {
                    @Override
                    public boolean test(Cookie cookie) {
                        return name.equals(cookie.getName());
                    }
                });
    }

    public static String buildFullRequestUrl(HttpServletRequest r) {
        return buildFullRequestUrl(r.getScheme(), r.getServerName(), r.getServerPort(), r.getRequestURI(),
                r.getQueryString());
    }

    /**
     * Obtains the full URL the client used to make the request.
     * <p>
     * Note that the server port will not be shown if it is the default server port for
     * HTTP or HTTPS (80 and 443 respectively).
     *
     * @return the full URL, suitable for redirects (not decoded).
     */
    public static String buildFullRequestUrl(String scheme, String serverName, int serverPort, String requestURI,
                                             String queryString) {
        scheme = scheme.toLowerCase();
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        // Only add port if not default
        if ("http".equals(scheme)) {
            if (serverPort != 80) {
                url.append(":").append(serverPort);
            }
        } else if ("https".equals(scheme)) {
            if (serverPort != 443) {
                url.append(":").append(serverPort);
            }
        }
        // Use the requestURI as it is encoded (RFC 3986) and hence suitable for
        // redirects.
        url.append(requestURI);
        if (queryString != null) {
            url.append("?").append(queryString);
        }
        return url.toString();
    }

    public static String getParameter(HttpServletRequest request, String name) {
        return getParameter(request, name, null);
    }

    public static String getParameter(HttpServletRequest request, String name, String defaultValue) {
        return Objs.useValueIfNull(request.getParameter(name), defaultValue);
    }

    public static List<String> getParameters(HttpServletRequest request, String name) {
        return getParameters(request, name, null);
    }

    public static List<String> getParameters(HttpServletRequest request, String name, List<String> defaultValue) {
        final String[] values = request.getParameterValues(name);
        if (values != null) {
            return Collects.asList(values);
        }
        return defaultValue;
    }

    public static Integer getIntParameter(HttpServletRequest request, String name, Integer defaultValue) {
        String value = getParameter(request, name);
        Integer ret = null;
        if (!Strings.isBlank(value)) {
            ret = Integer.parseInt(value);
        } else {
            ret = defaultValue;
        }
        return ret;
    }


    public static List<Integer> getIntParameters(HttpServletRequest request, final String name, List<Integer> defaultValue) {
        final List<String> valueOpt = getParameters(request, name);
        List<Integer> ret = null;
        if (valueOpt != null) {
            ret = Pipeline.of(valueOpt).map(new Function<String, Integer>() {
                @Override
                public Integer apply(String input) {
                    return Integer.parseInt(input);
                }
            }).asList();
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    public static Long getLongParameter(HttpServletRequest request, final String name, Long defaultValue) {
        final String valueOpt = getParameter(request, name);
        Long ret = null;
        if (!Strings.isBlank(valueOpt)) {
            ret = Long.parseLong(valueOpt);
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    public static List<Long> getLongParameters(HttpServletRequest request, final String name, List<Long> defaultValue) {
        final List<String> valueOpt = getParameters(request, name);
        List<Long> ret = null;
        if (valueOpt != null) {
            ret = Pipeline.of(valueOpt).map(new Function<String, Long>() {
                @Override
                public Long apply(String input) {
                    return Long.parseLong(input);
                }
            }).asList();
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    public static Float getFloatParameter(HttpServletRequest request, final String name, Float defaultValue) {
        final String valueOpt = getParameter(request, name);
        Float ret = null;
        if (!Strings.isBlank(valueOpt)) {
            ret = Float.parseFloat(valueOpt);
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    public static List<Float> getFloatParameters(HttpServletRequest request, final String name, List<Float> defaultValue) {
        final List<String> valueOpt = getParameters(request, name);
        List<Float> ret = null;
        if (valueOpt != null) {
            ret = Pipeline.of(valueOpt).map(new Function<String, Float>() {
                @Override
                public Float apply(String input) {
                    return Float.parseFloat(input);
                }
            }).asList();
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    public static Double getDoubleParameter(HttpServletRequest request, final String name, Double defaultValue) {
        final String valueOpt = getParameter(request, name);
        Double ret = null;
        if (!Strings.isBlank(valueOpt)) {
            ret = Double.parseDouble(valueOpt);
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    public static List<Double> getDoubleParameters(HttpServletRequest request, final String name, List<Double> defaultValue) {
        final List<String> valueOpt = getParameters(request, name);
        List<Double> ret = null;
        if (valueOpt != null) {
            ret = Pipeline.of(valueOpt).map(new Function<String, Double>() {
                @Override
                public Double apply(String input) {
                    return Double.parseDouble(input);
                }
            }).asList();
        } else {
            ret = defaultValue;
        }
        return ret;
    }

    private static final List<String> TRUE_VALUES;

    static {
        TRUE_VALUES = Collects.newArrayList("true", "1", "on", "yes");
    }


    public static Boolean isTrue(final String value) {
        return Pipeline.of(TRUE_VALUES).anyMatch(new Predicate<String>() {
            @Override
            public boolean test(String x) {
                return x.equalsIgnoreCase(value);
            }
        });
    }

    public static Boolean getBooleanParameter(HttpServletRequest request, final String name) {
        return getBooleanParameter(request, name, null);
    }

    public static Boolean getBooleanParameter(HttpServletRequest request, final String name, final Boolean defaultValue) {
        final String valueOpt = getParameter(request, name);
        if (!Strings.isBlank(valueOpt)) {
            return isTrue(name);
        }
        return defaultValue;
    }

    public static List<Boolean> getBooleanParameters(HttpServletRequest request, final String name, List<Boolean> defaultValue) {
        final List<String> valueOpt = getParameters(request, name);
        List<Boolean> ret = null;
        if (valueOpt != null) {
            ret = Pipeline.of(valueOpt).map(new Function<String, Boolean>() {
                @Override
                public Boolean apply(String input) {
                    return isTrue(input);
                }
            }).asList();
        }else {
            ret = defaultValue;
        }
        return ret;
    }
}
