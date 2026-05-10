package com.guidelam.facto.drive;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.guidelam.facto.auth.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveService {

    public static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    public static final String PDF_MIME_TYPE = "application/pdf";
    public static final String APP_NAME = "facto";

    private static final int MAX_RESULTS = 100;

    private final TokenStore tokenStore;
    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public Optional<Drive> client() {
        return tokenStore.loadCredential().map(credential ->
                new Drive.Builder(httpTransport, jsonFactory, credential)
                        .setApplicationName(APP_NAME)
                        .build());
    }

    public List<DriveFolder> listFolders(String nameContains) throws IOException {
        Drive drive = client().orElseThrow(() ->
                new IllegalStateException("Compte Google non connecté"));

        StringBuilder q = new StringBuilder("mimeType='").append(FOLDER_MIME_TYPE)
                .append("' and trashed=false");
        if (nameContains != null && !nameContains.isBlank()) {
            String escaped = nameContains.trim().replace("\\", "\\\\").replace("'", "\\'");
            q.append(" and name contains '").append(escaped).append("'");
        }

        var response = drive.files().list()
                .setQ(q.toString())
                .setSpaces("drive")
                .setFields("files(id,name,modifiedTime)")
                .setOrderBy("name")
                .setPageSize(MAX_RESULTS)
                .execute();

        return response.getFiles().stream()
                .map(file -> new DriveFolder(
                        file.getId(),
                        file.getName(),
                        file.getModifiedTime() != null
                                ? Instant.ofEpochMilli(file.getModifiedTime().getValue())
                                : null))
                .toList();
    }

    /**
     * Uploads a PDF into the given Drive folder. Returns the new file's Drive ID.
     */
    public String uploadPdf(Drive drive, String parentFolderId, String fileName, byte[] data)
            throws IOException {
        File metadata = new File();
        metadata.setName(fileName);
        metadata.setParents(List.of(parentFolderId));
        metadata.setMimeType(PDF_MIME_TYPE);

        ByteArrayContent content = new ByteArrayContent(PDF_MIME_TYPE, data);
        File created = drive.files().create(metadata, content)
                .setFields("id, name")
                .execute();
        return created.getId();
    }

    /**
     * Deletes a Drive file (or folder). 404 is treated as success — the file
     * was already gone, which matches the desired post-state.
     */
    public void deleteFile(Drive drive, String fileId) throws IOException {
        try {
            drive.files().delete(fileId).execute();
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }
            log.debug("Drive file {} already gone (404), skipping", fileId);
        }
    }

    /**
     * Returns the ID of a child folder by name under {@code parentId}, or empty
     * if it doesn't exist.
     */
    public Optional<String> findFolderByName(Drive drive, String parentId, String name)
            throws IOException {
        String escaped = name.replace("\\", "\\\\").replace("'", "\\'");
        String q = "mimeType='" + FOLDER_MIME_TYPE + "'"
                + " and trashed=false"
                + " and name='" + escaped + "'"
                + " and '" + parentId + "' in parents";
        var resp = drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setFields("files(id)")
                .setPageSize(10)
                .execute();
        if (resp.getFiles() == null || resp.getFiles().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resp.getFiles().get(0).getId());
    }

    /**
     * Returns true if the folder has no non-trashed children.
     */
    public boolean isFolderEmpty(Drive drive, String folderId) throws IOException {
        var resp = drive.files().list()
                .setQ("'" + folderId + "' in parents and trashed=false")
                .setSpaces("drive")
                .setFields("files(id)")
                .setPageSize(1)
                .execute();
        return resp.getFiles() == null || resp.getFiles().isEmpty();
    }
}
