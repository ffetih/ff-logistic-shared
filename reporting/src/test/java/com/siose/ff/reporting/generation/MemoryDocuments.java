package com.siose.ff.reporting.generation;

import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.storage.ObjectStoreUnavailableException;
import com.siose.ff.base.storage.ReferenceOnlyStorageAdapter;
import com.siose.ff.base.storage.StorageObjectDescriptor;
import com.siose.ff.base.storage.StorageObjectDescriptor.StorageMode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The same keys as the S3 adapter, with the bytes kept here. The download link is
 * {@code memory://<objectKey>}; the test reads the bytes back by that key.
 */
final class MemoryDocuments implements ObjectStoragePort {

    private final ReferenceOnlyStorageAdapter locations;
    private final Map<String, byte[]> stored = new ConcurrentHashMap<>();

    MemoryDocuments(String bucket) {
        this.locations = new ReferenceOnlyStorageAdapter(bucket);
    }

    byte[] bytes(String objectKey) {
        return stored.get(objectKey);
    }

    @Override
    public StorageObjectDescriptor registerDocumentReference(
            String tenantId, String category, String referenceId, String fileName, String contentType) {
        return locations.registerDocumentReference(tenantId, category, referenceId, fileName, contentType);
    }

    @Override
    public StorageObjectDescriptor store(
            String tenantId, String category, String referenceId, String fileName, String contentType, byte[] content) {
        StorageObjectDescriptor described = locations.registerDocumentReference(
                tenantId, category, referenceId, fileName, contentType);
        stored.put(described.objectKey(), Arrays.copyOf(content, content.length));
        return new StorageObjectDescriptor(described.tenantId(), described.bucketName(), described.objectKey(),
                described.category(), described.contentType(), StorageMode.STORED, described.objectUri(),
                Instant.now());
    }

    @Override
    public URI downloadUrl(StorageObjectDescriptor document, Duration validFor) {
        if (!stored.containsKey(document.objectKey())) {
            throw new ObjectStoreUnavailableException("No document at " + document.objectKey());
        }
        return URI.create("memory://" + document.objectKey());
    }

    @Override
    public URI uploadUrl(StorageObjectDescriptor document, Duration validFor) {
        throw new ObjectStoreUnavailableException("This store does not sign uploads");
    }

    @Override
    public boolean exists(StorageObjectDescriptor document) {
        return stored.containsKey(document.objectKey());
    }
}
