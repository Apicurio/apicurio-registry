package io.apicurio.registry.storage.error;


public class DownloadNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -8634862918588649938L;


    public DownloadNotFoundException() {
        super("Download not found.");
    }
}
