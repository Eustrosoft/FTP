package com.eustrosoft;

import com.eustrosoft.util.WebParams;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eustrosoft.qdbp.QDBPSession;
import org.eustrosoft.qdbp.QDBPool;

import java.io.IOException;
import java.sql.SQLException;

public class FTPServlet extends HttpServlet {

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        QDBPSession session = null;
        try {
            session = logon(req, resp);
        } catch (SQLException exception) {
            resp.getWriter().println("Exception while logon, call administrator");
            return;
        }
        // process request
        FTProcessor ftp = new FTProcessor();
        ftp.setQDBPSession(session);
        resp.setHeader("X-FTPServlet-version", "0.1.6");
        ftp.doGet(req, resp);
        ftp.setQDBPSession(null);
    }

    private QDBPSession logon(HttpServletRequest request, HttpServletResponse response) throws SQLException {
        QDBPool pool = QDBPool.get(WebParams.getString(request, WebParams.DB_POOL_NAME));
        if (pool == null) {
            pool = new QDBPool(
                    WebParams.getString(request, WebParams.DB_POOL_NAME),
                    WebParams.getString(request, WebParams.DB_POOL_URL),
                    WebParams.getString(request, WebParams.DB_JDBC_CLASS)
            );
            QDBPool.add(pool);
        }
        return pool.logon();
    }
}
