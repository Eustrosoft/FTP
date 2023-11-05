package com.eustrosoft;

public final class FileDetails {
    private String mimeType;
    private String fileName;
    private String description;
    private String securityLevel;
    private CMSType type;
    private Long fileLength;
    private String encoding;

    public FileDetails() {
    }

    public FileDetails(String fileName) {
        this.fileName = fileName;
    }

    public FileDetails(String mimeType, String fileName, String description, String securityLevel, CMSType type, Long fileLength, String encoding) {
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.description = description;
        this.securityLevel = securityLevel;
        this.type = type;
        this.fileLength = fileLength;
        this.encoding = encoding;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFileLength() {
        return fileLength;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFileLength(Long fileLength) {
        this.fileLength = fileLength;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(String securityLevel) {
        this.securityLevel = securityLevel;
    }

    public CMSType getType() {
        return type;
    }

    public void setType(CMSType type) {
        this.type = type;
    }
}