/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.dataframe.checkpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.client.Client;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameIndexerPosition;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransformCheckpointingInfo;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransformConfig;
import org.elasticsearch.xpack.core.dataframe.transforms.DataFrameTransformProgress;
import org.elasticsearch.xpack.core.dataframe.transforms.TimeSyncConfig;
import org.elasticsearch.xpack.core.indexing.IndexerState;
import org.elasticsearch.xpack.dataframe.persistence.DataFrameTransformsConfigManager;

/**
 * DataFrameTransform Checkpoint Service
 *
 * Allows checkpointing a source of a data frame transform which includes all relevant checkpoints of the source.
 *
 * This will be used to checkpoint a transform, detect changes, run the transform in continuous mode.
 *
 */
public class DataFrameTransformsCheckpointService {

    private static final Logger logger = LogManager.getLogger(DataFrameTransformsCheckpointService.class);

    private final Client client;
    private final DataFrameTransformsConfigManager dataFrameTransformsConfigManager;

    public DataFrameTransformsCheckpointService(final Client client,
            final DataFrameTransformsConfigManager dataFrameTransformsConfigManager) {
        this.client = client;
        this.dataFrameTransformsConfigManager = dataFrameTransformsConfigManager;
    }

    public CheckpointProvider getCheckpointProvider(final DataFrameTransformConfig transformConfig) {
        if (transformConfig.getSyncConfig() instanceof TimeSyncConfig) {
            return new TimeBasedCheckpointProvider(client, dataFrameTransformsConfigManager, transformConfig);
        }

        return new DefaultCheckpointProvider(client, dataFrameTransformsConfigManager, transformConfig);
    }

    /**
     * Get checkpointing stats for a stopped data frame
     *
     * @param transformId The data frame task
     * @param lastCheckpointNumber the last checkpoint
     * @param nextCheckpointIndexerState indexer state for the next checkpoint
     * @param nextCheckpointPosition position for the next checkpoint
     * @param nextCheckpointProgress progress for the next checkpoint
     * @param listener listener to retrieve the result
     */
    public void getCheckpointingInfo(final String transformId,
                                     final long lastCheckpointNumber,
                                     final IndexerState nextCheckpointIndexerState,
                                     final DataFrameIndexerPosition nextCheckpointPosition,
                                     final DataFrameTransformProgress nextCheckpointProgress,
                                     final ActionListener<DataFrameTransformCheckpointingInfo> listener) {

        // we need to retrieve the config first before we can defer the rest to the corresponding provider
        dataFrameTransformsConfigManager.getTransformConfiguration(transformId, ActionListener.wrap(
            transformConfig -> {
                getCheckpointProvider(transformConfig).getCheckpointingInfo(lastCheckpointNumber, nextCheckpointIndexerState,
                            nextCheckpointPosition, nextCheckpointProgress, listener);
                },
            transformError -> {
                logger.warn("Failed to retrieve configuration for data frame [" + transformId + "]", transformError);
                listener.onFailure(new CheckpointException("Failed to retrieve configuration", transformError));
            })
        );
    }

}
