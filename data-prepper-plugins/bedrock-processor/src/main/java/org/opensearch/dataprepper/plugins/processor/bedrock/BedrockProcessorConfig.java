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

    public String getModelId() {
        return modelId;
    }
}
