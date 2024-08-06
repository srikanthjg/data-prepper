/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.s3;

import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.EventMetadata;
import org.opensearch.dataprepper.plugins.source.s3.configuration.S3ScanBucketOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

class EventMetadataModifier implements BiConsumer<Event, S3ObjectReference> {
    private static final Logger LOG = LoggerFactory.getLogger(EventMetadataModifier.class);

    private static final String BUCKET_FIELD_NAME = "bucket";
    private static final String KEY_FIELD_NAME = "key";
    public static final String METADATA_KEY_NEXT_NODE = "next_node";
    private final String baseKey;
    private final boolean deleteS3MetadataInEvent;
    S3SourceConfig s3SourceConfig;

    EventMetadataModifier(final String metadataRootKey, boolean deleteS3MetadataInEvent,S3SourceConfig s3SourceConfig) {
        baseKey = generateBaseKey(metadataRootKey);
        this.deleteS3MetadataInEvent = deleteS3MetadataInEvent;
        this.s3SourceConfig = s3SourceConfig;
    }

    @Override
    public void accept(final Event event, final S3ObjectReference s3ObjectReference) {
        if(!deleteS3MetadataInEvent) {
            event.put(baseKey + BUCKET_FIELD_NAME, s3ObjectReference.getBucketName());
            event.put(baseKey + KEY_FIELD_NAME, s3ObjectReference.getKey());
        }
        EventMetadata eventMetadata = event.getMetadata();
        //put based on bucket to
        List<S3ScanBucketOptions> s3BucketOptions = s3SourceConfig.getS3ScanScanOptions().getBuckets();
        for(S3ScanBucketOptions s3BucketOption: s3BucketOptions){
            String bucketName = s3BucketOption.getS3ScanBucketOption().getName();
            if(bucketName.equals(s3ObjectReference.getBucketName())) {
                String nextNode = s3BucketOption.getS3ScanBucketOption().getNextNode();
                eventMetadata.setAttribute(METADATA_KEY_NEXT_NODE, nextNode);
                LOG.info("Adding next node {} metadata to event from bucket {}",nextNode, bucketName);
            }
        }

    }

    private static String generateBaseKey(String metadataRootKey) {
        Objects.requireNonNull(metadataRootKey);

        if(metadataRootKey.startsWith("/"))
            metadataRootKey = metadataRootKey.substring(1);

        if(metadataRootKey.isEmpty() || metadataRootKey.endsWith("/"))
            return metadataRootKey;

        return metadataRootKey + "/";
    }
}
