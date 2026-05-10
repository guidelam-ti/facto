package com.guidelam.facto.drive;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.guidelam.facto.invoice.InvoiceFiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves (creates if missing) the {@code <root>/<year>/<MM-mois_fr>/} hierarchy
 * inside the user's Drive root folder. One instance per processing job — its
 * cache lives only for the duration of the run.
 *
 * <p>Not a Spring bean: instantiated by the orchestrator with a freshly built
 * Drive client and the user-selected root folder ID.</p>
 */
public class DriveFolderManager {

    private final Drive drive;
    private final String rootFolderId;
    private final Map<String, String> folderIdCache = new HashMap<>();

    public DriveFolderManager(Drive drive, String rootFolderId) {
        this.drive = drive;
        this.rootFolderId = rootFolderId;
    }

    public String ensureMonthFolder(int year, int month) throws IOException {
        String yearFolderId = ensureFolder(rootFolderId, String.valueOf(year));
        return ensureFolder(yearFolderId, InvoiceFiles.monthFolderName(month));
    }

    private String ensureFolder(String parentId, String name) throws IOException {
        String cacheKey = parentId + ":" + name;
        String cached = folderIdCache.get(cacheKey);
        if (cached != null) return cached;

        String escapedName = name.replace("\\", "\\\\").replace("'", "\\'");
        String query = "mimeType='" + DriveService.FOLDER_MIME_TYPE + "'"
                + " and trashed=false"
                + " and name='" + escapedName + "'"
                + " and '" + parentId + "' in parents";

        var resp = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id,name)")
                .setPageSize(10)
                .execute();

        if (resp.getFiles() != null && !resp.getFiles().isEmpty()) {
            String existingId = resp.getFiles().get(0).getId();
            folderIdCache.put(cacheKey, existingId);
            return existingId;
        }

        File metadata = new File();
        metadata.setName(name);
        metadata.setMimeType(DriveService.FOLDER_MIME_TYPE);
        metadata.setParents(List.of(parentId));

        File created = drive.files().create(metadata).setFields("id").execute();
        folderIdCache.put(cacheKey, created.getId());
        return created.getId();
    }
}
