package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal GLB 2.0 loader for the shipped DirtBike.glb asset.
 *
 * The file itself stays standard glTF/GLB. This reader only implements the subset
 * Balance Point currently needs: indexed triangle meshes with float VEC3 positions
 * and optional normalized UBYTE VEC4 vertex colors. Normals are generated once at
 * load time so the result can be rendered by the normal libGDX ModelBatch on both
 * Android and RoboVM/iOS without another runtime rendering stack.
 */
final class DirtBikeMeshLoader {
    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;
    private static final int GLB_VERSION = 2;

    // The physics model is intentionally unchanged. The source dirt-bike tires are
    // visually normalized to the existing 0.31 m physics radius at import time.
    private static final float TARGET_WHEEL_RADIUS = 0.31f;

    // The exported source geometry is referenced to the rear axle and needs this
    // small chassis-only lift to sit correctly on the existing physics axle line.
    private static final float CHASSIS_Y_OFFSET = 0.28f;

    private DirtBikeMeshLoader() {}

    static ModelInstance[] load(Array<Model> ownedModels,
                                Material bodyMaterial,
                                Material engineMaterial,
                                Material wheelMaterial) throws IOException {
        byte[] bytes = Gdx.files.internal("models/DirtBike.glb").readBytes();
        ParsedGlb glb = parseGlb(bytes);

        JsonValue meshes = glb.json.get("meshes");
        if (meshes == null || meshes.size == 0) {
            throw new IOException("DirtBike.glb contains no meshes");
        }

        Array<MeshData> bodyParts = new Array<>();
        Array<MeshData> engineParts = new Array<>();
        Array<MeshData> frontWheelParts = new Array<>();
        Array<MeshData> rearWheelParts = new Array<>();

        for (int i = 0; i < meshes.size; i++) {
            JsonValue meshJson = meshes.get(i);
            String name = meshJson.getString("name", "mesh-" + i);
            JsonValue primitives = meshJson.get("primitives");
            if (primitives == null || primitives.size != 1) {
                throw new IOException("Expected one primitive for " + name);
            }

            JsonValue primitive = primitives.get(0);
            if (primitive.getInt("mode", GL20.GL_TRIANGLES) != GL20.GL_TRIANGLES) {
                throw new IOException("DirtBike mesh is not triangles: " + name);
            }

            JsonValue attributes = primitive.get("attributes");
            if (attributes == null || !attributes.has("POSITION") || !primitive.has("indices")) {
                throw new IOException("DirtBike mesh is missing POSITION/indices: " + name);
            }

            float[] positions = readPositions(glb, attributes.getInt("POSITION"));
            short[] indices = readIndices(glb, primitive.getInt("indices"), positions.length / 3);

            Material fallback;
            if ("Engine_GEO".equals(name)) fallback = engineMaterial;
            else if (name.contains("Wheel")) fallback = wheelMaterial;
            else fallback = bodyMaterial;

            Color color = attributes.has("COLOR_0")
                    ? readFirstColor(glb, attributes.getInt("COLOR_0"))
                    : diffuseColor(fallback);

            if ("FrontWheel_GEO".equals(name) || "RearWheel_GEO".equals(name)) {
                normalizeWheelRadius(positions);
            } else {
                for (int v = 0; v < positions.length; v += 3) {
                    positions[v + 1] += CHASSIS_Y_OFFSET;
                }
            }

            MeshData part = new MeshData(name, positions, indices, color);
            if ("FrontWheel_GEO".equals(name)) {
                frontWheelParts.add(part);
            } else if ("RearWheel_GEO".equals(name)) {
                rearWheelParts.add(part);
            } else if ("Engine_GEO".equals(name)) {
                engineParts.add(part);
            } else {
                // Body, fork/shock, handlebar, chain and levers all arrive in their
                // exported bike-local positions and can share the chassis transform.
                bodyParts.add(part);
            }
        }

        if (bodyParts.size == 0 || engineParts.size == 0
                || frontWheelParts.size == 0 || rearWheelParts.size == 0) {
            throw new IOException("DirtBike.glb is missing one of the required bike groups");
        }

        Model body = buildModel(bodyParts);
        Model engine = buildModel(engineParts);
        Model front = buildModel(frontWheelParts);
        Model rear = buildModel(rearWheelParts);
        ownedModels.add(body);
        ownedModels.add(engine);
        ownedModels.add(front);
        ownedModels.add(rear);

        return new ModelInstance[] {
                new ModelInstance(body),
                new ModelInstance(engine),
                new ModelInstance(front),
                new ModelInstance(rear)
        };
    }

    private static ParsedGlb parseGlb(byte[] bytes) throws IOException {
        if (bytes.length < 20) throw new IOException("DirtBike.glb is too small");

        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = in.getInt();
        int version = in.getInt();
        int declaredLength = in.getInt();
        if (magic != GLB_MAGIC) throw new IOException("Bad DirtBike GLB magic");
        if (version != GLB_VERSION) throw new IOException("Unsupported DirtBike GLB version: " + version);
        if (declaredLength != bytes.length) {
            throw new IOException("Truncated DirtBike GLB: header says " + declaredLength
                    + " bytes, file has " + bytes.length);
        }

        String jsonText = null;
        byte[] binary = null;
        while (in.remaining() >= 8) {
            int chunkLength = in.getInt();
            int chunkType = in.getInt();
            if (chunkLength < 0 || chunkLength > in.remaining()) {
                throw new IOException("Invalid DirtBike GLB chunk length");
            }
            byte[] chunk = new byte[chunkLength];
            in.get(chunk);
            if (chunkType == JSON_CHUNK) {
                jsonText = new String(chunk, StandardCharsets.UTF_8).trim();
            } else if (chunkType == BIN_CHUNK) {
                binary = chunk;
            }
        }

        if (jsonText == null || binary == null) {
            throw new IOException("DirtBike.glb is missing JSON or BIN chunk");
        }

        JsonValue json = new JsonReader().parse(jsonText);
        JsonValue asset = json.get("asset");
        if (asset == null || !asset.getString("version", "").startsWith("2")) {
            throw new IOException("DirtBike.glb is not glTF 2.x");
        }
        return new ParsedGlb(json, binary);
    }

    private static float[] readPositions(ParsedGlb glb, int accessorIndex) throws IOException {
        JsonValue accessor = getArrayItem(glb.json, "accessors", accessorIndex);
        if (accessor.getInt("componentType") != 5126 || !"VEC3".equals(accessor.getString("type"))) {
            throw new IOException("DirtBike POSITION accessor must be FLOAT VEC3");
        }

        int count = accessor.getInt("count");
        AccessView view = accessView(glb, accessor, 12);
        float[] positions = new float[count * 3];
        ByteBuffer bin = ByteBuffer.wrap(glb.binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int p = view.offset + i * view.stride;
            requireRange(p, 12, glb.binary.length);
            positions[i * 3] = bin.getFloat(p);
            positions[i * 3 + 1] = bin.getFloat(p + 4);
            positions[i * 3 + 2] = bin.getFloat(p + 8);
        }
        return positions;
    }

    private static short[] readIndices(ParsedGlb glb, int accessorIndex, int vertexCount)
            throws IOException {
        JsonValue accessor = getArrayItem(glb.json, "accessors", accessorIndex);
        if (!"SCALAR".equals(accessor.getString("type"))) {
            throw new IOException("DirtBike index accessor must be SCALAR");
        }

        int componentType = accessor.getInt("componentType");
        int componentSize;
        if (componentType == 5121) componentSize = 1;
        else if (componentType == 5123) componentSize = 2;
        else if (componentType == 5125) componentSize = 4;
        else throw new IOException("Unsupported DirtBike index component type: " + componentType);

        int count = accessor.getInt("count");
        if (count <= 0 || (count % 3) != 0) throw new IOException("Invalid DirtBike index count");
        AccessView view = accessView(glb, accessor, componentSize);
        ByteBuffer bin = ByteBuffer.wrap(glb.binary).order(ByteOrder.LITTLE_ENDIAN);
        short[] result = new short[count];

        for (int i = 0; i < count; i++) {
            int p = view.offset + i * view.stride;
            requireRange(p, componentSize, glb.binary.length);
            long value;
            if (componentType == 5121) value = bin.get(p) & 0xffL;
            else if (componentType == 5123) value = bin.getShort(p) & 0xffffL;
            else value = bin.getInt(p) & 0xffffffffL;

            if (value >= vertexCount || value > 32767L) {
                throw new IOException("DirtBike index out of range: " + value);
            }
            result[i] = (short)value;
        }
        return result;
    }

    private static Color readFirstColor(ParsedGlb glb, int accessorIndex) throws IOException {
        JsonValue accessor = getArrayItem(glb.json, "accessors", accessorIndex);
        String type = accessor.getString("type");
        int components = "VEC4".equals(type) ? 4 : ("VEC3".equals(type) ? 3 : 0);
        if (components == 0) throw new IOException("Unsupported DirtBike COLOR_0 type: " + type);

        int componentType = accessor.getInt("componentType");
        int componentSize;
        if (componentType == 5121) componentSize = 1;
        else if (componentType == 5123) componentSize = 2;
        else if (componentType == 5126) componentSize = 4;
        else throw new IOException("Unsupported DirtBike color component type: " + componentType);

        AccessView view = accessView(glb, accessor, componentSize * components);
        requireRange(view.offset, componentSize * components, glb.binary.length);
        ByteBuffer bin = ByteBuffer.wrap(glb.binary).order(ByteOrder.LITTLE_ENDIAN);
        float[] c = {1f, 1f, 1f, 1f};
        for (int i = 0; i < components; i++) {
            int p = view.offset + i * componentSize;
            if (componentType == 5121) c[i] = (bin.get(p) & 0xff) / 255f;
            else if (componentType == 5123) c[i] = (bin.getShort(p) & 0xffff) / 65535f;
            else c[i] = bin.getFloat(p);
        }
        return new Color(c[0], c[1], c[2], c[3]);
    }

    private static AccessView accessView(ParsedGlb glb, JsonValue accessor, int elementSize)
            throws IOException {
        int viewIndex = accessor.getInt("bufferView");
        JsonValue view = getArrayItem(glb.json, "bufferViews", viewIndex);
        if (view.getInt("buffer", 0) != 0) throw new IOException("DirtBike GLB uses external buffer");
        int offset = view.getInt("byteOffset", 0) + accessor.getInt("byteOffset", 0);
        int stride = view.getInt("byteStride", elementSize);
        if (stride < elementSize) throw new IOException("Invalid DirtBike buffer stride");
        return new AccessView(offset, stride);
    }

    private static JsonValue getArrayItem(JsonValue root, String name, int index) throws IOException {
        JsonValue array = root.get(name);
        if (array == null || index < 0 || index >= array.size) {
            throw new IOException("Invalid DirtBike " + name + " index: " + index);
        }
        return array.get(index);
    }

    private static void normalizeWheelRadius(float[] positions) throws IOException {
        float radius = 0f;
        for (int i = 0; i < positions.length; i += 3) {
            radius = Math.max(radius, Math.abs(positions[i + 1]));
            radius = Math.max(radius, Math.abs(positions[i + 2]));
        }
        if (radius < 0.05f) throw new IOException("DirtBike wheel radius is invalid");
        float scale = TARGET_WHEEL_RADIUS / radius;
        for (int i = 0; i < positions.length; i += 3) {
            positions[i + 1] *= scale;
            positions[i + 2] *= scale;
        }
    }

    private static Model buildModel(Array<MeshData> parts) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        for (MeshData part : parts) {
            float[] normals = generateNormals(part.positions, part.indices);
            int vertexCount = part.positions.length / 3;
            float[] vertices = new float[vertexCount * 6];
            for (int i = 0; i < vertexCount; i++) {
                int p = i * 3;
                int v = i * 6;
                vertices[v] = part.positions[p];
                vertices[v + 1] = part.positions[p + 1];
                vertices[v + 2] = part.positions[p + 2];
                vertices[v + 3] = normals[p];
                vertices[v + 4] = normals[p + 1];
                vertices[v + 5] = normals[p + 2];
            }

            Mesh mesh = new Mesh(true, vertexCount, part.indices.length,
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                    new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"));
            mesh.setVertices(vertices);
            mesh.setIndices(part.indices);
            Material material = new Material(ColorAttribute.createDiffuse(part.color));
            builder.part(part.name, mesh, GL20.GL_TRIANGLES, material);
        }
        return builder.end();
    }

    private static float[] generateNormals(float[] positions, short[] indices) {
        float[] normals = new float[positions.length];
        for (int i = 0; i < indices.length; i += 3) {
            int ia = indices[i] & 0xffff;
            int ib = indices[i + 1] & 0xffff;
            int ic = indices[i + 2] & 0xffff;
            int a = ia * 3;
            int b = ib * 3;
            int c = ic * 3;

            float abx = positions[b] - positions[a];
            float aby = positions[b + 1] - positions[a + 1];
            float abz = positions[b + 2] - positions[a + 2];
            float acx = positions[c] - positions[a];
            float acy = positions[c + 1] - positions[a + 1];
            float acz = positions[c + 2] - positions[a + 2];
            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;

            normals[a] += nx; normals[a + 1] += ny; normals[a + 2] += nz;
            normals[b] += nx; normals[b + 1] += ny; normals[b + 2] += nz;
            normals[c] += nx; normals[c + 1] += ny; normals[c + 2] += nz;
        }

        for (int i = 0; i < normals.length; i += 3) {
            float x = normals[i];
            float y = normals[i + 1];
            float z = normals[i + 2];
            float length = (float)Math.sqrt(x * x + y * y + z * z);
            if (length > 0.000001f) {
                float inv = 1f / length;
                normals[i] = x * inv;
                normals[i + 1] = y * inv;
                normals[i + 2] = z * inv;
            } else {
                normals[i] = 0f;
                normals[i + 1] = 1f;
                normals[i + 2] = 0f;
            }
        }
        return normals;
    }

    private static Color diffuseColor(Material material) {
        ColorAttribute attribute = (ColorAttribute)material.get(ColorAttribute.Diffuse);
        return attribute == null ? new Color(Color.WHITE) : new Color(attribute.color);
    }

    private static void requireRange(int offset, int length, int total) throws IOException {
        if (offset < 0 || length < 0 || offset > total - length) {
            throw new IOException("DirtBike GLB buffer read is out of range");
        }
    }

    private static final class ParsedGlb {
        final JsonValue json;
        final byte[] binary;

        ParsedGlb(JsonValue json, byte[] binary) {
            this.json = json;
            this.binary = binary;
        }
    }

    private static final class AccessView {
        final int offset;
        final int stride;

        AccessView(int offset, int stride) {
            this.offset = offset;
            this.stride = stride;
        }
    }

    private static final class MeshData {
        final String name;
        final float[] positions;
        final short[] indices;
        final Color color;

        MeshData(String name, float[] positions, short[] indices, Color color) {
            this.name = name;
            this.positions = positions;
            this.indices = indices;
            this.color = color;
        }
    }
}
