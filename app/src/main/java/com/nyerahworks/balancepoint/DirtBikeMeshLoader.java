package com.nyerahworks.balancepoint;

import android.util.Base64;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

/**
 * Loads the compact BPQ1 dirt-bike mesh generated from DirtBike.blend.
 *
 * The source model is split into body, engine, front wheel and rear wheel. Positions
 * are uint16-quantized, normals use two-byte octahedral encoding and triangle indices
 * are delta/zig-zag varints. Keeping this tiny decoder in-app avoids a heavyweight
 * model-loader dependency on the low-end GMEE target.
 */
final class DirtBikeMeshLoader {
    private static final int CHUNK_COUNT = 8;
    private static final int MAGIC = 0x42505131; // "BPQ1"

    private DirtBikeMeshLoader() {}

    static ModelInstance[] load(Array<Model> ownedModels,
                                Material bodyMaterial,
                                Material engineMaterial,
                                Material wheelMaterial) throws IOException {
        StringBuilder encoded = new StringBuilder(112000);
        for (int i = 0; i < CHUNK_COUNT; i++) {
            encoded.append(Gdx.files.internal("models/dirtbike_qmesh_" + i + ".txt").readString());
        }

        byte[] compressed = Base64.decode(encoded.toString(), Base64.DEFAULT);
        byte[] unpacked = inflate(compressed);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(unpacked));

        if (in.readInt() != MAGIC) throw new IOException("Bad dirtbike mesh magic");
        int meshCount = in.readUnsignedByte();
        if (meshCount != 4) throw new IOException("Expected 4 dirtbike meshes, got " + meshCount);

        Material[] materials = {bodyMaterial, engineMaterial, wheelMaterial, wheelMaterial};
        ModelInstance[] result = new ModelInstance[meshCount];
        for (int meshIndex = 0; meshIndex < meshCount; meshIndex++) {
            int vertexCount = in.readUnsignedShort();
            int indexCount = in.readInt();
            if (vertexCount <= 0 || vertexCount > 32767 || indexCount <= 0) {
                throw new IOException("Invalid dirtbike mesh dimensions");
            }

            float minX = in.readFloat();
            float minY = in.readFloat();
            float minZ = in.readFloat();
            float maxX = in.readFloat();
            float maxY = in.readFloat();
            float maxZ = in.readFloat();

            float[] vertices = new float[vertexCount * 6];
            float rangeX = maxX - minX;
            float rangeY = maxY - minY;
            float rangeZ = maxZ - minZ;

            // Positions are grouped before normals in BPQ1.
            for (int i = 0; i < vertexCount; i++) {
                int base = i * 6;
                vertices[base] = minX + (in.readUnsignedShort() / 65535f) * rangeX;
                vertices[base + 1] = minY + (in.readUnsignedShort() / 65535f) * rangeY;
                vertices[base + 2] = minZ + (in.readUnsignedShort() / 65535f) * rangeZ;
            }

            for (int i = 0; i < vertexCount; i++) {
                int base = i * 6;
                float nx = in.readByte() / 127f;
                float ny = in.readByte() / 127f;
                float nz = 1f - Math.abs(nx) - Math.abs(ny);
                if (nz < 0f) {
                    float oldX = nx;
                    nx = (1f - Math.abs(ny)) * signNotZero(oldX);
                    ny = (1f - Math.abs(oldX)) * signNotZero(ny);
                }
                float invLen = 1f / (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
                vertices[base + 3] = nx * invLen;
                vertices[base + 4] = ny * invLen;
                vertices[base + 5] = nz * invLen;
            }

            short[] indices = new short[indexCount];
            int previous = 0;
            for (int i = 0; i < indexCount; i++) {
                int packed = readVarUInt(in);
                int delta = (packed >>> 1) ^ -(packed & 1);
                previous += delta;
                if (previous < 0 || previous >= vertexCount) {
                    throw new IOException("Dirtbike index out of range");
                }
                indices[i] = (short)previous;
            }

            Mesh mesh = new Mesh(true, vertexCount, indexCount,
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                    new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"));
            mesh.setVertices(vertices);
            mesh.setIndices(indices);

            ModelBuilder builder = new ModelBuilder();
            builder.begin();
            builder.part("dirtbike-" + meshIndex, mesh, GL20.GL_TRIANGLES, materials[meshIndex]);
            Model model = builder.end();
            ownedModels.add(model);
            result[meshIndex] = new ModelInstance(model);
        }
        return result;
    }

    private static byte[] inflate(byte[] compressed) throws IOException {
        InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream out = new ByteArrayOutputStream(128000);
        byte[] buffer = new byte[4096];
        int count;
        while ((count = inflater.read(buffer)) >= 0) {
            if (count > 0) out.write(buffer, 0, count);
        }
        inflater.close();
        return out.toByteArray();
    }

    private static int readVarUInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (shift < 35) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IOException("Malformed dirtbike varint");
    }

    private static float signNotZero(float v) {
        return v >= 0f ? 1f : -1f;
    }
}
