/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.bedrock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.Collection;

@DataPrepperPlugin(name = "bedrock", pluginType = Processor.class, pluginConfigurationType = BedrockProcessorConfig.class)
public class BedrockProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(BedrockProcessor.class);
    private final BedrockProcessorConfig config;
    BedrockRuntimeClient runtime = BedrockRuntimeClient.builder()
            .region(Region.US_EAST_1)
            .build();
    private static final String CLAUDE = "anthropic.claude-v2";
    private static final String TITAN_TEXT_EMBEDDING = "amazon.titan-embed-text-v1";
    private static final String JURASSIC2 = "ai21.j2-mid-v1";
    private static final String MISTRAL7B = "mistral.mistral-7b-instruct-v0:2";
    private static final String MIXTRAL8X7B = "mistral.mixtral-8x7b-instruct-v0:1";
    private static final String STABLE_DIFFUSION = "stability.stable-diffusion-xl";
    private static final String TITAN_IMAGE = "amazon.titan-image-generator-v1";


    @DataPrepperPluginConstructor
    public BedrockProcessor(final PluginMetrics pluginMetrics, final BedrockProcessorConfig config) {
        super(pluginMetrics);
        this.config = config;
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {
        for (final Record<Event> record : records) {
            final Event recordEvent = record.getData();
            try {
                String modelId = config.getModelId();
                switch(modelId) {
                    case CLAUDE:
                        invokeClaude(recordEvent);
                        break;

                    case TITAN_TEXT_EMBEDDING:
                        invokeTitanEmbed(recordEvent, TITAN_TEXT_EMBEDDING);
                        break;
                }
            } catch (Exception e) {
                LOG.error("Fail to perform bedrock call", e);
            }
        }
        return records;
    }

    private void invokeClaude(Event recordEvent) {
    }

    @Override
    public void prepareForShutdown() {
    }

    @Override
    public boolean isReadyForShutdown() {
        return true;
    }

    @Override
    public void shutdown() {
    }

    private void invokeTitanEmbed(Event recordEvent, final String modelId) {
        // Create a Bedrock Runtime client in the AWS Region of your choice.
        BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .build();

        // Set the model ID
        final String modelIdText = "amazon.titan-embed-text-v1";

        // Create a JSON payload using the model's native structure.
        JSONObject request = new JSONObject().put("inputText", recordEvent.toJsonString());

        // Encode and send the request.
        InvokeModelResponse response = client.invokeModel(req -> req
                .body(SdkBytes.fromUtf8String(request.toString()))
                .modelId(modelId));

        // Decode the model's native response body.
        JSONObject nativeResponse = new JSONObject(response.body().asUtf8String());

        // Extract and print the generated embedding.
        JSONArray embeddingsJsonArray = nativeResponse.getJSONArray("embedding");
        float[] embeddings = new float[embeddingsJsonArray.length()];
        for (int i = 0; i < embeddingsJsonArray.length(); i++) {
            embeddings[i] = embeddingsJsonArray.getFloat(i);
        }

        recordEvent.put("embeddings", (Object) embeddings);
    }
}
