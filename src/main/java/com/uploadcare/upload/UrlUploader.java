package com.uploadcare.upload;

import com.uploadcare.api.Client;
import com.uploadcare.api.File;
import com.uploadcare.api.RequestHelper;
import com.uploadcare.data.UploadFromUrlData;
import com.uploadcare.data.UploadFromUrlStatusData;
import com.uploadcare.urls.Urls;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.TextUtils;

import java.net.URI;

/**
 * Uploadcare uploader for URLs.
 */
public class UrlUploader implements Uploader {

    private final Client client;

    private final String sourceUrl;

    private String store = "auto";

    private String filename = null;

    private String checkURLDuplicates = null;

    private String saveURLDuplicates = null;

    private String signature = null;

    private String expire = null;

    /**
     * Create a new uploader from a URL.
     *
     * @param client Uploadcare client
     * @param sourceUrl URL to upload from
     */
    public UrlUploader(Client client, String sourceUrl) {
        this.client = client;
        this.sourceUrl = sourceUrl;
    }

    /**
     * Synchronously uploads the file to Uploadcare.
     *
     * The calling thread will be busy until the upload is finished.
     * Uploadcare is polled every 500 ms for upload progress.
     *
     * @return An Uploadcare file
     * @throws UploadFailureException
     */
    public File upload() throws UploadFailureException {
        return upload(500);
    }

    /**
     * Store the file upon uploading.
     *
     * @param store is set true - store the file upon uploading. Requires “automatic file storing” setting to be enabled.
     *              is set false - do not store file upon uploading.
     */
    public UrlUploader store(boolean store) {
        this.store = store ? String.valueOf(1) : String.valueOf(0);
        return this;
    }

    /**
     * Store the file upon uploading.
     *
     * @param checkDuplicates Runs the duplicate check and provides the immediate-download behavior.
     */
    public UrlUploader checkDuplicates(boolean checkDuplicates) {
        this.checkURLDuplicates = checkDuplicates ? String.valueOf(1) : String.valueOf(0);
        return this;
    }

    /**
     * Store the file upon uploading.
     *
     * @param saveDuplicates Provides the save/update URL behavior. The parameter can be used if you believe a
     *                       source_url will be used more than once. If you don’t explicitly define "saveDuplicates",
     *                       it is by default set to the value of "checkDuplicates".
     */
    public UrlUploader saveDuplicates(boolean saveDuplicates) {
        this.saveURLDuplicates = saveDuplicates ? String.valueOf(1) : String.valueOf(0);
        return this;
    }

    /**
     * Sets the name for a file uploaded from URL. If not defined, the filename is obtained from either response headers
     * or a source URL.
     *
     * @param filename name for a file uploaded from URL.
     */
    public UrlUploader fileName(String filename){
        this.filename = filename;
        return this;
    }

    /**
     * Signed Upload - let you control who and when can upload files to a specified Uploadcare
     * project.
     *
     * @param signature is a string sent along with your upload request. It requires your Uploadcare
     *                  project secret key and hence should be crafted on your back end.
     * @param expire    sets the time until your signature is valid. It is a Unix time.(ex 1454902434)
     */
    public UrlUploader signedUpload(String signature, String expire) {
        this.signature = signature;
        this.expire = expire;
        return this;
    }

    /**
     * Synchronously uploads the file to Uploadcare.
     *
     * The calling thread will be busy until the upload is finished.
     *
     * @param pollingInterval Progress polling interval in ms
     * @return An Uploadcare file
     *
     * @throws UploadFailureException
     */
    public File upload(int pollingInterval) throws UploadFailureException {
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addTextBody("pub_key", client.getPublicKey());
        entityBuilder.addTextBody("source_url", sourceUrl);
        entityBuilder.addTextBody("store", store);

        if (filename != null) {
            entityBuilder.addTextBody("filename", filename);
        }

        if (checkURLDuplicates != null) {
            entityBuilder.addTextBody("check_URL_duplicates", checkURLDuplicates);
        }

        if (saveURLDuplicates != null) {
            entityBuilder.addTextBody("save_URL_duplicates", saveURLDuplicates);
        }

        if (!TextUtils.isEmpty(signature) && !TextUtils.isEmpty(expire)) {
            entityBuilder.addTextBody("signature", signature);
            entityBuilder.addTextBody("expire", expire);
        }

        URI uploadUrl = Urls.uploadFromUrl();
        HttpPost uploadRequest = new HttpPost(uploadUrl);
        uploadRequest.setEntity(entityBuilder.build());

        RequestHelper requestHelper = client.getRequestHelper();
        String token = requestHelper.executeQuery(uploadRequest, false, UploadFromUrlData.class).token;

        URI statusUrl = Urls.uploadFromUrlStatus(token);
        while (true) {
            sleep(pollingInterval);
            HttpGet request = new HttpGet(statusUrl);
            UploadFromUrlStatusData data = requestHelper.executeQuery(
                    request,
                    false,
                    UploadFromUrlStatusData.class);
            if (data.status.equals("success")) {
                if (client.getSecretKey() != null) {
                    // If Client have "secretkey", we use Rest API to get full file info.
                    return client.getFile(data.fileId);
                } else {
                    // If Client doesn't have "secretkey" info about file might not have all info.
                    return client.getUploadedFile(data.fileId);
                }
            } else if (data.status.equals("error") || data.status.equals("failed")) {
                throw new UploadFailureException(data.error);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
