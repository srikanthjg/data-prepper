/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.awstranslate;

import org.opensearch.dataprepper.metrics.PluginMetrics;
import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
import org.opensearch.dataprepper.model.event.Event;
import org.opensearch.dataprepper.model.processor.AbstractProcessor;
import org.opensearch.dataprepper.model.processor.Processor;
import org.opensearch.dataprepper.model.record.Record;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;
import software.amazon.awssdk.services.translate.model.TranslateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@DataPrepperPlugin(name = "aws_translate", pluginType = Processor.class, pluginConfigurationType = AwsTranslateProcessorConfig.class)
public class AwsTranslateProcessor extends AbstractProcessor<Record<Event>, Record<Event>> {
    private static final Logger LOG = LoggerFactory.getLogger(AwsTranslateProcessor.class);
    private final AwsTranslateProcessorConfig config;

    @DataPrepperPluginConstructor
    public AwsTranslateProcessor(final PluginMetrics pluginMetrics, final AwsTranslateProcessorConfig config) {
        super(pluginMetrics);
        this.config = config;
    }

    @Override
    public Collection<Record<Event>> doExecute(final Collection<Record<Event>> records) {

        for (final Record<Event> record : records) {
            final Event event = record.getData();
            invokeTranslator(event);
        }
        return records;
    }

    private void invokeTranslator(Event event) {
        Region region = Region.US_EAST_1;
        TranslateClient translateClient = TranslateClient.builder()
                .region(region)
                .build();
        String sourceLanguage = config.getSourceLanguage();
        String targetLanguage = config.getTargetLanguage();

        try {
            TranslateTextRequest textRequest = TranslateTextRequest.builder()
                    .sourceLanguageCode(sourceLanguage) //"en"
                    .targetLanguageCode(targetLanguage) //"fr"
                    .text(event.toJsonString())
                    .build();

            TranslateTextResponse textResponse = translateClient.translateText(textRequest);
            event.put("translated", textResponse.translatedText());
            translateClient.close();
        } catch (TranslateException e) {
            translateClient.close();
            LOG.error(e.getMessage());
        }

        return;
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
