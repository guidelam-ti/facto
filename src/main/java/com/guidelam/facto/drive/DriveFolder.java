package com.guidelam.facto.drive;

import java.time.Instant;

public record DriveFolder(
        String id,
        String name,
        Instant modifiedTime
) {
}
