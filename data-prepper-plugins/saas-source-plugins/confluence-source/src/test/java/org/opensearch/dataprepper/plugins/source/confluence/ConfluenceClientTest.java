/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 */

package org.opensearch.dataprepper.plugins.source.confluence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.acknowledgements.AcknowledgementSet;
import org.opensearch.dataprepper.model.buffer.Buffer;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.record.Record;
import org.opensearch.dataprepper.plugins.source.source_crawler.base.PluginExecutorServiceProvider;
import org.opensearch.dataprepper.plugins.source.source_crawler.coordination.state.PaginationCrawlerWorkerProgressState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfluenceClientTest {

    @Mock
    private Buffer<Record<Event>> buffer;
    @Mock
    private PaginationCrawlerWorkerProgressState saasWorkerProgressState;
    @Mock
    private AcknowledgementSet acknowledgementSet;
    @Mock
    private ConfluenceSourceConfig confluenceSourceConfig;
    @Mock
    private ConfluenceService confluenceService;
    @Mock
    private ConfluenceIterator confluenceIterator;
    private final PluginExecutorServiceProvider executorServiceProvider = new PluginExecutorServiceProvider();

    @Test
    void testConstructor() {
        ConfluenceClient confluenceClient = new ConfluenceClient(confluenceService, confluenceIterator, executorServiceProvider, confluenceSourceConfig);
        assertNotNull(confluenceClient);
    }

    @Test
    void testListItems() {
        ConfluenceClient confluenceClient = new ConfluenceClient(confluenceService, confluenceIterator, executorServiceProvider, confluenceSourceConfig);
        assertNotNull(confluenceClient.listItems(Instant.ofEpochSecond(1234L)));
    }


    @Test
    void testExecutePartition() throws Exception {
        ConfluenceClient confluenceClient = new ConfluenceClient(confluenceService, confluenceIterator, executorServiceProvider, confluenceSourceConfig);
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("space", "test");
        when(saasWorkerProgressState.getKeyAttributes()).thenReturn(keyAttributes);
        List<String> itemIds = new ArrayList<>();
        itemIds.add(null);
        itemIds.add("ID2");
        itemIds.add("ID3");
        when(saasWorkerProgressState.getItemIds()).thenReturn(itemIds);
        Instant exportStartTime = Instant.now();
        when(saasWorkerProgressState.getExportStartTime()).thenReturn(Instant.ofEpochSecond(exportStartTime.toEpochMilli()));

        when(confluenceService.getContent(anyString())).thenReturn("{\"id\":\"ID1\",\"key\":\"TEST-1\"}");

        ArgumentCaptor<Collection<Record<Event>>> recordsCaptor = ArgumentCaptor.forClass((Class) Collection.class);

        confluenceClient.executePartition(saasWorkerProgressState, buffer, acknowledgementSet);

        verify(buffer).writeAll(recordsCaptor.capture(), anyInt());
        Collection<Record<Event>> capturedRecords = recordsCaptor.getValue();
        assertFalse(capturedRecords.isEmpty());
        for (Record<Event> record : capturedRecords) {
            assertNotNull(record.getData());
        }
    }

    @Test
    void testExecutePartitionError() throws Exception {
        ConfluenceClient confluenceClient = new ConfluenceClient(confluenceService, confluenceIterator, executorServiceProvider, confluenceSourceConfig);
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("project", "test");
        when(saasWorkerProgressState.getKeyAttributes()).thenReturn(keyAttributes);
        List<String> itemIds = List.of("ID1", "ID2", "ID3", "ID4");
        when(saasWorkerProgressState.getItemIds()).thenReturn(itemIds);
        Instant exportStartTime = Instant.now();
        when(saasWorkerProgressState.getExportStartTime()).thenReturn(Instant.ofEpochSecond(exportStartTime.toEpochMilli()));

        when(confluenceService.getContent(anyString())).thenReturn("{\"id\":\"ID1\",\"key\":\"TEST-1\"}");


        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.readValue(any(String.class), any(TypeReference.class))).thenThrow(new JsonProcessingException("test") {
        });
        confluenceClient.injectObjectMapper(mockObjectMapper);

        assertThrows(RuntimeException.class, () -> confluenceClient.executePartition(saasWorkerProgressState, buffer, acknowledgementSet));
    }

    @Test
    void bufferWriteRuntimeTest() throws Exception {
        ConfluenceClient confluenceClient = new ConfluenceClient(confluenceService, confluenceIterator, executorServiceProvider, confluenceSourceConfig);
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("space", "test");
        when(saasWorkerProgressState.getKeyAttributes()).thenReturn(keyAttributes);
        List<String> itemIds = List.of("ID1", "ID2", "ID3", "ID4");
        when(saasWorkerProgressState.getItemIds()).thenReturn(itemIds);
        Instant exportStartTime = Instant.now();
        when(saasWorkerProgressState.getExportStartTime()).thenReturn(Instant.ofEpochSecond(exportStartTime.toEpochMilli()));

        when(confluenceService.getContent(anyString())).thenReturn("{\"id\":\"ID1\",\"key\":\"TEST-1\"}");

        ArgumentCaptor<Collection<Record<Event>>> recordsCaptor = ArgumentCaptor.forClass((Class) Collection.class);

        doThrow(new RuntimeException()).when(buffer).writeAll(recordsCaptor.capture(), anyInt());
        assertThrows(RuntimeException.class, () -> confluenceClient.executePartition(saasWorkerProgressState, buffer, acknowledgementSet));
    }
}