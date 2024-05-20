package org.opensearch.dataprepper.plugins.processor.bedrock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opensearch.dataprepper.model.configuration.PipelineModel;
import org.opensearch.dataprepper.model.event.Event;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.CLAUDE_V2;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.JURASSIC2;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.STABLE_DIFFUSION;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.TITAN_IMAGE;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.TITAN_IMAGE_EMBEDDING_V1;
import static org.opensearch.dataprepper.plugins.processor.bedrock.ModelNames.TITAN_TEXT_EMBEDDING_V1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;


public class InvokeModel {

    private static final Random random = new Random();
    private static final long seed = random.nextLong() & 0xFFFFFFFFL;
    private static final Logger LOG = LoggerFactory.getLogger(InvokeModel.class);

    /**
     * Invokes the Anthropic Claude 2 model to run an inference based on the
     * provided input.
     *
     * @param recordEvent
     */
    public static void invokeClaude(Event recordEvent) {
        /*
         * The different model providers have individual request and response formats.
         * For the format, ranges, and default values for Anthropic Claude, refer to:
         * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html
         */

        String claudeModelId = CLAUDE_V2;

        // Claude requires you to enclose the prompt as follows:
        String question = recordEvent.getMetadata().getAttribute("question").toString();

        String promptTemplate = String.format(
                "Human: You are a helpful assistant.  Generate a concise and informative answer in less than 100 words, %s " +
                "Question: %s " +
                "Assistant:",
                recordEvent.toJsonString(), question);

        BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.US_EAST_1).build();

        String payload = new JSONObject().put("prompt", promptTemplate).put("max_tokens_to_sample", 200).put("temperature", 0.5).toString();

        InvokeModelRequest request = InvokeModelRequest.builder().body(SdkBytes.fromUtf8String(payload)).modelId(claudeModelId).contentType("application/json").accept("application/json").build();

        InvokeModelResponse response = client.invokeModel(request);

        JSONObject responseBody = new JSONObject(response.body().asUtf8String());

        String generatedText = responseBody.getString("completion");

        recordEvent.put(CLAUDE_V2 + "_response", generatedText);
    }

    /**
     * Invokes the AI21 Labs Jurassic-2 model to run an inference based on the
     * provided input.
     *
     * @param recordEvent
     */
    public static void invokeJurassic2(Event recordEvent) {
        /*
         * The different model providers have individual request and response formats.
         * For the format, ranges, and default values for AI21 Labs Jurassic-2, refer
         * to:
         * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-jurassic2.html
         */

        String jurassic2ModelId = JURASSIC2;

        BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.US_EAST_1).build();

        String payload = new JSONObject().put("prompt", recordEvent.toJsonString()).put("temperature", 0.5).put("maxTokens", 200).toString();

        InvokeModelRequest request = InvokeModelRequest.builder().body(SdkBytes.fromUtf8String(payload)).modelId(jurassic2ModelId).contentType("application/json").accept("application/json").build();

        InvokeModelResponse response = client.invokeModel(request);

        JSONObject responseBody = new JSONObject(response.body().asUtf8String());

        String generatedText = responseBody.getJSONArray("completions").getJSONObject(0).getJSONObject("data").getString("text");

        recordEvent.put("generatedText", generatedText);
    }

    /**
     * Invokes the Stability.ai Stable Diffusion XL model to create an image based
     * on the provided input.
     *
     * @param recordEvent
     */
    public static void invokeStableDiffusion(Event recordEvent) {
        /*
         * The different model providers have individual request and response formats.
         * For the format, ranges, and available style_presets of Stable Diffusion
         * ModelNames refer to:
         * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-stability-diffusion.html
         */

        String stableDiffusionModelId = STABLE_DIFFUSION;
        String stylePreset = null;

        BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.US_EAST_1).credentialsProvider(ProfileCredentialsProvider.create()).build();

        JSONArray wrappedPrompt = new JSONArray().put(new JSONObject().put("text", recordEvent.toJsonString()));

        JSONObject payload = new JSONObject().put("text_prompts", wrappedPrompt).put("seed", seed);

        if (!(stylePreset == null || stylePreset.isEmpty())) {
            payload.put("style_preset", stylePreset);
        }

        InvokeModelRequest request = InvokeModelRequest.builder().body(SdkBytes.fromUtf8String(payload.toString())).modelId(stableDiffusionModelId).contentType("application/json").accept("application/json").build();

        InvokeModelResponse response = client.invokeModel(request);

        JSONObject responseBody = new JSONObject(response.body().asUtf8String());

        String base64ImageData = responseBody.getJSONArray("artifacts").getJSONObject(0).getString("base64");

        recordEvent.put("base64ImageData", base64ImageData);
    }

    /**
     * Invokes the Amazon Titan image generation model to create an image using the
     * input
     * provided in the request body.
     *
     * @param recordEvent
     */
    public static void invokeTitanImage(Event recordEvent) {
        /*
         * The different model providers have individual request and response formats.
         * For the format, ranges, and default values for Titan Image ModelNames refer to:
         * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-
         * image.html
         */
        String titanImageModelId = TITAN_IMAGE;

        BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.US_EAST_1).credentialsProvider(ProfileCredentialsProvider.create()).build();

        var textToImageParams = new JSONObject().put("text", recordEvent.toJsonString());

        var imageGenerationConfig = new JSONObject().put("numberOfImages", 1).put("quality", "standard").put("cfgScale", 8.0).put("height", 512).put("width", 512).put("seed", seed);

        JSONObject payload = new JSONObject().put("taskType", "TEXT_IMAGE").put("textToImageParams", textToImageParams).put("imageGenerationConfig", imageGenerationConfig);

        InvokeModelRequest request = InvokeModelRequest.builder().body(SdkBytes.fromUtf8String(payload.toString())).modelId(titanImageModelId).contentType("application/json").accept("application/json").build();

        InvokeModelResponse response = client.invokeModel(request);

        JSONObject responseBody = new JSONObject(response.body().asUtf8String());

        String base64ImageData = responseBody.getJSONArray("images").getString(0);

        recordEvent.put("base64ImageData", base64ImageData);
    }

    public static void invokeTitanTextEmbed(Event recordEvent) {
        final String modelId = TITAN_TEXT_EMBEDDING_V1;

        // Create a Bedrock Runtime client in the AWS Region of your choice.
        BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.US_EAST_1).build();

        // Create a JSON payload using the model's native structure.
        JSONObject request = new JSONObject().put("inputText", recordEvent.toJsonString());

        // Encode and send the request.
        InvokeModelResponse response = client.invokeModel(req -> req.body(SdkBytes.fromUtf8String(request.toString())).modelId(modelId));

        // Decode the model's native response body.
        JSONObject nativeResponse = new JSONObject(response.body().asUtf8String());

        // Extract and print the generated embedding.
        JSONArray embeddingsJsonArray = nativeResponse.getJSONArray("embedding");
        float[] embeddings = new float[embeddingsJsonArray.length()];
        for (int i = 0; i < embeddingsJsonArray.length(); i++) {
            embeddings[i] = embeddingsJsonArray.getFloat(i);
        }

        recordEvent.put("embeddings", embeddings);
    }

    public static void invokeTitanImageEmbed(Event recordEvent) {
        final String modelId = TITAN_IMAGE_EMBEDDING_V1;

        // Create a Bedrock Runtime client in the AWS Region of your choice.
        BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.US_EAST_1).build();

        //TODO
        JSONObject request = new JSONObject().put("inputText", recordEvent.toJsonString());
        String imageBytes = recordEvent.get("imageBytes", String.class);

        // Encode and send the request.
        InvokeModelResponse response = client.invokeModel(req -> req.body(SdkBytes.fromUtf8String(request.toString())).modelId(modelId));

        // Decode the model's native response body.
        JSONObject nativeResponse = new JSONObject(response.body().asUtf8String());

        // Extract and print the generated embedding.
        JSONArray embeddingsJsonArray = nativeResponse.getJSONArray("embedding");
        float[] embeddings = new float[embeddingsJsonArray.length()];
        for (int i = 0; i < embeddingsJsonArray.length(); i++) {
            embeddings[i] = embeddingsJsonArray.getFloat(i);
        }

        recordEvent.put("image_embeddings", embeddings);
    }
}