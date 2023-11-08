package com.eustrosoft.util;

import java.sql.ResultSet;

public final class StringUtils {

    public static String getStrValueOrEmpty(ResultSet resultSet, String colName) {
        String val = "";
        try {
            val = resultSet.getObject(colName).toString();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return val;
    }

    public static String getStrValueOrEmpty(String str) {
        return str == null ? "" : str;
    }

    public static String getStrValueOrEmpty(Long number) {
        return number == null ? "" : String.valueOf(number);
    }

    private StringUtils() {

    }
}
