package com.jobdata.ai.common;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingDimensionValidator implements ApplicationRunner {

    private final EmbeddingModel embeddingModel;
    private final PgVectorStoreProperties pgVectorStoreProperties;
    private final boolean enabled;

    public EmbeddingDimensionValidator(
            EmbeddingModel embeddingModel,
            PgVectorStoreProperties pgVectorStoreProperties,
            @Value("${jobdata.ai.embedding.validate-on-startup:true}") boolean enabled
    ) {
        this.embeddingModel = embeddingModel;
        this.pgVectorStoreProperties = pgVectorStoreProperties;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int expected = pgVectorStoreProperties.getDimensions();
        if (expected <= 0) {
            return;
        }
        float[] v = embeddingModel.embed("dimension_check");
        int actual = v == null ? 0 : v.length;
        if (actual != expected) {
            throw new IllegalStateException("Embedding 维度不匹配：pgvector.dimensions=" + expected + "，但模型输出维度=" + actual + "。请调整 AI_EMBEDDING_DIM 或重建 vector_store 表。");
        }
    }
}

