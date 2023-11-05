package com.eustrosoft;

import com.eustrosoft.util.WebParams;
import org.eustrosoft.qdbp.QDBPSession;
import org.eustrosoft.qdbp.QDBPool;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.eustrosoft.Constants.*;
import static com.eustrosoft.FtpDao.*;
import static com.eustrosoft.util.StringUtils.getStrValueOrEmpty;
import static com.eustrosoft.util.HttpUtil.*;

public class FTPServlet extends HttpServlet {
    private HttpServletRequest request;
    private HttpServletResponse response;

    private QDBPSession session;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.request = req;
        this.response = resp;
        response.setCharacterEncoding("UTF-8");
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Charset", "UFT-8");

        // Login
        try {
            String login = WebParams.getString(request, WebParams.DB_USER);
            String password = WebParams.getString(request, WebParams.DB_PASSWORD);
            login(login, password);
        } catch (SQLException exception) {
            response.getWriter().println("Exception while logging, call administrator");
            exception.printStackTrace();
            return;
        }

        // Processing path
        try {
            synchronized (session) {
                String contextPath = getServletPath(req);
                printContentByPath(contextPath);
            }
        } catch (Exception e) {
            response.getWriter().println("Exception while processing path, be sure this is correct path or call administrator");
            e.printStackTrace();
        }
    }

    private void printContentByPath(String path)
            throws Exception {
        checkPathInjection(path);

        Connection connection = session.getSQLConnection();
        if (connection == null) {
            throw new SQLException("No connection, call the administrator");
        }

        String fullPath = FtpDao.getFullPath(connection, path);
        if (isFile(connection, fullPath)) {
            downloadFile(response, fullPath, connection);
        } else {
            printFiles(path, connection, fullPath);
        }
    }

    private void printFiles(String path, Connection connection, String fullPath) throws Exception {
        PrintWriter writer = response.getWriter();
        writer.println("<html>");
        Long millis = System.currentTimeMillis();
        writer.println("<meta charset=\"UTF-8\">");
        writer.println("<h1>Index of " + path + "</h1>");
        writer.println("<hr>");
        PreparedStatement preparedStatement = getViewStatementForPath(connection, fullPath);
        if (preparedStatement != null) {
            ResultSet resultSet = preparedStatement.executeQuery();

            writer.println("<table style=\"border: none; width: 80vw\">");
            printBackLine(request, writer, path);
            while (resultSet.next()) {
                try {
                    String name = getStrValueOrEmpty(resultSet, NAME);
                    String fname = getStrValueOrEmpty(resultSet, F_NAME);
                    String finalName = fname.isEmpty() ? name : fname;
                    CMSType type = CMSType.getType(resultSet);
                    String descr = getStrValueOrEmpty(resultSet, DESCRIPTION);
                    String zlvl = getStrValueOrEmpty(resultSet, ZLVL);
                    String fileId = getStrValueOrEmpty(resultSet, "f_id");
                    Long fileLength = 0L;
                    if (fileId != null && !fileId.isEmpty()) {
                        fileLength = getFileLength(connection, Long.parseLong(fileId));
                    }
                    if (type.equals(CMSType.DIRECTORY)) {
                        finalName = finalName + SEPARATOR;
                    }
                    printLinkForPath(
                            request, writer, path,
                            new FileDetails(
                                    null, finalName, descr, zlvl, type, fileLength, null
                            )
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            writer.println("</table>");
            preparedStatement.close();
            resultSet.close();
        }
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

    private void login(String login, String password) throws SQLException {
        QDBPool pool = QDBPool.get(WebParams.getString(request, WebParams.DB_POOL_NAME));
        if (pool == null) {
            pool = new QDBPool(
                    WebParams.getString(request, WebParams.DB_POOL_NAME),
                    WebParams.getString(request, WebParams.DB_POOL_URL),
                    WebParams.getString(request, WebParams.DB_JDBC_CLASS)
            );
            QDBPool.add(pool);
        }
        QDBPSession dbps = new QDBPSession(WebParams.getString(request, WebParams.DB_POOL_NAME), null);
        if (dbps != null) {
            dbps.logout();
        }
        dbps = pool.logon(login, password);
        if (dbps == null) {
            dbps = pool.createSession();
        }
        session = dbps;
    }
}
