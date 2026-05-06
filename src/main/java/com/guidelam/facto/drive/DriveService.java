package com.guidelam.facto.drive;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.drive.Drive;
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
}
