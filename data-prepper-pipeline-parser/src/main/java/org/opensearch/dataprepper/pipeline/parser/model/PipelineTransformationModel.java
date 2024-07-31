package org.opensearch.dataprepper.pipeline.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(setterPrefix = "with")
@Getter
@AllArgsConstructor
public class PipelineTransformationModel {
    String pipelineName;
    String pipelineJson;
    List<TransformablePluginModel> transformablePluginModel;
}
