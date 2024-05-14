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
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeClaude;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeStableDiffusion;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeTitanImage;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeTitanImageEmbed;
import static org.opensearch.dataprepper.plugins.processor.bedrock.InvokeModel.invokeTitanTextEmbed;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.CLAUDE_V2;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.STABLE_DIFFUSION;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.TITAN_IMAGE;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.TITAN_IMAGE_EMBEDDING_V1;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.TITAN_TEXT_EMBEDDING_V1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

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
        for (final Record<Event> record : records) {
            final Event recordEvent = record.getData();
            try {
                String modelId = config.getModelId();
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
        return records;
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
