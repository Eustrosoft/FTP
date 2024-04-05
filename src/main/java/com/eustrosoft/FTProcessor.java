package com.eustrosoft;

import com.eustrosoft.util.StringUtils;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eustrosoft.qdbp.QDBPSession;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

import static com.eustrosoft.Constants.VIRTUAL_PATH;
import static com.eustrosoft.FtpDao.SEPARATOR;
import static com.eustrosoft.util.HttpUtil.checkPathInjection;
import static com.eustrosoft.util.HttpUtil.getServletPath;
import static com.eustrosoft.util.HttpUtil.printBackLine;
import static com.eustrosoft.util.HttpUtil.printLinkForPath;
import static com.eustrosoft.util.HttpUtil.setHeadersForFileDownload;

public class FTProcessor {
    private HttpServletRequest request;
    private HttpServletResponse response;
    private QDBPSession session;

    public void setQDBPSession(QDBPSession s) {
        session = s;
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.request = req;
        this.response = resp;
        request.setCharacterEncoding("UTF-8");

        // Login
        if (session == null) {
            response.getWriter().println("No session, call administrator");
            return;
        }

        // Processing path
        try {
            synchronized (session) {
                String contextPath = getServletPath(req);
                printContentByPath(session, contextPath);
            }
        } catch (Exception e) {
            response.getWriter().println("Exception while processing path, be sure this is correct path or call administrator");
        }
    }

    private void printContentByPath(QDBPSession session, String path)
            throws Exception {
        checkPathInjection(path);

        Connection connection = session.getSQLConnection();
        if (connection == null) {
            throw new SQLException("No connection, call the administrator");
        }
        if (path == null || path.isEmpty() || path.equalsIgnoreCase(SEPARATOR)) {
            printFiles(new PrinterData(response.getWriter(), connection, path), VIRTUAL_PATH_PROCESSOR);
        } else {
            if (path.equalsIgnoreCase(VIRTUAL_PATH) || path.equalsIgnoreCase(VIRTUAL_PATH + SEPARATOR)) {
                path = SEPARATOR;
            }
            if (path.startsWith(VIRTUAL_PATH)) {
                path = path.substring(VIRTUAL_PATH.length());
            }
            String fullPath = FtpDao.getFullPath(connection, path);
            if (FtpDao.isFile(connection, fullPath)) {
                downloadFile(response, fullPath, connection);
            } else {
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Charset", "UFT-8");
                printFiles(
                        new PrinterData(response.getWriter(), connection, VIRTUAL_PATH + path, fullPath),
                        REAL_PATH_PROCESSING
                );
            }
        }
    }

    private void printFiles(PrinterData data, Consumer<PrinterData> consumer) throws Exception {
        PrintWriter writer = response.getWriter();
        writer.println("<html>");
        long millis = System.currentTimeMillis();
        writer.println("<meta charset=\"UTF-8\">");
        writer.println("<h1>Index of " + data.getNamesPath() + "</h1>");
        writer.println("<hr>");

        // Write data in table
        consumer.accept(data);

        writer.println("<hr>");
        long timing = System.currentTimeMillis() - millis;
        writer.println("<p> Timing: " + timing + " ms. </p>");
        writer.println("</html>");
    }

    private void downloadFile(HttpServletResponse response, String path, Connection connection) throws IOException {
        OutputStream outputStream = response.getOutputStream();
        try {
            setHeadersForFileDownload(response, new FtpDao().getFileDetails(connection, path));
            new FtpDao().printToOutput(connection, outputStream, path);
        } catch (Exception ex) {
            String unexpectedError = "Unexpected Error Occurred. Call administrator.";
            outputStream.write(unexpectedError.getBytes());
        } finally {
            outputStream.flush();
            outputStream.close();
        }
    }

    private final Consumer<PrinterData> VIRTUAL_PATH_PROCESSOR = (data) -> {
        PrintWriter writer = data.getWriter();
        writer.println("<table style=\"border: none; width: 80vw\">");
        printBackLine(request, writer, data.getNamesPath());
        printLinkForPath(
                request, writer, data.getNamesPath(),
                new FileDetails(
                        null, "s", "", null, CMSType.DIRECTORY, null, null
                )
        );
        writer.println("</table>");
    };

    private final Consumer<PrinterData> REAL_PATH_PROCESSING = (data) -> {
        try {
            PreparedStatement preparedStatement = FtpDao.getViewStatementForPath(data.getConnection(), data.getIdsPath());
            PrintWriter writer = data.getWriter();
            if (preparedStatement != null) {
                ResultSet resultSet = preparedStatement.executeQuery();

                writer.println("<table style=\"border: none; width: 80vw\">");
                printBackLine(request, writer, data.getNamesPath());
                while (resultSet.next()) {
                    try {
                        String name = StringUtils.getStrValueOrEmpty(resultSet, Constants.NAME);
                        String fname = StringUtils.getStrValueOrEmpty(resultSet, Constants.F_NAME);
                        String finalName = fname.isEmpty() ? name : fname;
                        CMSType type = CMSType.getType(resultSet);
                        String descr = StringUtils.getStrValueOrEmpty(resultSet, Constants.DESCRIPTION);
                        String zlvl = StringUtils.getStrValueOrEmpty(resultSet, Constants.ZLVL);
                        String fileId = StringUtils.getStrValueOrEmpty(resultSet, Constants.F_ID);
                        Long fileLength = 0L;
                        if (fileId != null && !fileId.isEmpty()) {
                            fileLength = FtpDao.getFileLength(data.getConnection(), Long.parseLong(fileId));
                        }
                        if (type.equals(CMSType.DIRECTORY)) {
                            finalName = finalName + SEPARATOR;
                        }
                        printLinkForPath(
                                request, writer, data.getNamesPath(),
                                new FileDetails(
                                        null, finalName, descr, zlvl, type, fileLength, null
                                )
                        );
                    } catch (Exception ignored) {

                    }
                }
                writer.println("</table>");
                preparedStatement.close();
                resultSet.close();
            }
        } catch (Exception ignored) {

        }
    };

    static class PrinterData {
        private final PrintWriter writer;
        private final Connection connection;
        private String namesPath;
        private String idsPath;

        public PrinterData(PrintWriter writer, Connection connection, String namesPath) {
            this.writer = writer;
            this.connection = connection;
            this.namesPath = namesPath;
        }

        public PrinterData(PrintWriter writer, Connection connection, String namesPath, String idsPath) {
            this.writer = writer;
            this.connection = connection;
            this.namesPath = namesPath;
            this.idsPath = idsPath;
        }

        public PrintWriter getWriter() {
            return writer;
        }

        public Connection getConnection() {
            return connection;
        }

        public String getNamesPath() {
            return namesPath;
        }

        public void setNamesPath(String namesPath) {
            this.namesPath = namesPath;
        }

        public String getIdsPath() {
            return idsPath;
        }

        public void setIdsPath(String idsPath) {
            this.idsPath = idsPath;
        }
    }
}
