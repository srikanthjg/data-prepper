///*
// * Copyright OpenSearch Contributors
// * SPDX-License-Identifier: Apache-2.0
// */
//package org.opensearch.dataprepper.plugins.sink.bedrock;
//
//import org.opensearch.dataprepper.aws.api.AwsCredentialsSupplier;
//import org.opensearch.dataprepper.model.annotations.DataPrepperPlugin;
//import org.opensearch.dataprepper.model.annotations.DataPrepperPluginConstructor;
//import org.opensearch.dataprepper.model.configuration.PluginSetting;
//import org.opensearch.dataprepper.model.event.Event;
//import org.opensearch.dataprepper.model.plugin.InvalidPluginConfigurationException;
//import org.opensearch.dataprepper.model.plugin.PluginFactory;
//import org.opensearch.dataprepper.model.record.Record;
//import org.opensearch.dataprepper.model.sink.AbstractSink;
//import org.opensearch.dataprepper.model.sink.OutputCodecContext;
//import org.opensearch.dataprepper.model.sink.Sink;
//import org.opensearch.dataprepper.model.sink.SinkContext;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Collection;
//
//@DataPrepperPlugin(name = "bedrock", pluginType = Sink.class, pluginConfigurationType = BedrockSinkConfig.class)
//public class BedrockSink extends AbstractSink<Record<Event>> {
//
//    private static final Logger LOG = LoggerFactory.getLogger(BedrockSink.class);
//    private volatile boolean sinkInitialized;
//    private final BedrockSinkService bedrockSinkService;
//    private static final String BUCKET = "bucket";
//    private static final String KEY_PATH = "key_path_prefix";
//
//    @DataPrepperPluginConstructor
//    public BedrockSink(final PluginSetting pluginSetting,
//                      final BedrockSinkConfig lambdaSinkConfig,
//                      final PluginFactory pluginFactory,
//                      final SinkContext sinkContext,
//                      final AwsCredentialsSupplier awsCredentialsSupplier
//    ) {
//        super(pluginSetting);
//        sinkInitialized = Boolean.FALSE;
//        OutputCodecContext outputCodecContext = OutputCodecContext.fromSinkContext(sinkContext);
////        LambdaClient lambdaClient = LambdaClientFactory.createLambdaClient(lambdaSinkConfig, awsCredentialsSupplier);
////        this.dlqPushHandler = new DlqPushHandler(pluginFactory,
////                String.valueOf(lambdaSinkConfig.getDlqPluginSetting().get(BUCKET)),
////                lambdaSinkConfig.getDlqStsRoleARN()
////                , lambdaSinkConfig.getDlqStsRegion(),
////                String.valueOf(lambdaSinkConfig.getDlqPluginSetting().get(KEY_PATH)));
////        this.bufferFactory = new InMemoryBufferFactory();
//
//        bedrockSinkService = new BedrockSinkService(bedrockClient,
//                lambdaSinkConfig,
//                pluginMetrics,
//                pluginFactory,
//                pluginSetting,
//                outputCodecContext,
//                awsCredentialsSupplier
//        );
//
//    }
//
//    @Override
//    public boolean isReady() {
//        return sinkInitialized;
//    }
//
//    @Override
//    public void doInitialize() {
//        try {
//            doInitializeInternal();
//        } catch (InvalidPluginConfigurationException e) {
//            LOG.error("Invalid plugin configuration, Hence failed to initialize s3-sink plugin.");
//            this.shutdown();
//            throw e;
//        } catch (Exception e) {
//            LOG.error("Failed to initialize lambda plugin.");
//            this.shutdown();
//            throw e;
//        }
//    }
//
//    private void doInitializeInternal() {
//        sinkInitialized = Boolean.TRUE;
//    }
//
//    /**
//     * @param records Records to be output
//     */
//    @Override
//    public void doOutput(final Collection<Record<Event>> records) {
//
//        if (records.isEmpty()) {
//            return;
//        }
//        bedrockSinkService.output(records);
//    }
//}