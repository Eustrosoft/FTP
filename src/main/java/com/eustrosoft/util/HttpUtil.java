package com.eustrosoft.util;

import com.eustrosoft.FileDetails;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;

import static com.eustrosoft.FtpDao.SEPARATOR;
import static com.eustrosoft.FtpDao.getPathParts;
import static com.eustrosoft.util.StringUtils.getStrValueOrEmpty;

public final class HttpUtil {

    public static final String[] AVAILABLE_CONTENT_TYPES = new String[]{
            "application/octet-stream", "application/json",
            "application/pdf", "application/xml", "plain/text",
            "image/gif", "image/jpeg", "image/jpg", "image/png",
            "image/svg", "video/mp4", "video/mpeg"
    };

    public static void printBackLine(HttpServletRequest request, PrintWriter writer, String path) {
        String[] pathParts = getPathParts(path);
        if (pathParts.length >= 1) {
            StringBuilder backPath = new StringBuilder();
            backPath.append(SEPARATOR);
            for (int i = 0; i < pathParts.length - 1; i++) {
                backPath.append(pathParts[i]);
                backPath.append(SEPARATOR);
            }
            writer.println("<td>");
            writer.println(String.format(
                        "<a href=\"%s\"> %s </a>",
                        getFormedFilePath(request, backPath.toString(), ""),
                        ".."
                    )
            );
            writer.println("</td>");
        }
    }

    public static void printLinkForPath(HttpServletRequest request, PrintWriter writer,
                                        String basePath, FileDetails fileDetails) {
        String path = getFormedFilePath(request, basePath, fileDetails.getFileName());
        writer.println("<tr>");
        writer.println("<td style=\"width: 60%\">");
        writer.println(String.format("<a href=\"%s\"> %s </a>", path, fileDetails.getFileName()));
        writer.println("</td>");
        writer.println("<td style=\"width: 20%\">");
        writer.println(getStrValueOrEmpty(fileDetails.getDescription()));
        writer.println("</td>");
        writer.println("<td style=\"width: 10%\">");
        writer.println(getStrValueOrEmpty(fileDetails.getSecurityLevel()));
        writer.println("</td>");
        writer.println("<td style=\"width: 10%\">");
        writer.println(getStrValueOrEmpty(fileDetails.getFileLength()));
        writer.println("</td>");
        writer.println("</tr>");
    }

    private static String getFormedFilePath(HttpServletRequest request, String basePath, String fileName) {
        String bp = basePath.endsWith(SEPARATOR) ? basePath : String.format("%s%s", basePath, SEPARATOR);
        return String.format("%s%s%s", request.getContextPath(), bp, fileName);
    }

    public static void setHeadersForFileDownload(HttpServletResponse response, FileDetails fileDetails) {
        response.reset();
        setFileContentType(response, fileDetails.getMimeType());
        response.setCharacterEncoding(fileDetails.getEncoding());
        response.setHeader(
                "Content-Disposition",
                String.format(
                        "attachment; filename=\"%s\"",
                        fileDetails.getFileName()
                )
        );
        response.setContentLengthLong(fileDetails.getFileLength());
        response.setBufferSize(1024);
        response.setHeader("Accept-Ranges", "bytes");
    }

    public static String getServletPath(HttpServletRequest request) {
        String servletPath = request.getPathInfo();
        return servletPath == null || servletPath.isEmpty() ? SEPARATOR : request.getPathInfo();
    }

    public static void checkPathInjection(String... params) throws Exception {
        for (String param : params) {
            if (param == null || param.isEmpty()) {
                throw new Exception("Param was null or empty.");
            }
            if (param.contains("..")) {
                throw new Exception("Path Injection Detected.");
            }
        }
    }

    public static void setFileContentType(HttpServletResponse httpResponse, String mimeType) {
        if (mimeType != null && !mimeType.isEmpty()) {
            if (isAvailableContentType(mimeType)) {
                httpResponse.setContentType(mimeType);
                return;
            }
        }
        httpResponse.setContentType("application/octet-stream");
    }

    protected static boolean isAvailableContentType(String contentType) {
        return Arrays.asList(AVAILABLE_CONTENT_TYPES).contains(contentType);
    }

    private HttpUtil() { }
}
