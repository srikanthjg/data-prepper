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

}