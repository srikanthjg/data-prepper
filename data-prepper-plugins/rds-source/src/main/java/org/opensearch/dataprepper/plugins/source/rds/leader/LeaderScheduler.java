/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.source.rds.leader;

import org.opensearch.dataprepper.model.source.coordinator.enhanced.EnhancedSourceCoordinator;
import org.opensearch.dataprepper.model.source.coordinator.enhanced.EnhancedSourcePartition;
import org.opensearch.dataprepper.plugins.source.rds.RdsSourceConfig;
import org.opensearch.dataprepper.plugins.source.rds.coordination.partition.ExportPartition;
import org.opensearch.dataprepper.plugins.source.rds.coordination.partition.GlobalState;
import org.opensearch.dataprepper.plugins.source.rds.coordination.partition.LeaderPartition;
import org.opensearch.dataprepper.plugins.source.rds.coordination.partition.StreamPartition;
import org.opensearch.dataprepper.plugins.source.rds.coordination.state.ExportProgressState;
import org.opensearch.dataprepper.plugins.source.rds.coordination.state.LeaderProgressState;
import org.opensearch.dataprepper.plugins.source.rds.coordination.state.MySqlStreamState;
import org.opensearch.dataprepper.plugins.source.rds.coordination.state.PostgresStreamState;
import org.opensearch.dataprepper.plugins.source.rds.coordination.state.StreamProgressState;
import org.opensearch.dataprepper.plugins.source.rds.model.BinlogCoordinate;
import org.opensearch.dataprepper.plugins.source.rds.model.DbTableMetadata;
import org.opensearch.dataprepper.plugins.source.rds.schema.MySqlSchemaManager;
import org.opensearch.dataprepper.plugins.source.rds.schema.PostgresSchemaManager;
import org.opensearch.dataprepper.plugins.source.rds.schema.SchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.opensearch.dataprepper.plugins.source.rds.RdsService.S3_PATH_DELIMITER;

public class LeaderScheduler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(LeaderScheduler.class);
    private static final int DEFAULT_EXTEND_LEASE_MINUTES = 3;
    private static final Duration DEFAULT_LEASE_INTERVAL = Duration.ofMinutes(1);
    private static final String S3_EXPORT_PREFIX = "rds";
    private final EnhancedSourceCoordinator sourceCoordinator;
    private final RdsSourceConfig sourceConfig;
    private final String s3Prefix;
    private final SchemaManager schemaManager;
    private final DbTableMetadata dbTableMetadata;
    private final String pipelineName;

    private LeaderPartition leaderPartition;
    private List<String> tableNames;
    private StreamPartition streamPartition = null;
    private volatile boolean shutdownRequested = false;

    public LeaderScheduler(final EnhancedSourceCoordinator sourceCoordinator,
                           final RdsSourceConfig sourceConfig,
                           final String s3Prefix,
                           final SchemaManager schemaManager,
                           final DbTableMetadata dbTableMetadata,
                           final String pipelineName) {
        this.sourceCoordinator = sourceCoordinator;
        this.sourceConfig = sourceConfig;
        this.s3Prefix = s3Prefix;
        this.schemaManager = schemaManager;
        this.dbTableMetadata = dbTableMetadata;
        this.pipelineName = pipelineName;
        tableNames = new ArrayList<>(dbTableMetadata.getTableColumnDataTypeMap().keySet());
    }

    @Override
    public void run() {
        LOG.info("Starting Leader Scheduler for initialization.");

        while (!shutdownRequested && !Thread.currentThread().isInterrupted()) {
            try {
                // Try to acquire the lease if not owned
                if (leaderPartition == null) {
                    final Optional<EnhancedSourcePartition> sourcePartition = sourceCoordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE);
                    if (sourcePartition.isPresent()) {
                        LOG.info("Running as a LEADER node.");
                        leaderPartition = (LeaderPartition) sourcePartition.get();
                    }
                }

                // Once owned, run Normal LEADER node process
                if (leaderPartition != null) {
                    LeaderProgressState leaderProgressState = leaderPartition.getProgressState().get();
                    if (!leaderProgressState.isInitialized()) {
                        LOG.info("Performing initialization as LEADER node.");
                        init();
                    }
                }
            } catch (final Exception e) {
                LOG.error("Exception occurred in primary leader scheduling loop", e);
            } finally {
                if (leaderPartition != null) {
                    // Extend the timeout
                    // will always be a leader until shutdown
                    sourceCoordinator.saveProgressStateForPartition(leaderPartition, Duration.ofMinutes(DEFAULT_EXTEND_LEASE_MINUTES));
                }

                try {
                    Thread.sleep(DEFAULT_LEASE_INTERVAL.toMillis());
                } catch (final InterruptedException e) {
                    LOG.info("InterruptedException occurred while waiting in leader scheduling loop.");
                    break;
                }
            }
        }

        // Should stop
        LOG.warn("Quitting Leader Scheduler");
        if (leaderPartition != null) {
            sourceCoordinator.giveUpPartition(leaderPartition);
        }
    }

    public void shutdown() {
        shutdownRequested = true;
    }

    private void init() {
        LOG.info("Initializing RDS source service...");

        // Create a Global state in the coordination table for rds cluster/instance information.
        // Global State here is designed to be able to read whenever needed
        // So that the jobs can refer to the configuration.
        sourceCoordinator.createPartition(new GlobalState(sourceConfig.getDbIdentifier(), dbTableMetadata.toMap()));
        LOG.debug("Created global state for DB: {}", sourceConfig.getDbIdentifier());

        if (sourceConfig.isExportEnabled()) {
            LOG.debug("Export is enabled. Creating export partition in the source coordination store.");
            createExportPartition(sourceConfig);
        }

        if (sourceConfig.isStreamEnabled()) {
            LOG.debug("Stream is enabled. Creating stream partition in the source coordination store.");
            createStreamPartition(sourceConfig);
        }

        LOG.debug("Update initialization state");
        LeaderProgressState leaderProgressState = leaderPartition.getProgressState().get();
        leaderProgressState.setInitialized(true);
    }

    private void createExportPartition(RdsSourceConfig sourceConfig) {
        ExportProgressState progressState = new ExportProgressState();
        progressState.setEngineType(sourceConfig.getEngine().toString());
        progressState.setIamRoleArn(sourceConfig.getExport().getIamRoleArn());
        progressState.setBucket(sourceConfig.getS3Bucket());
        // This prefix is for data exported from RDS
        progressState.setPrefix(getS3PrefixForExport(s3Prefix));
        progressState.setTables(tableNames);
        progressState.setKmsKeyId(sourceConfig.getExport().getKmsKeyId());
        progressState.setPrimaryKeyMap(getPrimaryKeyMap());
        ExportPartition exportPartition = new ExportPartition(sourceConfig.getDbIdentifier(), sourceConfig.isCluster(), progressState);
        sourceCoordinator.createPartition(exportPartition);
    }

    private String getS3PrefixForExport(final String givenS3Prefix) {
        return givenS3Prefix.isEmpty() ? S3_EXPORT_PREFIX : givenS3Prefix + S3_PATH_DELIMITER + S3_EXPORT_PREFIX;
    }

    private Map<String, List<String>> getPrimaryKeyMap() {
        return schemaManager.getPrimaryKeys(tableNames);
    }

    private Map<String, Set<String>> getPostgresEnumColumnsByTable() {
        return ((PostgresSchemaManager) schemaManager).getEnumColumns(tableNames);
    }

    private void createStreamPartition(RdsSourceConfig sourceConfig) {
        final StreamProgressState progressState = new StreamProgressState();
        progressState.setEngineType(sourceConfig.getEngine().toString());
        progressState.setWaitForExport(sourceConfig.isExportEnabled());
        progressState.setPrimaryKeyMap(getPrimaryKeyMap());
        if (sourceConfig.getEngine().isMySql()) {
            final MySqlStreamState mySqlStreamState = new MySqlStreamState();
            getCurrentBinlogPosition().ifPresent(mySqlStreamState::setCurrentPosition);
            mySqlStreamState.setForeignKeyRelations(((MySqlSchemaManager)schemaManager).getForeignKeyRelations(tableNames));
            progressState.setMySqlStreamState(mySqlStreamState);
        } else {
            // Postgres
            // Create replication slot, which will mark the starting point for stream
            final String suffix = UUID.randomUUID().toString().substring(0, 8);
            final String publicationName = generatePublicationName(suffix);
            final String slotName = generateReplicationSlotName(suffix);
            ((PostgresSchemaManager)schemaManager).createLogicalReplicationSlot(tableNames, publicationName, slotName);
            final PostgresStreamState postgresStreamState = new PostgresStreamState();
            postgresStreamState.setPublicationName(publicationName);
            postgresStreamState.setReplicationSlotName(slotName);
            postgresStreamState.setEnumColumnsByTable(getPostgresEnumColumnsByTable());
            progressState.setPostgresStreamState(postgresStreamState);
        }
        streamPartition = new StreamPartition(sourceConfig.getDbIdentifier(), progressState);
        sourceCoordinator.createPartition(streamPartition);
    }

    private Optional<BinlogCoordinate> getCurrentBinlogPosition() {
        Optional<BinlogCoordinate> binlogCoordinate = ((MySqlSchemaManager)schemaManager).getCurrentBinaryLogPosition();
        LOG.debug("Current binlog position: {}", binlogCoordinate.orElse(null));
        return binlogCoordinate;
    }

    private String generatePublicationName(final String suffix) {
        return "data_prepper_" + getPipelineName() + "_pub_" + suffix;
    }

    private String generateReplicationSlotName(final String suffix) {
        return "data_prepper_" + getPipelineName() + "_slot_" + suffix;
    }

    private String getPipelineName() {
        // Shorten the name (if needed) and replace any invalid characters with underscores
        final String shortenedPipelineName = pipelineName.length() <= 16 ? pipelineName : pipelineName.substring(0, 16);
        return shortenedPipelineName.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
