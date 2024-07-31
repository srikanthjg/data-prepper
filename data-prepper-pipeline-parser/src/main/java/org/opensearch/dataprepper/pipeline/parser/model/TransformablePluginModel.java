package org.opensearch.dataprepper.pipeline.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.opensearch.dataprepper.model.configuration.PipelineModel;

@Builder(setterPrefix = "with")
@Getter
@AllArgsConstructor
public class TransformablePluginModel {
    private String pluginName;
    private String pipelineName;
    private String s3Bucket;
    private int index;
    private int nextNode;
//    private String ruleFile;
//    private String tranformationFile;
    private PipelineModel transformedModel;
}
