/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.pipeline.parser.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.opensearch.dataprepper.model.configuration.PipelineModel;
import org.opensearch.dataprepper.model.configuration.PipelinesDataFlowModel;
import org.opensearch.dataprepper.pipeline.parser.model.PipelineTransformationModel;
import org.opensearch.dataprepper.pipeline.parser.model.TransformablePluginModel;
import org.opensearch.dataprepper.pipeline.parser.transformer.PipelineTemplateModel;
import org.opensearch.dataprepper.pipeline.parser.transformer.TransformersFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuleEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(RuleEvaluator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final TransformersFactory transformersFactory;
    private String PLUGIN_NAME = null;
    List<String> transformablePluginsList = List.of("documentdb","aws_lambda");
//    List<String> processorTransformablePlugins = List.of("aws_lambda");

    public RuleEvaluator(TransformersFactory transformersFactory) {
        this.transformersFactory = transformersFactory;
    }

    public RuleEvaluatorResult isTransformationNeeded(PipelinesDataFlowModel pipelineModel) {
        //TODO - Dynamically scan the rules folder and get the corresponding template.


        RuleEvaluatorResult docDBResult = isDocDBSource(pipelineModel);

        if (docDBResult.isEvaluatedResult()){
            return docDBResult;
        }
        return null;
    }

    /**
     * Evaluates model based on pre defined rules and
     * result contains the name of the pipeline that will need transformation,
     * evaluated boolean result and the corresponding template model
     * Assumption: only one pipeline can have transformation.
     *
     * @param pipelinesModel
     * @return RuleEvaluatorResult
     */
    private RuleEvaluatorResult isDocDBSource(PipelinesDataFlowModel pipelinesModel) {
        Map<String, PipelineModel> pipelines = pipelinesModel.getPipelines();
        List<TransformablePluginModel> transformablePluginModelsList = new ArrayList<>();

        for (Map.Entry<String, PipelineModel> entry : pipelines.entrySet()) {
            try {
                String pipelineJson = OBJECT_MAPPER.writeValueAsString(entry);

                for(String pluginName: transformablePluginsList) {
                    evaluate(pipelineJson, pluginName, transformablePluginModelsList);
                }
                if (transformablePluginModelsList.size()>0) {
                    LOG.info("Rule for {} is evaluated true for pipelineJson {}", PLUGIN_NAME, pipelineJson);

                    //TODO - add id to processors in pipelineJson. -> hardcoded for now in the pipeline config definition

                    //get processor list as jsonNode
                    List<JsonNode> originalPipelineProcessorList = new ArrayList<>();
                    String pipelineName = entry.getKey();
                    //array of processors
                    JsonNode processorNode = OBJECT_MAPPER.readTree(pipelineJson).at("/"+pipelineName+"/processor");

                    //get template model based on priority of transformation.
                    InputStream templateStream = transformersFactory.getPluginTemplateFileStream(PLUGIN_NAME);
                    PipelineTemplateModel templateModel = yamlMapper.readValue(templateStream,
                            PipelineTemplateModel.class);
                    LOG.info("Template is chosen for {}", PLUGIN_NAME);

                    PipelineTransformationModel pipelineTransformationModel = PipelineTransformationModel.builder()
                            .withTransformablePluginModel(transformablePluginModelsList)
                            .withPipelineJson(pipelineJson)
                            .withPipelineName(entry.getKey())
                            .build();

                    return RuleEvaluatorResult.builder()
                            .withEvaluatedResult(true)
                            .withPipelineTemplateModel(templateModel)
                            .withPipelineName(entry.getKey())
                            .withPipelineTransformationModel(pipelineTransformationModel)
                            .build();
                }
//                }
            } catch (FileNotFoundException e){
                LOG.error("Template File Not Found for {}", PLUGIN_NAME);
                throw new RuntimeException(e);
            }
            catch (JsonProcessingException e) {
                LOG.error("Error processing json");
                throw new RuntimeException(e);
            } catch (IOException e) {
                LOG.error("Error reading file");
                throw new RuntimeException(e);
            }
        }
        return RuleEvaluatorResult.builder()
                .withEvaluatedResult(false)
                .withPipelineName(null)
                .withPipelineTemplateModel(null)
                .build();
    }

    private Boolean evaluate(String pipelinesJson,
                             String pluginName,
                             List<TransformablePluginModel> transformablePluginModel) {

        Configuration parseConfig = Configuration.builder()
                .jsonProvider(new JacksonJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .options(Option.AS_PATH_LIST)
                .build();
        ParseContext parseContext = JsonPath.using(parseConfig);
        ReadContext readPathContext = parseContext.parse(pipelinesJson);

        RuleTransformerModel rulesModel = null;
        InputStream ruleStream = null;
        try {
            ruleStream = transformersFactory.getPluginRuleFileStream(pluginName);

            rulesModel = yamlMapper.readValue(ruleStream, RuleTransformerModel.class);
            List<String> rules = rulesModel.getApplyWhen();
//            for (String rule : rules) {
            try {
                //every plugin should have only one uniquely identifiable rule
                //that way if there are multiple of the same tranformable plugin then
                //we get a list.
                List<Map<String, Object>> result = readPathContext.read(rules.get(0));
                //List<Map<String, Object>> result = JsonPath.parse(json).read("$.lambda-pipeline.processor[?(@.aws_lambda.mode == 'event')]");
                //processor transformation
                if(result.stream().anyMatch(map -> map.keySet().stream().anyMatch(key -> key.contains("aws_lambda")))){
                    for(Map entry : result){
                        //todo
//                                entry.containsKey()
                        transformablePluginModel.add(TransformablePluginModel.builder()
                                        .withPluginName(pluginName)
                                        .withIsProcessorTransformation(true)
                                        .withIsTransformationNeeded(true)
//                                        .withNextNode(getNextNode(pluginName,result))
//                                        .withS3Bucket()
                                        .build()
                        );
                    }

                } else{ //source tranformation
                    transformablePluginModel.add(TransformablePluginModel.builder()
                            .withPluginName(pluginName)
                            .withIsProcessorTransformation(false)
                            .withIsTransformationNeeded(true)
                            .withNextNode(null)
//                            .withS3Bucket()
                            .build()
                    );
                }

            } catch (PathNotFoundException e) {
                LOG.warn("Json Path not found for {}", pluginName);
                return false;
            }
//            }
        } catch (FileNotFoundException e){
            LOG.warn("Rule File Not Found for {}", pluginName);
            return false;
        } catch(IOException e){
            throw new RuntimeException(e);
        }finally {
            if (ruleStream != null) {
                try {
                    ruleStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return true;
    }
}

