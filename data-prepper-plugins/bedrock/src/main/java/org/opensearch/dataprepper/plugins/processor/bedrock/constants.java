package org.opensearch.dataprepper.plugins.processor.bedrock;

public class constants {
    //Embeddings
    static final String TITAN_TEXT_EMBEDDING_V1 = "amazon.titan-embed-text-v1";
    static final String TITAN_IMAGE_EMBEDDING_V1 = "amazon.titan-embed-image-v1";
    static final String TITAN_TEXT_EMBEDDING_V2 = "amazon.titan-embed-text-v2:0";

    //LLM
    static final String CLAUDE_V2 = "anthropic.claude-v2";
    static final String JURASSIC2 = "ai21.j2-mid-v1";

    //Image
    static final String STABLE_DIFFUSION = "stability.stable-diffusion-xl";
    static final String TITAN_IMAGE = "amazon.titan-image-generator-v1";

    static final String SYNC_MODE = "sync";
    static final String ASYNC_MODE = "async";
}
