/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.plugins.source.s3;

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.common.concurrent.BackgroundThreadFactory;
import org.opensearch.dataprepper.model.acknowledgements.AcknowledgementSetManager;
import org.opensearch.dataprepper.model.source.coordinator.SourceCoordinator;
import org.opensearch.dataprepper.plugins.source.s3.configuration.S3ScanBucketOptions;
import org.opensearch.dataprepper.plugins.source.s3.ownership.BucketOwnerProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Class responsible for taking an {@link S3SourceConfig} and creating all the necessary {@link ScanOptions}
 * objects and spawn a thread {@link S3SelectObjectWorker}
 */
public class S3ScanService {
    static final long SHUTDOWN_TIMEOUT = 30L;

    private final S3SourceConfig s3SourceConfig;
    private final List<S3ScanBucketOptions> s3ScanBucketOptions;
    private final S3ClientBuilderFactory s3ClientBuilderFactory;
    private final LocalDateTime endDateTime;
    private final LocalDateTime startDateTime;
    private final Duration range;
    private final S3ObjectHandler s3ObjectHandler;

    private Thread scanObjectWorkerThread;

    private final BucketOwnerProvider bucketOwnerProvider;
    private final SourceCoordinator<S3SourceProgressState> sourceCoordinator;
    private final AcknowledgementSetManager acknowledgementSetManager;
    private final S3ObjectDeleteWorker s3ObjectDeleteWorker;
    private final PluginMetrics pluginMetrics;
    private final ExecutorService executorService;
    private final List<ScanObjectWorker> workers;

    public S3ScanService(final S3SourceConfig s3SourceConfig,
                         final S3ClientBuilderFactory s3ClientBuilderFactory,
                         final S3ObjectHandler s3ObjectHandler,
                         final BucketOwnerProvider bucketOwnerProvider,
                         final SourceCoordinator<S3SourceProgressState> sourceCoordinator,
                         final AcknowledgementSetManager acknowledgementSetManager,
                         final S3ObjectDeleteWorker s3ObjectDeleteWorker,
                         final PluginMetrics pluginMetrics) {
        this.s3SourceConfig = s3SourceConfig;
        this.s3ScanBucketOptions = s3SourceConfig.getS3ScanScanOptions().getBuckets();
        this.s3ClientBuilderFactory = s3ClientBuilderFactory;
        this.endDateTime = s3SourceConfig.getS3ScanScanOptions().getEndTime();
        this.startDateTime = s3SourceConfig.getS3ScanScanOptions().getStartTime();
        this.range = s3SourceConfig.getS3ScanScanOptions().getRange();
        this.s3ObjectHandler = s3ObjectHandler;
        this.bucketOwnerProvider = bucketOwnerProvider;
        this.sourceCoordinator = sourceCoordinator;
        this.acknowledgementSetManager = acknowledgementSetManager;
        this.s3ObjectDeleteWorker = s3ObjectDeleteWorker;
        this.pluginMetrics = pluginMetrics;
        this.workers = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(s3SourceConfig.getNumWorkers(), BackgroundThreadFactory.defaultExecutorThreadFactory("s3-source-scan"));
    }

    public void start() {
        long backOffMs = s3SourceConfig.getBackOff().toMillis();
        for (int i = 0; i < s3SourceConfig.getNumWorkers(); i++) {
            ScanObjectWorker scanObjectWorker = new ScanObjectWorker(s3ClientBuilderFactory.getS3Client(),
                    getScanOptions(),s3ObjectHandler,bucketOwnerProvider, sourceCoordinator, s3SourceConfig, acknowledgementSetManager, s3ObjectDeleteWorker, backOffMs, pluginMetrics);
            workers.add(scanObjectWorker);
            executorService.submit(new Thread(scanObjectWorker));
        }
    }

    public void stop() {
        for (int i = 0; i < s3SourceConfig.getNumWorkers(); i++) {
            workers.get(i).stop();
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            if (e.getCause() instanceof InterruptedException) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * This Method Used to fetch the scan options details from {@link S3SourceConfig} amd build the
     * all the s3 scan buckets information in list.
     *
     * @return @List<ScanOptionsBuilder>
     */
    List<ScanOptions> getScanOptions() {
        List<ScanOptions> scanOptionsList = new ArrayList<>();
        s3ScanBucketOptions.forEach(
                obj -> buildScanOptions(scanOptionsList, obj));
        return scanOptionsList;
    }

    private void buildScanOptions(final List<ScanOptions> scanOptionsList, final S3ScanBucketOptions scanBucketOptions) {
        scanOptionsList.add(ScanOptions.builder()
                .setStartDateTime(startDateTime)
                .setEndDateTime(endDateTime)
                .setRange(range)
                .setBucketOption(scanBucketOptions.getS3ScanBucketOption())
                        .set
                .build());
    }
}
