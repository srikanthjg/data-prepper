/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.bedrock;

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.createJsonPayload;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeClaude;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeStableDiffusion;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeTitanImage;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeTitanImageEmbed;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeTitanTextEmbed;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.sendPostRequest;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.ASYNC_MODE;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.CLAUDE_V2;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.STABLE_DIFFUSION;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.SYNC_MODE;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.TITAN_IMAGE;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.TITAN_IMAGE_EMBEDDING_V1;
import static org.opensearch.dataprepper.plugins.processor.bedrock.constants.TITAN_TEXT_EMBEDDING_V1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

@DataPrepperPlugin(name = "bedrock", pluginType = Processor.class, pluginConfigurationType = BedrockProcessorConfig.class)
public class BedrockProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(BedrockProcessor.class);
    private final BedrockProcessorConfig config;

    @DataPrepperPluginConstructor
    public BedrockProcessor(final PluginMetrics pluginMetrics, final BedrockProcessorConfig config) {
        super(pluginMetrics);
        this.config = config;
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {

        String modelId = config.getModelId();
        String mode = config.getMode();

        if (mode.equals(ASYNC_MODE)){
            invokeBatchInferenceJob();
            return records;
        }

        for (final Record<Event> record : records) {
            final Event recordEvent = record.getData();
            if(mode.equals(SYNC_MODE)) {
                invokeModel(recordEvent, modelId);
            } else{
                LOG.info("invalid option");
            }
        }
        return records;
    }

    private void invokeBatchInferenceJob() {
        String url =config.getUrl();

        url = url + "/model/" + config.getModelId() + "/model-invocation-job";
        String clientRequestToken = UUID.randomUUID().toString();
        String s3InputUri = config.getS3InputUri();

        assert s3InputUri.endsWith(".jsonl");

        String jsonPayload = createJsonPayload(
                clientRequestToken,
                s3InputUri, //should be jsonl
                config.getJobName(),
                config.getModelId(),
                config.getS3OutputUri(),
                config.getAws().getAwsStsRoleArn(),
                null,
                null
        );

        try {
            sendPostRequest(url,jsonPayload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void invokeModel(Event recordEvent, String modelId) {
        try {
            switch (modelId) {
                case CLAUDE_V2:
                    invokeClaude(recordEvent);
                    break;

                case TITAN_TEXT_EMBEDDING_V1:
                    invokeTitanTextEmbed(recordEvent);
                    break;

                case TITAN_IMAGE_EMBEDDING_V1:
                    invokeTitanImageEmbed(recordEvent);
                    break;

                case STABLE_DIFFUSION:
                    invokeStableDiffusion(recordEvent);
                    break;

                case TITAN_IMAGE:
                    invokeTitanImage(recordEvent);
                    break;
            }
        } catch (Exception e) {
            LOG.error("Fail to perform bedrock call", e);
        }
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

}
