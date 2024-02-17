package com.common.samplerender;

import android.opengl.GLES30;
import java.io.Closeable;
import java.nio.FloatBuffer;

public class VertexBuffer implements Closeable {
    private final GpuBuffer buffer;
    private final int numberOfEntriesPerVertex;

    public VertexBuffer(SampleRender render, int numberOfEntriesPerVertex, FloatBuffer entries) {
        if (entries != null && entries.limit() % numberOfEntriesPerVertex != 0) {
            throw new IllegalArgumentException(
                    "If non-null, vertex buffer data must be divisible by the number of data points per vertex");
        }

        this.numberOfEntriesPerVertex = numberOfEntriesPerVertex;

        if (entries != null) {
            // Apply scaling to the vertex data if needed
            // for (int i = 0; i < entries.limit(); i++) {
            //     entries.put(i, entries.get(i) * scale);
            // }
        }

        buffer = new GpuBuffer(GLES30.GL_ARRAY_BUFFER, GpuBuffer.FLOAT_SIZE, entries);
    }

    public void set(FloatBuffer entries) {
        if (entries != null && entries.limit() % numberOfEntriesPerVertex != 0) {
            throw new IllegalArgumentException(
                    "If non-null, vertex buffer data must be divisible by the number of data points per vertex");
        }
        buffer.set(entries);
    }

    @Override
    public void close() {
        buffer.free();
    }

    /* package-private */
    public int getBufferId() {
        return buffer.getBufferId();
    }

    /* package-private */
    public int getNumberOfEntriesPerVertex() {
        return numberOfEntriesPerVertex;
    }

    /* package-private */
    int getNumberOfVertices() {
        return buffer.getSize() / numberOfEntriesPerVertex;
    }

    public float[] getVertexData() {
        FloatBuffer floatBuffer = buffer.mapRead();
        if (floatBuffer == null) {
            // Handle the case where mapping failed
            return new float[0]; // Or take appropriate action
        }

        try {
            float[] vertices = new float[floatBuffer.remaining()];
            floatBuffer.get(vertices);
            return vertices;
        } finally {
            buffer.unmap();
        }
    }

    public void setVertexData(float[] vertices) {
        FloatBuffer floatBuffer = FloatBuffer.wrap(vertices);
        buffer.set(floatBuffer);
    }
}