package org.opensearch.dataprepper.pipeline.parser.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder(setterPrefix = "with")
@Getter
@AllArgsConstructor
public class TransformablePluginModel {
    private String pluginName;
    private JsonNode originalConfig;
    private String s3Bucket;
    private Integer nextNode; //specific to processor but can be extended to source and sink based on id
}
