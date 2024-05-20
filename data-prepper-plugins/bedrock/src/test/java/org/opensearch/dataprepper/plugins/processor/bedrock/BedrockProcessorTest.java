//package org.opensearch.dataprepper.plugins.processor.bedrock;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.opensearch.dataprepper.metrics.PluginMetrics;
//import org.opensearch.dataprepper.model.event.Event;
//import org.opensearch.dataprepper.model.record.Record;
//import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
//
//import java.util.Collection;
//import java.util.Collections;
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
//class BedrockProcessorTest {
//    private BedrockProcessor processor;
//    private PluginMetrics pluginMetrics;
//    private BedrockProcessorConfig config;
//    private BedrockRuntimeClient mockClient;
//
//    @BeforeEach
//    void setup() {
//        pluginMetrics = mock(PluginMetrics.class);
//        config = mock(BedrockProcessorConfig.class);
//        when(config.getModelId()).thenReturn("amazon.titan-embed-text-v1");
//
//        mockClient = mock(BedrockRuntimeClient.class, RETURNS_DEEP_STUBS);
//        processor = new BedrockProcessor(pluginMetrics, config);
//    }
//
//    @Test
//    void testDoExecute() throws Exception {
//        Event mockEvent = mock(Event.class);
//        when(mockEvent.toJsonString()).thenReturn("test event content");
//
//        Record<Event> record = new Record<>(mockEvent);
//        List<Record<Event>> records = Collections.singletonList(record);
//
////        InvokeModelResponse response = InvokeModelResponse.builder()
////                .body(SdkBytes.fromUtf8String("{\"embedding\": [1.0, 2.0, 3.0]}"))
////                .httpResponse(SdkHttpResponse.builder().statusCode(200).build())
////                .build();
////        when(mockClient.invokeModel(any())).thenReturn(response);
//
//        Collection<Record<Event>> processedRecords = processor.doExecute(records);
//
////        verify(mockClient).invokeModel(any());
//        verify(mockEvent).put(eq("embeddings"), any());
//    }
//
//    @Test
//    void testErrorHandling() {
//        Event mockEvent = mock(Event.class);
//        when(mockEvent.toJsonString()).thenReturn("test event content");
//
//        Record<Event> record = new Record<>(mockEvent);
//        List<Record<Event>> records = Collections.singletonList(record);
//
////        when(mockClient.invokeModel(any())).thenThrow(RuntimeException.class);
////
////        processor.doExecute(records);
////
////        verify(mockClient).invokeModel(any());
//    }
//}
