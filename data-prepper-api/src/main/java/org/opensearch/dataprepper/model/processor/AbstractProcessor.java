/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.processor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.opensearch.dataprepper.metrics.MetricNames;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;

import java.util.Collection;
import java.util.Map;

/**
 * @since 1.2
 * Abstract implementation of the {@link Processor} interface. This class implements an execute function which records
 * some basic metrics. Logic of the execute function is handled by extensions of this class in the doExecute function.
 */
public abstract class AbstractProcessor<InputRecord extends Record<?>, OutputRecord extends Record<?>> implements
        Processor<InputRecord, OutputRecord> {

    public static final String METADATA_KEY_NEXT_NODE = "next_node";
    protected final PluginMetrics pluginMetrics;
    private final Counter recordsInCounter;
    private final Counter recordsOutCounter;
    private final Timer timeElapsedTimer;
    private final PluginSetting pluginSetting;

    public AbstractProcessor(final PluginSetting pluginSetting) {
        this.pluginSetting = pluginSetting;
        pluginMetrics = PluginMetrics.fromPluginSetting(pluginSetting);
        recordsInCounter = pluginMetrics.counter(MetricNames.RECORDS_IN);
        recordsOutCounter = pluginMetrics.counter(MetricNames.RECORDS_OUT);
        timeElapsedTimer = pluginMetrics.timer(MetricNames.TIME_ELAPSED);
    }

    protected AbstractProcessor(final PluginMetrics pluginMetrics) {
        this.pluginMetrics = pluginMetrics;
        recordsInCounter = pluginMetrics.counter(MetricNames.RECORDS_IN);
        recordsOutCounter = pluginMetrics.counter(MetricNames.RECORDS_OUT);
        timeElapsedTimer = pluginMetrics.timer(MetricNames.TIME_ELAPSED);
        this.pluginSetting = null;
    }

    /**
     * @since 1.2
     * This execute function calls the {@link AbstractProcessor#doExecute(Collection)} function of the implementation,
     * and records metrics for records in, records out, and elapsed time.
     * @param records Input records that will be modified/processed
     * @return Records as processed by the doExecute function
     */
    @Override
    public Collection<OutputRecord> execute(Collection<InputRecord> records) {
        recordsInCounter.increment(records.size());

        Boolean isValidNode = false;
        String currentNodeID = String.valueOf(pluginSetting.getSettings().get("id"));

        // Check if right node to start execution chain
        // Assumption: all events of records have the same metadata.
        for (InputRecord record : records) {
            final Event event = (Event) record.getData();
            Map<String, Object> attributes = event.getMetadata().getAttributes();
            if (attributes.containsKey(METADATA_KEY_NEXT_NODE)) {
                String nextNodeID = String.valueOf(attributes.get(METADATA_KEY_NEXT_NODE));
                if(nextNodeID.equals(currentNodeID)){
                    isValidNode = true;
                }
                break;
            }
        }

        //skip processing if not valid node
        if(isValidNode) {
            final Collection<OutputRecord> result = timeElapsedTimer.record(() -> doExecute(records));
            recordsOutCounter.increment(result.size());
            return result;
        }else{
            return (Collection<OutputRecord>) records;
        }
    }

    //TODO
    private boolean isCurrentNode(String nodeID) {
        String currentNodeID = String.valueOf(pluginSetting.getSettings().get("id"));
        String node = currentNodeID;
        return nodeID.equals(currentNodeID);
    }

    /**
     * @since 1.2
     * This function should implement the processing logic of the processor
     * @param records Input records
     * @return Processed records
     */
    public abstract Collection<OutputRecord> doExecute(Collection<InputRecord> records);
}
