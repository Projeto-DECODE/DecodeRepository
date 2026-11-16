package com.example.sos.objects.dto;

import java.io.InputStream;

public record DownloadedObject(String filename, long sizeBytes, InputStream stream) {
}
