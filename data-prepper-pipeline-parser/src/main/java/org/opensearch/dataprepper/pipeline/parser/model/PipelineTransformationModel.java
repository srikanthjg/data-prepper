package org.opensearch.dataprepper.pipeline.parser.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder(setterPrefix = "with")
@Getter
@Setter
@AllArgsConstructor
public class PipelineTransformationModel {
    String pipelineName;
    String pipelineJson;
    List<JsonNode> originalPipelineProcessorList; //processorJson


    Boolean isTranformationNeeded;
    Boolean isProcessorTransformation;
    Boolean isSourceTransformation;
    Integer transformedAtProcessorIndex; //transformed at index in processor list

    List<TransformablePluginModel> transformablePluginModel;
}
