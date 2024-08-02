package org.opensearch.dataprepper.pipeline.parser.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder(setterPrefix = "with")
@Getter
@Setter
@AllArgsConstructor
public class PipelineTransformationModel {
    String pipelineJson;
    List<JsonNode> originalPipelineProcessorList; //processorJson
    String pluginNameThatNeedsTranformation;
    Boolean isTranformationNeeded;
    Boolean isProcessorTransformation; //only async processors need transformation
    Boolean isSourceTransformation;
    Integer transformedAtProcessorIndex; //transformed at index in processor list ; null if source transformation
    Map<Integer, TransformablePluginModel> transformablePluginModelMap; // key:pluginId , val: pluginDetails ; source id = -1 ; processor id = 0 ...n

    public PipelineTransformationModel() {

    }
}
