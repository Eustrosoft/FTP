package com.eustrosoft;

import java.sql.ResultSet;

import static com.eustrosoft.Constants.TYPE;

public enum CMSType {
    UNKNOWN("UNKNOWN"),
    DIRECTORY("DIRECTORY"),
    FILE("FILE"),
    LINK("LINK");

    final String value;

    CMSType(String value) {
        this.value = value;
    }

    public static CMSType getType(ResultSet resultSet) {
        CMSType val = CMSType.UNKNOWN;
        try {
            String typeStr = resultSet.getObject(TYPE).toString();
            if (typeStr.equals("R") || typeStr.equals("D")) {
                val = CMSType.DIRECTORY;
            }
            if (typeStr.equals("B")) {
                val = CMSType.FILE;
            }
        } catch (Exception ex) {
            return val;
        }
        return val;
    }
}
