/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.model.sink;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.metrics.MetricNames;
import org.opensearch.dataprepper.metrics.MetricsTestUtil;
import org.opensearch.dataprepper.model.configuration.PluginSetting;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.event.JacksonEvent;
import org.opensearch.dataprepper.model.event.EventHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

class AbstractSinkTest {
    private int count;
    @Test
    void testMetrics() {
        final String sinkName = "testSink";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();
        PluginSetting pluginSetting = new PluginSetting(sinkName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        AbstractSink<Record<String>> abstractSink = new AbstractSinkImpl(pluginSetting);
        abstractSink.initialize();
        assertEquals(abstractSink.isReady(), true);
        abstractSink.updateLatencyMetrics(Arrays.asList(
                new Record<>(UUID.randomUUID().toString())));
        abstractSink.output(Arrays.asList(
                new Record<>(UUID.randomUUID().toString()),
                new Record<>(UUID.randomUUID().toString()),
                new Record<>(UUID.randomUUID().toString()),
                new Record<>(UUID.randomUUID().toString()),
                new Record<>(UUID.randomUUID().toString())
        ));

        final List<Measurement> recordsInMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(sinkName).add(MetricNames.RECORDS_IN).toString());
        final List<Measurement> elapsedTimeMeasurements = MetricsTestUtil.getMeasurementList(
                new StringJoiner(MetricNames.DELIMITER).add(pipelineName).add(sinkName).add(MetricNames.TIME_ELAPSED).toString());

        assertEquals(1, recordsInMeasurements.size());
        assertEquals(5.0, recordsInMeasurements.get(0).getValue(), 0);
        assertEquals(3, elapsedTimeMeasurements.size());
        assertEquals(1.0, MetricsTestUtil.getMeasurementFromList(elapsedTimeMeasurements, Statistic.COUNT).getValue(), 0);
        assertTrue(MetricsTestUtil.isBetween(
                MetricsTestUtil.getMeasurementFromList(elapsedTimeMeasurements, Statistic.TOTAL_TIME).getValue(),
                0.2,
                0.3));
        assertEquals(abstractSink.getRetryThreadState(), null);
        abstractSink.shutdown();
    }

    @Test
    void testSinkNotReady() {
        final String sinkName = "testSink";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();
        PluginSetting pluginSetting = new PluginSetting(sinkName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        AbstractSink<Record<String>> abstractSink = new AbstractSinkNotReadyImpl(pluginSetting);
        abstractSink.initialize();
        assertEquals(abstractSink.isReady(), false);
        assertEquals(abstractSink.getRetryThreadState(), Thread.State.RUNNABLE);
        // Do another intialize to make sure the sink is still not ready
        abstractSink.initialize();
        assertEquals(abstractSink.isReady(), false);
        await().atMost(Duration.ofSeconds(5))
                .until(abstractSink::isReady);
        assertEquals(abstractSink.getRetryThreadState(), Thread.State.TERMINATED);
        abstractSink.shutdown();
    }

    @Test
    void testSinkWithRegisterEventReleaseHandler() {
        final String sinkName = "testSink";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();
        PluginSetting pluginSetting = new PluginSetting(sinkName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        AbstractSink<Record<Event>> abstractSink = new AbstractEventSinkImpl(pluginSetting);
        abstractSink.initialize();
        assertEquals(abstractSink.isReady(), true);
        count = 0;
        Event event = JacksonEvent.builder()
                .withEventType("event")
                .build();
        Record record = mock(Record.class);
        EventHandle eventHandle = mock(EventHandle.class);
        when(record.getData()).thenReturn(event);

        abstractSink.updateLatencyMetrics(Arrays.asList(record));
        abstractSink.output(Arrays.asList(record));
        await().atMost(Duration.ofSeconds(5))
                .until(abstractSink::isReady);
        abstractSink.shutdown();
    }

    @Test
    void testSinkReadyWithOutputSync() {
        final String sinkName = "testSink";
        final String pipelineName = "pipelineName";
        MetricsTestUtil.initMetrics();
        PluginSetting pluginSetting = new PluginSetting(sinkName, Collections.emptyMap());
        pluginSetting.setPipelineName(pipelineName);
        AbstractSink<Record<String>> abstractSink = new AbstractSinkImpl(pluginSetting);
        abstractSink.initialize();
        assertEquals(abstractSink.isReady(), true);
        Record record = mock(Record.class);
        abstractSink.updateLatencyMetrics(Arrays.asList(record));
        Object returnResponse = abstractSink.outputSync(Arrays.asList(record), false);
        assertTrue(returnResponse != null);
        abstractSink.shutdown();
    }

    private static class AbstractEventSinkImpl extends AbstractSink<Record<Event>> {

        AbstractEventSinkImpl(PluginSetting pluginSetting) {
            super(pluginSetting, 10, 1000);
        }

        @Override
        public void doOutput(Collection<Record<Event>> records) {
            for (final Record<Event> record: records) {
                Event event = record.getData();
                event.getEventHandle().release(true);
            }
        }

        @Override
        public Object doOutputSync(Collection<Record<Event>> records, boolean isQuery) {
            return super.doOutputSync(records, isQuery);
        }

        @Override
        public void shutdown() {
            super.shutdown();
        }

        @Override
        public void doInitialize() {
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }


    private static class AbstractSinkImpl extends AbstractSink<Record<String>> {

        AbstractSinkImpl(PluginSetting pluginSetting) {
            super(pluginSetting, 10, 1000);
        }

        @Override
        public void doOutput(Collection<Record<String>> records) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {

            }
        }

        @Override
        public void shutdown() {
            super.shutdown();
        }

        @Override
        public void doInitialize() {
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }

    private static class AbstractSinkNotReadyImpl extends AbstractSink<Record<String>> {

        boolean initialized;
        int initCount;
        AbstractSinkNotReadyImpl(PluginSetting pluginSetting) {
            super(pluginSetting);
            initialized = false;
            initCount = 0;
        }

        @Override
        public void doOutput(Collection<Record<String>> records) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
        }

        @Override
        public void shutdown() {
            super.shutdown();
        }

        @Override
        public void doInitialize() {
            // make this check for smaller number so that test finishes sooner
            if (initCount++ == DEFAULT_MAX_RETRIES/200) {
                initialized = true;
            }
        }

        @Override
        public boolean isReady() {
            return initialized;
        }
    }
}
