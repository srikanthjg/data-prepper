/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.dataprepper.pipeline.parser.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class RuleEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(RuleEvaluator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final String PIPELINE_NAME_PLACEHOLDER = "<<pipeline-name>>";
    private static final String PIPELINE_NAME_PLACEHOLDER_REGEX = "\\<\\<\\s*" + Pattern.quote("pipeline-name") + "\\s*\\>\\>";


    private final TransformersFactory transformersFactory;
    List<String> transformablePluginsList = List.of("documentdb","aws_lambda");
//    List<String> processorTransformablePlugins = List.of("aws_lambda");

    public RuleEvaluator(TransformersFactory transformersFactory) {
        this.transformersFactory = transformersFactory;
    }

    public RuleEvaluatorResult isTransformationNeeded(PipelinesDataFlowModel pipelineModel) {

        //TODO - preprocess all pipelines to add id for processors.
        //hardcoded for now.

        RuleEvaluatorResult ruleEvaluationResult = isPluginTransformationNeeded(pipelineModel);

        if (ruleEvaluationResult.isEvaluatedResult()){
            //modify template model based on the transformarable plugin.
            modifyTemplateModel(ruleEvaluationResult);
            return ruleEvaluationResult;
        }
        return ruleEvaluationResult;
    }

    /**
     * Transform template based on the transformable plugins
     *
     * @param ruleEvaluationResult
     */
    private void modifyTemplateModel(RuleEvaluatorResult ruleEvaluationResult) {
        PipelineTransformationModel pipelineTransformationModel = ruleEvaluationResult.getPipelineTransformationModel();
        PipelineTemplateModel pipelineTemplateModel = ruleEvaluationResult.getPipelineTemplateModel();
//        String pipelineTemplateJson = null;
//        try {
//            pipelineTemplateJson = OBJECT_MAPPER.writeValueAsString(pipelineTemplateModel);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }

        if(pipelineTransformationModel.getIsSourceTransformation()){
            modifyTemplateModelBasedOnSourceTransformation(ruleEvaluationResult, pipelineTransformationModel);
        } else if(pipelineTransformationModel.getIsProcessorTransformation()){
            modifyTemplateModelBasedOnProcessorTransformation(ruleEvaluationResult, pipelineTransformationModel);
        } else{
            throw new RuntimeException("Unknown transformation");
        }

    }

    private void modifyTemplateModelBasedOnProcessorTransformation(RuleEvaluatorResult ruleEvaluatorResult,
                                                                   PipelineTransformationModel pipelineTransformationModel){
        modifyS3BucketinTemplate(ruleEvaluatorResult, pipelineTransformationModel);
        modifyProcessorInTemplate(ruleEvaluatorResult, pipelineTransformationModel);
    }

    /**
     * If processor transformation,
     * First pipeline - modify only processor; processor list only till first tranformable processor
     * Second pipeline - modify processor (rest of the processors) and sink(add s3 buckets)
     *
     *
     * @param ruleEvaluatorResult
     * @param pipelineTransformationModel
     */
    private void modifyProcessorInTemplate(RuleEvaluatorResult ruleEvaluatorResult, PipelineTransformationModel pipelineTransformationModel) {
        List<JsonNode> originalProcessorList = pipelineTransformationModel.getOriginalPipelineProcessorList();
        String pipelineName = ruleEvaluatorResult.getPipelineName();
        String pipelineJson = pipelineTransformationModel.getPipelineJson();
        String jsonPtrExprProcessorFirstPipeline = "/templatePipelines/"+pipelineName+"/processor/";
        String jsonPtrExprProcessorSecondPipeline = "/templatePipelines/"+pipelineName+"-s3"+"/processor/";

        JsonNode processor = null;
        String templateJson = null;
        JsonNode templateJsonNode = null;
        PipelineTemplateModel pipelineTemplateModel = null;

        try {
            templateJson = OBJECT_MAPPER.writeValueAsString(ruleEvaluatorResult.getPipelineTemplateModel());
            templateJson = templateJson.replaceAll(PIPELINE_NAME_PLACEHOLDER_REGEX,pipelineName);
            templateJsonNode = OBJECT_MAPPER.readTree(templateJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


        //Only if there are more than 1 tranformable plugins this is needed.
        //modify 1st pipeline
        //modify processor list only - till transformedAtProcessorIndex
        JsonNode firstPipelineNode = templateJsonNode.at("/templatePipelines/" + pipelineName);
        if (!firstPipelineNode.isObject()) {
            throw new RuntimeException("The first pipeline node is not an object");
        }
        ArrayNode processorListInFirstPipeline = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i <= pipelineTransformationModel.getTransformedAtProcessorIndex(); i++) {
            processorListInFirstPipeline.add(originalProcessorList.get(i));
        }
        ((ObjectNode) firstPipelineNode).set("processor", processorListInFirstPipeline);

        // Modify 2nd pipeline processor node
        JsonNode secondPipelineNode = templateJsonNode.at("/templatePipelines/" + pipelineName + "-s3");
        if (!secondPipelineNode.isObject()) {
            throw new RuntimeException("The second pipeline node is not an object");
        }
        ArrayNode processorListInSecondPipeline = OBJECT_MAPPER.createArrayNode();
        for (int i = pipelineTransformationModel.getTransformedAtProcessorIndex() + 1; i < originalProcessorList.size(); i++) {
            processorListInSecondPipeline.add(originalProcessorList.get(i));
        }
        ((ObjectNode) secondPipelineNode).set("processor", processorListInSecondPipeline);


        // Extract AWS configuration from the aws_lambda processor and populate it to
        JsonNode awsConfig = null;
        Integer id = pipelineTransformationModel.getTransformedAtProcessorIndex();
        String pluginName = pipelineTransformationModel.getPluginNameThatNeedsTranformation();
        TransformablePluginModel transformablePluginModel = pipelineTransformationModel.getTransformablePluginModelMap().get(id);
        JsonNode originalConfigForTransformablePlugin = transformablePluginModel.getOriginalConfig();
        awsConfig = originalConfigForTransformablePlugin.at("/"+pluginName+"/aws");
        // Set AWS configuration in the second pipeline source if found
        if (awsConfig != null) {
            JsonNode s3SourceNode = secondPipelineNode.at("/source/s3");
            if (s3SourceNode.isObject()) {
                ((ObjectNode) s3SourceNode).set("aws", awsConfig);
            }
        }
        // Serialize the modified rootNode back to PipelineTemplateModel
        try {
            pipelineTemplateModel = OBJECT_MAPPER.treeToValue(templateJsonNode, PipelineTemplateModel.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ruleEvaluatorResult.setPipelineTemplateModel(pipelineTemplateModel);

    }

    private void modifyS3BucketinTemplate(RuleEvaluatorResult ruleEvaluatorResult, PipelineTransformationModel pipelineTransformationModel) {
        //insert s3 buckets and next node to s3 scan pipeline.
        Map<Integer, TransformablePluginModel> transformablePluginModelMap = pipelineTransformationModel.getTransformablePluginModelMap();
        String pipelineName = ruleEvaluatorResult.getPipelineName();
        //get 2nd pipeline(s3 scan) details
        String jsonPtrExprS3ScanBucketSecondPipeline = "/templatePipelines/"+pipelineName+"-s3"+"/source/s3/scan/buckets";
        PipelineTemplateModel pipelineTemplateModelModified = null;
        String templateJson = null;
        ArrayNode sourceS3ScanBuckets = null;
        JsonNode templateNode = null;

        try {
            templateJson = OBJECT_MAPPER.writeValueAsString(ruleEvaluatorResult.getPipelineTemplateModel());
            templateJson = templateJson.replaceAll(PIPELINE_NAME_PLACEHOLDER_REGEX, pipelineName);
            templateNode = OBJECT_MAPPER.readTree(templateJson);
            sourceS3ScanBuckets = (ArrayNode)OBJECT_MAPPER.readTree(templateJson).at(jsonPtrExprS3ScanBucketSecondPipeline);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        //modify s3 scan bucket
        for (Map.Entry<Integer, TransformablePluginModel> entry: transformablePluginModelMap.entrySet()){
            if(entry.getKey() ==-1)continue; //if it is source just continue
            // Create a new bucket entry
            ObjectNode newBucket = OBJECT_MAPPER.createObjectNode();
            ObjectNode bucketDetails = OBJECT_MAPPER.createObjectNode();

            TransformablePluginModel transformablePluginModel = entry.getValue();
            Integer nextNode = transformablePluginModel.getNextNode();
            String s3Bucket = transformablePluginModel.getS3Bucket();
            bucketDetails.put("name", s3Bucket);
            bucketDetails.put("next_node", nextNode);
            newBucket.set("bucket", bucketDetails);

            // Add the new bucket entry to the buckets array
            sourceS3ScanBuckets.add(newBucket);
        }

        // Replace the buckets array node in the root node
        ((ObjectNode) templateNode.at("/templatePipelines/" + pipelineName + "-s3/source/s3/scan")).set("buckets", sourceS3ScanBuckets);

        // Serialize the modified rootNode back to PipelineTemplateModel
        try {
            pipelineTemplateModelModified = OBJECT_MAPPER.treeToValue(templateNode, PipelineTemplateModel.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ruleEvaluatorResult.setPipelineTemplateModel(pipelineTemplateModelModified);
    }

    /*
    Assumption: 2nd pipeline is always s3-scan pipeline.
     */
    private void modifyTemplateModelBasedOnSourceTransformation(RuleEvaluatorResult ruleEvaluatorResult,PipelineTransformationModel pipelineTransformationModel) {
        //insert s3 buckets and next node to s3 scan pipeline.
        modifyS3BucketinTemplate(ruleEvaluatorResult, pipelineTransformationModel);
        return;
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
    private RuleEvaluatorResult isPluginTransformationNeeded(PipelinesDataFlowModel pipelinesModel) {
        Map<String, PipelineModel> pipelines = pipelinesModel.getPipelines();
        PipelineTransformationModel pipelineTransformationModel = new PipelineTransformationModel();
        Map<Integer, TransformablePluginModel> transformablePluginModelMap = new HashMap<>();
        pipelineTransformationModel.setTransformablePluginModelMap(transformablePluginModelMap);

        for (Map.Entry<String, PipelineModel> entry : pipelines.entrySet()) {
            String pluginTranformation = null;
            try {
                String pipelineJson = OBJECT_MAPPER.writeValueAsString(entry);
                String pipelineName = entry.getKey();
                //evaluate tranformation needed for every plugin
                for (String pluginName : transformablePluginsList) {
                    evaluate(pipelineJson, pipelineName, pluginName, pipelineTransformationModel);
                }
                if (transformablePluginModelMap.size() > 0) {
                    pluginTranformation = pipelineTransformationModel.getPluginNameThatNeedsTranformation();
                    LOG.info("Rule for {} is evaluated true for pipelineJson {}", pluginTranformation, pipelineJson);

                    //get template model based on priority of transformation.
                    InputStream templateStream = transformersFactory.getPluginTemplateFileStream(pluginTranformation);
                    PipelineTemplateModel templateModel = yamlMapper.readValue(templateStream,
                            PipelineTemplateModel.class);
                    LOG.info("Template is chosen for {}", pluginTranformation);

                    return RuleEvaluatorResult.builder()
                            .withEvaluatedResult(true)
                            .withPipelineTemplateModel(templateModel)
                            .withPipelineName(pipelineName)
                            .withPipelineTransformationModel(pipelineTransformationModel)
                            .build();
                }
//                }
            } catch (FileNotFoundException e) {
                LOG.error("Template File Not Found for {}", pluginTranformation);
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
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

    private void populateProcessorListAsJsonNode(Map.Entry<String, PipelineModel> entry, String pipelineJson, List<JsonNode> originalPipelineProcessorList) throws JsonProcessingException {
        String pipelineName = entry.getKey();
        //array of processors
        JsonNode processorNode = OBJECT_MAPPER.readTree(pipelineJson).at("/"+pipelineName+"/processor");

        // Add each JsonNode from the original ArrayNode to the List
        for (JsonNode node : processorNode) {
            originalPipelineProcessorList.add(node);
        }
    }

    private void evaluate(String pipelineJson, String pipelineName,
                          String pluginName,
                          PipelineTransformationModel pipelineTransformationModel) {

        Configuration parseConfig = Configuration.builder()
                .jsonProvider(new JacksonJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .options(Option.SUPPRESS_EXCEPTIONS)
                .build();
        ParseContext parseContext = JsonPath.using(parseConfig);
        ReadContext readPathContext = parseContext.parse(pipelineJson);
        ArrayNode processors;
        //get processor list as jsonNode
        List<JsonNode> originalPipelineProcessorList = new ArrayList<>();
//        populateProcessorListAsJsonNode(pipelineName, pipelineJson, originalPipelineProcessorList);

        try {
            processors = (ArrayNode) OBJECT_MAPPER.readTree(pipelineJson).at("/"+pipelineName+"/processor");

            // Add each JsonNode from the original ArrayNode to the List
            for (JsonNode node : processors) {
                originalPipelineProcessorList.add(node);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        RuleTransformerModel rulesModel = null;
        InputStream ruleStream = null;
        try {
            ruleStream = transformersFactory.getPluginRuleFileStream(pluginName);
            rulesModel = yamlMapper.readValue(ruleStream, RuleTransformerModel.class);
            List<String> rulesOrg = rulesModel.getApplyWhen();
            List<String> rules = new ArrayList<>();
            //replace pipeline-name placeholder
            for(String rule: rulesOrg){
                if(rule.contains("<<pipeline-name>>")){
                    String ruleModified = rule.replace(PIPELINE_NAME_PLACEHOLDER,pipelineName);
                    rules.add(ruleModified);
                }else{
                    rules.add(rule);
                }
            }


            //every plugin should have only one uniquely identifiable rule
            //that way if there are multiple of the same tranformable plugin then
            //we get a list.
            JsonNode result = readPathContext.read(rules.get(0), JsonNode.class);
            if(result == null){
                pipelineTransformationModel.setIsTranformationNeeded(false);
                return;
            }

            Map<Integer, TransformablePluginModel> transformablePluginModelMap = pipelineTransformationModel.getTransformablePluginModelMap();

            //processor transformation
            //result cannot be null here as readPathContext.read did not throw an exception.
            if(rules.get(0).contains("aws_lambda")){
                ArrayNode processorsFromRule = (ArrayNode) result;
                Integer processorTransformationIndex = Integer.MAX_VALUE;
                for (JsonNode processor : processorsFromRule) {
                    Integer id = Integer.valueOf(String.valueOf(processor.at("/"+pluginName+"/id")));
                    processorTransformationIndex = Integer.min(id,processorTransformationIndex);
                    String s3_bucket = String.valueOf(processor.at("/"+pluginName+"/s3_bucket"));
                    Integer nextNode = getNextNode(id,processors.size());
                    TransformablePluginModel transformablePluginModel =
                            TransformablePluginModel.builder()
                                    .withPluginName(pluginName)
                                    .withNextNode(nextNode)
                                    .withS3Bucket(s3_bucket)
                                    .withOriginalConfig(processor)
                                    .build();
                    transformablePluginModelMap.put(id, transformablePluginModel);
                }
                //if both source and processor transformation co-exist, give preference to source.
                if(pipelineTransformationModel.getIsSourceTransformation()!=null &&
                        pipelineTransformationModel.getIsSourceTransformation()) {
                    pipelineTransformationModel.setPipelineJson(pipelineJson);
                    pipelineTransformationModel.setOriginalPipelineProcessorList(originalPipelineProcessorList);
                    pipelineTransformationModel.setIsProcessorTransformation(false);
                    pipelineTransformationModel.setIsSourceTransformation(true);
                    pipelineTransformationModel.setTransformedAtProcessorIndex(null);
                    pipelineTransformationModel.setIsTranformationNeeded(true);
                }else{
                    pipelineTransformationModel.setPipelineJson(pipelineJson);
                    pipelineTransformationModel.setOriginalPipelineProcessorList(originalPipelineProcessorList);
                    pipelineTransformationModel.setPluginNameThatNeedsTranformation(pluginName);
                    pipelineTransformationModel.setIsProcessorTransformation(true);
                    pipelineTransformationModel.setIsSourceTransformation(false);
                    pipelineTransformationModel.setTransformedAtProcessorIndex(processorTransformationIndex);
                    pipelineTransformationModel.setIsTranformationNeeded(true);
                }
            } else{ //source tranformation
                pipelineTransformationModel.setPipelineJson(pipelineJson);
                pipelineTransformationModel.setOriginalPipelineProcessorList(originalPipelineProcessorList);
                pipelineTransformationModel.setPluginNameThatNeedsTranformation(pluginName);
                pipelineTransformationModel.setIsProcessorTransformation(false);
                pipelineTransformationModel.setIsSourceTransformation(true);
                pipelineTransformationModel.setIsTranformationNeeded(true);
                pipelineTransformationModel.setTransformedAtProcessorIndex(null);
            }

        } catch (PathNotFoundException e) {
            LOG.warn("Json Path not found for {}", pluginName);
            return;
        } catch (FileNotFoundException e){
            LOG.warn("Rule File Not Found for {}", pluginName);
            pipelineTransformationModel.setIsTranformationNeeded(false);
            return ;
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
        return;
    }

    private Integer getNextNode(Integer id, Integer size) {
        if (id>=size-1){
            return null;
        }
        return id+1;
    }
}

