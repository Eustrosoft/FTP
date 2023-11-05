/**
 * Copyright (c) 2023, Yadzuka & EustroSoft.org
 * This file is part of RequestHandler project.
 * See the LICENSE file at the project root for licensing information.
 */

package com.eustrosoft.util;

import javax.servlet.http.HttpServletRequest;

public final class WebParams {
    // DB
    public final static String DB_POOL_URL = "QDBPOOL_URL";
    public final static String DB_POOL_NAME = "QDBPOOL_NAME";
    public final static String DB_JDBC_CLASS = "QDBPOOL_JDBC_CLASS";
    // DB User
    public final static String DB_USER = "DBUser";
    public final static String DB_PASSWORD = "DBPassword";

    public static String getString(HttpServletRequest request, String param) {
        return request.getServletContext().getInitParameter(param);
    }

    private WebParams() {

    }
}
