/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.plugins.processor.awstranslate;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AwsTranslateProcessorConfig {

    @JsonProperty("aws")
    private AwsAuthenticationOptions aws;

    @JsonProperty("source_language")
    private String sourceLanguage;

    @JsonProperty("target_language")
    private String targetLanguage;

    public AwsAuthenticationOptions getAws() {
        return aws;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }
}