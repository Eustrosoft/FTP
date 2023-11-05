package com.eustrosoft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.eustrosoft.Constants.ID;
import static com.eustrosoft.Constants.ZOID;
import static com.eustrosoft.util.StringUtils.getStrValueOrEmpty;

public class FtpDao {
    public final static int LVL_SCOPE = 0;
    public final static int LVL_ROOT = 1;
    public final static int LVL_OTHER = 2;

    public final static int FILE_DB_BUFFER = 10;

    public final static String SEPARATOR = "/";

    public final static String SCOPES = "SAM.V_Scope";
    public final static String ROOTS = "FS.V_FFile";

    public static PreparedStatement getViewStatementForPath(Connection connection, String path)
            throws Exception {
        int pathLvl = getPathLvl(path);
        if (pathLvl == LVL_SCOPE) {
            return connection.prepareStatement("SELECT * FROM " + SCOPES);
        }
        if (pathLvl == LVL_ROOT) {
            String rootId = path.replaceAll(SEPARATOR, "");
            PreparedStatement statement = connection
                    .prepareStatement("SELECT * FROM " + ROOTS + " where (ZSID = ? AND type = 'R')");
            statement.setLong(1, Long.parseLong(rootId));
            return statement;
        } else if (pathLvl == LVL_OTHER) {
            if (path.lastIndexOf(SEPARATOR) == path.length() - 1) {
                path = path.substring(0, path.length() - 1);
            }
            String lastId = path.substring(path.lastIndexOf(SEPARATOR) + 1);
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT FD.ZOID, FF.ZSID, FF.ZLVL, FD.f_id, " +
                            "COALESCE(FD.mimetype, FF.mimetype) mimetype, " +
                            "COALESCE(FD.descr, FF.descr) descr, FD.fname, FF.name, FF.type " +
                            "FROM FS.V_FDir AS FD left outer join FS.V_FFile as FF on (FD.f_id = FF.ZOID) " +
                            "where (FD.ZOID = ?)"
            );
            statement.setLong(1, Long.parseLong(lastId));
            return statement;
        }
        throw new Exception("Can not find PreparedStatement for path.");
    }

    public FileDetails getFileDetails(Connection connection, String path) throws Exception {
        try {
            path = getFullPath(connection, path);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Long zoid = getLastLevelFromPath(path);
        PreparedStatement fileDetailsPS = getFileDetails(connection, zoid);
        FileDetails fileDetails = new FileDetails();
        try {
            ResultSet resultSet = fileDetailsPS.executeQuery();
            int index = 0;
            while (resultSet.next()) {
                if (index >= 1) {
                    throw new Exception("More than 1 file present in this path.\nCall administrator to resolve.");
                }
                index++;
                if (!resultSet.getString("type").equals("B")) {
                    throw new Exception("Type not match.");
                }
                fileDetails.setMimeType(resultSet.getString("mimetype"));
                fileDetails.setFileName(resultSet.getString("name"));
            }
            resultSet.close();
        } finally {
            fileDetailsPS.close();
        }
        fileDetails.setFileLength(getFileLength(connection, zoid));
        fileDetails.setEncoding("UTF-8");
        return fileDetails;
    }

    public static boolean isFile(Connection connection, String path) throws Exception {
        if (path.equalsIgnoreCase("/"))
            return false;
        Long zoid = getLastLevelFromPath(path);
        boolean isFile = false;
        PreparedStatement statement = connection.prepareStatement("SELECT * FROM FS.V_FFile WHERE (ZOID = ?)");
        statement.setLong(1, zoid);
        if (statement != null) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                CMSType type = CMSType.getType(resultSet);
                if (type.equals(CMSType.FILE)) {
                    isFile = true;
                    break;
                }
            }
            resultSet.close();
        }
        statement.close();
        return isFile;
    }

    public void printToOutput(Connection connection, OutputStream outputStream, String path)
            throws Exception {
        printToOutput(connection, outputStream, getLastLevelFromPath(path));
    }

    private void printToOutput(Connection connection, OutputStream outputStream, Long zoid)
            throws SQLException, IOException {
        PreparedStatement blobDetailsPS = connection.prepareStatement(
                "SELECT * FROM FS.V_FBlob WHERE (ZOID = ?) ORDER BY ZRID ASC"
        );
        blobDetailsPS.setLong(1, zoid);
        try {
            ResultSet resultSet = blobDetailsPS.executeQuery();
            while (resultSet.next()) {
                InputStream chunk = resultSet.getBinaryStream("chunk");
                if (chunk != null) {
                    printBytes(outputStream, chunk);
                    chunk.close();
                }
            }
            resultSet.close();
        } finally {
            blobDetailsPS.close();
        }
    }

    public static int getPathLvl(String path) throws Exception {
        if (path == null || path.isEmpty() || path.trim().isEmpty()) {
            throw new Exception("Path was null.");
        }
        String processedPath = path.trim().replace("..", "");
        if (processedPath.equals(SEPARATOR)) {
            return LVL_SCOPE;
        }
        processedPath = processedPath.substring(1);
        int nextSlash = processedPath.indexOf(SEPARATOR);
        if (nextSlash == -1) {
            return LVL_ROOT;
        }
        if (nextSlash > 0) {
            String afterSlash = processedPath.substring(nextSlash + 1);
            if (afterSlash == null || afterSlash.isEmpty()) {
                return LVL_ROOT;
            }
        }
        return LVL_OTHER;
    }

    public static String getFullPath(Connection connection, String source) throws Exception {
        if (source == null || source.isEmpty()) {
            return "";
        }
        if (source.trim().equalsIgnoreCase(SEPARATOR)) {
            return SEPARATOR;
        }
        String selectForPath = getSelectForPath(source);
        Statement statement = connection.createStatement();
        try {
            ResultSet resultSet = statement.executeQuery(selectForPath);
            if (resultSet.next()) {
                String id = getStrValueOrEmpty(resultSet, ID);
                String zoid = getStrValueOrEmpty(resultSet, ZOID);
                List<String> fIds = new ArrayList<>();
                if (getPathParts(source).length > 2) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        if (columnName.equals("f_id")) {
                            String columnVal = resultSet.getString(i);
                            if (columnVal == null || columnName.isEmpty()) {
                                throw new Exception("f_id was null for one of the files in path.");
                            }
                            fIds.add(columnVal);
                        }
                    }
                }
                List<String> pathParts = new ArrayList<>();
                pathParts.add(id);
                if (!zoid.isEmpty()) {
                    pathParts.add(zoid);
                }
                pathParts.addAll(fIds);
                StringBuilder pathBuilder = new StringBuilder();
                for (String pathPart : pathParts) {
                    pathBuilder.append(SEPARATOR);
                    pathBuilder.append(pathPart);
                }
                return pathBuilder.toString();
            } else {
                throw new IllegalArgumentException("Can not find this path.");
            }
        } finally {
            statement.close();
        }
    }

    public static String getSelectForPath(String path) {
        String[] pathParts = getPathParts(path);
        int lvl = pathParts.length;
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ");
        for (int i = 0; i < lvl; i++) {
            if (i == 0) {
                builder.append("XS.id, XS.name");
            } else if (i == 1) {
                builder.append("FF.ZOID, FF.name");
            } else {
                builder.append(String.format("FD%d.f_id, FD%d.fname", i - 2, i - 2));
            }
            if (i != lvl - 1) {
                builder.append(",");
            }
        }
        builder.append(" FROM ");
        for (int i = 0; i < lvl; i++) {
            if (i == 0) {
                builder.append("SAM.V_Scope XS");
            } else if (i == 1) {
                builder.append("FS.V_FFile FF");
            } else {
                builder.append(String.format("FS.V_FDir FD%d", i - 2));
            }
            if (i != lvl - 1) {
                builder.append(",");
            }
        }
        return builder
                .append(" WHERE ")
                .append(getWhereForLvlAndName(pathParts, lvl))
                .toString();
    }

    public static String getWhereForLvlAndName(String[] partNames, int lvl) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lvl; i++) {
            if (i == 0) {
                builder.append(String.format(" XS.name = '%s'", partNames[i]));
            } else if (i == 1) {
                builder.append(String.format("FF.ZSID = XS.id and FF.name = '%s'", partNames[i]));
            } else if (i == 2) {
                builder.append(String.format("FD0.ZOID = FF.ZOID and FD0.fname = '%s'", partNames[i]));
            } else {
                builder.append(String.format("FD%d.ZOID = FD%d.f_id and FD%d.fname ='%s'", i - 2, i - 3, i - 2, partNames[i]));
            }
            if (i != lvl - 1) {
                builder.append(" and ");
            }
        }
        return builder.toString();
    }

    public static String[] getPathParts(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path was null.");
        }
        if (path.indexOf(SEPARATOR) == 0) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            return new String[0];
        }
        if (path.lastIndexOf(SEPARATOR) == path.length() - 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path.trim().split(SEPARATOR);
    }

    public static Long getLastLevelFromPath(String path) throws Exception {
        int lastSlash = path.lastIndexOf(SEPARATOR);
        if (lastSlash == -1) {
            throw new Exception("Illegal path.");
        } else {
            return Long.parseLong(path.substring(lastSlash + 1));
        }
    }

    public static Long getFileLength(Connection connection, Long zoid) throws Exception {
        PreparedStatement blobLengthPS = connection.prepareStatement("SELECT sum(length(chunk)) FROM FS.V_FBlob WHERE (ZOID = ?)");
        blobLengthPS.setLong(1, zoid);
        try {
            ResultSet resultSet = blobLengthPS.executeQuery();
            if (resultSet != null) {
                resultSet.next();
                return resultSet.getLong("sum");
            }
        } finally {
            blobLengthPS.close();
        }
        return -1L;
    }

    private PreparedStatement getFileDetails(Connection connection, Long zoid) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT name, mimetype, type FROM FS.V_FFile WHERE (ZOID = ?)");
        statement.setLong(1, zoid);
        return statement;
    }

    private void printBytes(OutputStream outputStream, InputStream fileStream) throws IOException {
        byte[] buff = new byte[1024];
        while (fileStream.read(buff) != -1) {
            outputStream.write(buff);
        }
    }
}
