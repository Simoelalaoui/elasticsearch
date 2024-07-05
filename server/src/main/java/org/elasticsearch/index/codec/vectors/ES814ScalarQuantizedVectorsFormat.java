/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.codec.vectors;

import org.apache.lucene.codecs.KnnFieldVectorsWriter;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.codecs.hnsw.FlatFieldVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorsFormat;
import org.apache.lucene.codecs.hnsw.FlatVectorsReader;
import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.codecs.hnsw.FlatVectorsWriter;
import org.apache.lucene.codecs.hnsw.ScalarQuantizedVectorScorer;
import org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorsReader;
import org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorsWriter;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Sorter;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.util.hnsw.CloseableRandomVectorScorerSupplier;
import org.apache.lucene.util.hnsw.RandomAccessVectorValues;
import org.apache.lucene.util.hnsw.RandomVectorScorer;
import org.apache.lucene.util.hnsw.RandomVectorScorerSupplier;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues;
import org.apache.lucene.util.quantization.QuantizedVectorsReader;
import org.apache.lucene.util.quantization.RandomAccessQuantizedByteVectorValues;
import org.apache.lucene.util.quantization.ScalarQuantizer;
import org.elasticsearch.vec.VectorScorerFactory;
import org.elasticsearch.vec.VectorSimilarityType;

import java.io.IOException;

public class ES814ScalarQuantizedVectorsFormat extends FlatVectorsFormat {

    static final String NAME = "ES814ScalarQuantizedVectorsFormat";

    private static final FlatVectorsFormat rawVectorFormat = new Lucene99FlatVectorsFormat(DefaultFlatVectorScorer.INSTANCE);

    /** The minimum confidence interval */
    private static final float MINIMUM_CONFIDENCE_INTERVAL = 0.9f;

    /** The maximum confidence interval */
    private static final float MAXIMUM_CONFIDENCE_INTERVAL = 1f;

    /**
     * Controls the confidence interval used to scalar quantize the vectors the default value is
     * calculated as `1-1/(vector_dimensions + 1)`
     */
    public final Float confidenceInterval;
    final FlatVectorsScorer flatVectorScorer;

    public ES814ScalarQuantizedVectorsFormat(Float confidenceInterval) {
        super(NAME);
        if (confidenceInterval != null
            && (confidenceInterval < MINIMUM_CONFIDENCE_INTERVAL || confidenceInterval > MAXIMUM_CONFIDENCE_INTERVAL)) {
            throw new IllegalArgumentException(
                "confidenceInterval must be between "
                    + MINIMUM_CONFIDENCE_INTERVAL
                    + " and "
                    + MAXIMUM_CONFIDENCE_INTERVAL
                    + "; confidenceInterval="
                    + confidenceInterval
            );
        }
        this.confidenceInterval = confidenceInterval;
        this.flatVectorScorer = new ESFlatVectorsScorer(new ScalarQuantizedVectorScorer(DefaultFlatVectorScorer.INSTANCE));
    }

    @Override
    public String toString() {
        return NAME + "(name=" + NAME + ", confidenceInterval=" + confidenceInterval + ", rawVectorFormat=" + rawVectorFormat + ")";
    }

    @Override
    public FlatVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
        return new ES814ScalarQuantizedVectorsWriter(
            new Lucene99ScalarQuantizedVectorsWriter(state, confidenceInterval, rawVectorFormat.fieldsWriter(state), flatVectorScorer)
        );
    }

    @Override
    public FlatVectorsReader fieldsReader(SegmentReadState state) throws IOException {
        return new ES814ScalarQuantizedVectorsReader(
            new Lucene99ScalarQuantizedVectorsReader(state, rawVectorFormat.fieldsReader(state), flatVectorScorer)
        );
    }

    static final class ES814ScalarQuantizedVectorsWriter extends FlatVectorsWriter {

        final Lucene99ScalarQuantizedVectorsWriter delegate;

        ES814ScalarQuantizedVectorsWriter(Lucene99ScalarQuantizedVectorsWriter delegate) {
            super(delegate.getFlatVectorScorer());
            this.delegate = delegate;
        }

        @Override
        public FlatFieldVectorsWriter<?> addField(FieldInfo fieldInfo, KnnFieldVectorsWriter<?> knnFieldVectorsWriter) throws IOException {
            return delegate.addField(fieldInfo, knnFieldVectorsWriter);
        }

        @Override
        public void mergeOneField(FieldInfo fieldInfo, MergeState mergeState) throws IOException {
            delegate.mergeOneField(fieldInfo, mergeState);
        }

        @Override
        public CloseableRandomVectorScorerSupplier mergeOneFieldToIndex(FieldInfo fieldInfo, MergeState mergeState) throws IOException {
            return delegate.mergeOneFieldToIndex(fieldInfo, mergeState);
        }

        @Override
        public void finish() throws IOException {
            delegate.finish();
        }

        @Override
        public void flush(int i, Sorter.DocMap docMap) throws IOException {
            delegate.flush(i, docMap);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long ramBytesUsed() {
            return delegate.ramBytesUsed();
        }
    }

    static final class ES814ScalarQuantizedVectorsReader extends FlatVectorsReader implements QuantizedVectorsReader {

        final Lucene99ScalarQuantizedVectorsReader delegate;

        ES814ScalarQuantizedVectorsReader(Lucene99ScalarQuantizedVectorsReader delegate) {
            super(delegate.getFlatVectorScorer());
            this.delegate = delegate;
        }

        @Override
        public RandomVectorScorer getRandomVectorScorer(String field, float[] target) throws IOException {
            return delegate.getRandomVectorScorer(field, target);
        }

        @Override
        public RandomVectorScorer getRandomVectorScorer(String field, byte[] target) throws IOException {
            return delegate.getRandomVectorScorer(field, target);
        }

        @Override
        public void checkIntegrity() throws IOException {
            delegate.checkIntegrity();
        }

        @Override
        public FloatVectorValues getFloatVectorValues(String field) throws IOException {
            return delegate.getFloatVectorValues(field);
        }

        @Override
        public ByteVectorValues getByteVectorValues(String field) throws IOException {
            return delegate.getByteVectorValues(field);
        }

        @Override
        public QuantizedByteVectorValues getQuantizedVectorValues(String fieldName) throws IOException {
            return delegate.getQuantizedVectorValues(fieldName);
        }

        @Override
        public ScalarQuantizer getQuantizationState(String fieldName) {
            return delegate.getQuantizationState(fieldName);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long ramBytesUsed() {
            return delegate.ramBytesUsed();
        }
    }

    static final class ESFlatVectorsScorer implements FlatVectorsScorer {

        final FlatVectorsScorer delegate;
        final VectorScorerFactory factory;

        ESFlatVectorsScorer(FlatVectorsScorer delegte) {
            this.delegate = delegte;
            factory = VectorScorerFactory.instance().orElse(null);
        }

        @Override
        public RandomVectorScorerSupplier getRandomVectorScorerSupplier(VectorSimilarityFunction sim, RandomAccessVectorValues values)
            throws IOException {
            if (values instanceof RandomAccessQuantizedByteVectorValues qValues && values.getSlice() != null) {
                if (factory != null) {
                    var scorer = factory.getInt7SQVectorScorerSupplier(
                        VectorSimilarityType.of(sim),
                        values.getSlice(),
                        qValues,
                        qValues.getScalarQuantizer().getConstantMultiplier()
                    );
                    if (scorer.isPresent()) {
                        return scorer.get();
                    }
                }
            }
            return delegate.getRandomVectorScorerSupplier(sim, values);
        }

        @Override
        public RandomVectorScorer getRandomVectorScorer(VectorSimilarityFunction sim, RandomAccessVectorValues values, float[] query)
            throws IOException {
            if (values instanceof RandomAccessQuantizedByteVectorValues qValues && values.getSlice() != null) {
                if (factory != null) {
                    var scorer = factory.getInt7SQVectorScorer(sim, qValues, query);
                    if (scorer.isPresent()) {
                        return scorer.get();
                    }
                }
            }
            return delegate.getRandomVectorScorer(sim, values, query);
        }

        @Override
        public RandomVectorScorer getRandomVectorScorer(VectorSimilarityFunction sim, RandomAccessVectorValues values, byte[] query)
            throws IOException {
            return delegate.getRandomVectorScorer(sim, values, query);
        }
    }
}
