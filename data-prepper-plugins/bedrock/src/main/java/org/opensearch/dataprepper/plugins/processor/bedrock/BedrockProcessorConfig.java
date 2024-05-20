/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.bedrock;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class BedrockProcessorConfig {

    @NotNull
    @JsonProperty("modelId")
    private String modelId;

    @JsonProperty("seed")
    private long seed;

    @JsonProperty("temperature")
    private String temperature;

    @JsonProperty("max_tokens_to_sample")
    private String max_tokens_to_sample;

    @JsonProperty("style_preset")
    private String style_preset;

    @JsonProperty("aws")
    private AwsAuthenticationOptions aws;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("s3_input_uri")
    private String s3InputUri;

    @JsonProperty("s3_output_uri")
    private String s3OutputUri;

    @JsonProperty("job_name")
    private String jobName;

    @JsonProperty("url")
    private String url;

    public String getModelId() {
        return modelId;
    }

    public long getSeed() {
        return seed;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getMax_tokens_to_sample() {
        return max_tokens_to_sample;
    }

    public String getStyle_preset() {
        return style_preset;
    }

    public String getMode() {
        return mode;
    }

    public AwsAuthenticationOptions getAws() {
        return aws;
    }

    public String getS3InputUri() {
        return s3InputUri;
    }

    public String getS3OutputUri() {
        return s3OutputUri;
    }

    public String getJobName() {
        return jobName;
    }

    public String getUrl() {
        return url;
    }
}