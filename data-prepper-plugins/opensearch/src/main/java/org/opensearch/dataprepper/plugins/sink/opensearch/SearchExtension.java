package org.opensearch.dataprepper.plugins.sink.opensearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Directory user summary entity. */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SearchExtension {
    private String answer;
}