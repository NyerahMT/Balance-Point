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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
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
 * The file itself stays standard glTF/GLB. This reader implements the subset
 * Balance Point currently needs: indexed triangle meshes with float VEC3 positions,
 * optional vertex colors, and the glTF scene/node transform hierarchy.
 *
 * Static parts are baked into bike-local space once at load time. Wheel mesh
 * transforms are baked relative to their wheel-pivot nodes so gameplay can still
 * spin and steer the wheels independently.
 */
final class DirtBikeMeshLoader {
    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;
    private static final int GLB_VERSION = 2;

    // Physics remains unchanged. The visual tires are normalized around their
    // authored pivot so they continue to match the existing physics contact model.
    private static final float TARGET_WHEEL_RADIUS = 0.31f;

    // Existing visual alignment between the imported chassis and physics axle line.
    // This is applied after authored glTF node transforms have been baked.
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

        Array<NodeMeshUse> meshUses = collectMeshUses(glb.json, meshes.size);
        Array<MeshData> bodyParts = new Array<>();
        Array<MeshData> engineParts = new Array<>();
        Array<MeshData> frontWheelParts = new Array<>();
        Array<MeshData> rearWheelParts = new Array<>();

        for (NodeMeshUse use : meshUses) {
            JsonValue meshJson = getArrayItem(glb.json, "meshes", use.meshIndex);
            String name = meshJson.getString("name", "mesh-" + use.meshIndex);
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

            boolean frontWheel = "FrontWheel_GEO".equals(name);
            boolean rearWheel = "RearWheel_GEO".equals(name);
            if (frontWheel || rearWheel) {
                // A wheel is animated by BalancePointGame around its axle. Preserve
                // the model-authored transform inside that pivot, but intentionally
                // remove the pivot's world translation/rotation from the baked mesh.
                Matrix4 wheelLocal = new Matrix4(use.worldTransform);
                if (use.wheelPivotWorld != null) {
                    Matrix4 inversePivot = new Matrix4(use.wheelPivotWorld);
                    try {
                        inversePivot.inv();
                    } catch (RuntimeException e) {
                        throw new IOException("Non-invertible wheel pivot for " + name, e);
                    }
                    wheelLocal.set(inversePivot).mul(use.worldTransform);
                }
                transformPositions(positions, wheelLocal);
                normalizeWheelRadius(positions);
            } else {
                // Body/engine/detail geometry remains attached to the chassis, so
                // bake the complete authored scene transform into its vertices.
                transformPositions(positions, use.worldTransform);
                for (int v = 0; v < positions.length; v += 3) {
                    positions[v + 1] += CHASSIS_Y_OFFSET;
                }
            }

            MeshData part = new MeshData(name, positions, indices, color);
            if (frontWheel) {
                frontWheelParts.add(part);
            } else if (rearWheel) {
                rearWheelParts.add(part);
            } else if ("Engine_GEO".equals(name)) {
                engineParts.add(part);
            } else {
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

    /**
     * Resolve glTF scene/node transforms before reading mesh geometry.
     *
     * The previous loader iterated the meshes[] array directly, which discards the
     * transforms stored in nodes[]. That is legal glTF but assembles a multi-part
     * model incorrectly whenever a mesh is positioned by a parent pivot/node.
     */
    private static Array<NodeMeshUse> collectMeshUses(JsonValue json, int meshCount)
            throws IOException {
        Array<NodeMeshUse> result = new Array<>();
        JsonValue nodes = json.get("nodes");
        JsonValue scenes = json.get("scenes");

        // Keep a safe fallback for a flat GLB. DirtBike.glb currently has a scene
        // hierarchy, but a future re-export without nodes should still load.
        if (nodes == null || nodes.size == 0 || scenes == null || scenes.size == 0) {
            for (int meshIndex = 0; meshIndex < meshCount; meshIndex++) {
                result.add(new NodeMeshUse(meshIndex, new Matrix4().idt(), null));
            }
            return result;
        }

        int sceneIndex = json.getInt("scene", 0);
        JsonValue scene = getArrayItem(json, "scenes", sceneIndex);
        JsonValue roots = scene.get("nodes");
        if (roots == null || roots.size == 0) {
            throw new IOException("DirtBike glTF scene contains no root nodes");
        }

        boolean[] visiting = new boolean[nodes.size];
        boolean[] visited = new boolean[nodes.size];
        for (int i = 0; i < roots.size; i++) {
            int rootIndex = roots.get(i).asInt();
            collectNodeMeshUses(json, rootIndex, new Matrix4().idt(), null,
                    result, visiting, visited);
        }

        if (result.size == 0) {
            throw new IOException("DirtBike glTF scene contains no mesh nodes");
        }
        return result;
    }

    private static void collectNodeMeshUses(JsonValue json,
                                            int nodeIndex,
                                            Matrix4 parentWorld,
                                            Matrix4 inheritedWheelPivot,
                                            Array<NodeMeshUse> out,
                                            boolean[] visiting,
                                            boolean[] visited) throws IOException {
        JsonValue nodes = json.get("nodes");
        if (nodeIndex < 0 || nodeIndex >= nodes.size) {
            throw new IOException("Invalid DirtBike node index: " + nodeIndex);
        }
        if (visiting[nodeIndex]) {
            throw new IOException("Cycle detected in DirtBike glTF node hierarchy");
        }

        // A legal glTF scene is a tree/forest, not a DAG. Avoid accidentally baking
        // the same node twice if a malformed re-export references it twice.
        if (visited[nodeIndex]) return;
        visiting[nodeIndex] = true;

        JsonValue node = nodes.get(nodeIndex);
        Matrix4 local = readNodeTransform(node);
        Matrix4 world = new Matrix4(parentWorld).mul(local);

        String nodeName = node.getString("name", "node-" + nodeIndex);
        Matrix4 wheelPivot = inheritedWheelPivot;
        if ("FrontWheelPivot".equals(nodeName) || "RearWheelPivot".equals(nodeName)) {
            wheelPivot = new Matrix4(world);
        }

        if (node.has("mesh")) {
            int meshIndex = node.getInt("mesh");
            JsonValue meshes = json.get("meshes");
            if (meshes == null || meshIndex < 0 || meshIndex >= meshes.size) {
                throw new IOException("Invalid mesh index on DirtBike node " + nodeName);
            }
            out.add(new NodeMeshUse(meshIndex, new Matrix4(world),
                    wheelPivot == null ? null : new Matrix4(wheelPivot)));
        }

        JsonValue children = node.get("children");
        if (children != null) {
            for (int i = 0; i < children.size; i++) {
                collectNodeMeshUses(json, children.get(i).asInt(), world, wheelPivot,
                        out, visiting, visited);
            }
        }

        visiting[nodeIndex] = false;
        visited[nodeIndex] = true;
    }

    private static Matrix4 readNodeTransform(JsonValue node) throws IOException {
        JsonValue matrixJson = node.get("matrix");
        if (matrixJson != null) {
            if (matrixJson.size != 16) {
                throw new IOException("DirtBike node matrix must contain 16 values");
            }
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) values[i] = matrixJson.get(i).asFloat();
            // glTF and libGDX both store 4x4 transforms in column-major order.
            return new Matrix4(values);
        }

        Vector3 translation = new Vector3();
        JsonValue translationJson = node.get("translation");
        if (translationJson != null) {
            if (translationJson.size != 3) {
                throw new IOException("DirtBike node translation must contain 3 values");
            }
            translation.set(translationJson.get(0).asFloat(),
                    translationJson.get(1).asFloat(),
                    translationJson.get(2).asFloat());
        }

        Quaternion rotation = new Quaternion(0f, 0f, 0f, 1f);
        JsonValue rotationJson = node.get("rotation");
        if (rotationJson != null) {
            if (rotationJson.size != 4) {
                throw new IOException("DirtBike node rotation must contain 4 values");
            }
            rotation.set(rotationJson.get(0).asFloat(),
                    rotationJson.get(1).asFloat(),
                    rotationJson.get(2).asFloat(),
                    rotationJson.get(3).asFloat());
        }

        Vector3 scale = new Vector3(1f, 1f, 1f);
        JsonValue scaleJson = node.get("scale");
        if (scaleJson != null) {
            if (scaleJson.size != 3) {
                throw new IOException("DirtBike node scale must contain 3 values");
            }
            scale.set(scaleJson.get(0).asFloat(),
                    scaleJson.get(1).asFloat(),
                    scaleJson.get(2).asFloat());
        }

        return new Matrix4().idt()
                .translate(translation)
                .rotate(rotation)
                .scale(scale.x, scale.y, scale.z);
    }

    private static void transformPositions(float[] positions, Matrix4 transform) {
        Vector3 point = new Vector3();
        for (int i = 0; i < positions.length; i += 3) {
            point.set(positions[i], positions[i + 1], positions[i + 2]).mul(transform);
            positions[i] = point.x;
            positions[i + 1] = point.y;
            positions[i + 2] = point.z;
        }
    }

    private static ParsedGlb parseGlb(byte[] bytes) throws IOException {
        if (bytes.length < 20) throw new IOException("DirtBike.glb is too small");

        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = in.getInt();
        int version = in.getInt();
        int declaredLength = in.getInt();
        if (magic != GLB_MAGIC) throw new IOException("Bad DirtBike GLB magic");
        if (version != GLB_VERSION) {
            throw new IOException("Unsupported DirtBike GLB version: " + version);
        }
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
        if (accessor.getInt("componentType") != 5126
                || !"VEC3".equals(accessor.getString("type"))) {
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
        else {
            throw new IOException("Unsupported DirtBike index component type: " + componentType);
        }

        int count = accessor.getInt("count");
        if (count <= 0 || (count % 3) != 0) {
            throw new IOException("Invalid DirtBike index count");
        }
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
        if (components == 0) {
            throw new IOException("Unsupported DirtBike COLOR_0 type: " + type);
        }

        int componentType = accessor.getInt("componentType");
        int componentSize;
        if (componentType == 5121) componentSize = 1;
        else if (componentType == 5123) componentSize = 2;
        else if (componentType == 5126) componentSize = 4;
        else {
            throw new IOException("Unsupported DirtBike color component type: " + componentType);
        }

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
        if (view.getInt("buffer", 0) != 0) {
            throw new IOException("DirtBike GLB uses external buffer");
        }
        int offset = view.getInt("byteOffset", 0) + accessor.getInt("byteOffset", 0);
        int stride = view.getInt("byteStride", elementSize);
        if (stride < elementSize) throw new IOException("Invalid DirtBike buffer stride");
        return new AccessView(offset, stride);
    }

    private static JsonValue getArrayItem(JsonValue root, String name, int index)
            throws IOException {
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

            normals[a] += nx;
            normals[a + 1] += ny;
            normals[a + 2] += nz;
            normals[b] += nx;
            normals[b + 1] += ny;
            normals[b + 2] += nz;
            normals[c] += nx;
            normals[c + 1] += ny;
            normals[c + 2] += nz;
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

    private static final class NodeMeshUse {
        final int meshIndex;
        final Matrix4 worldTransform;
        final Matrix4 wheelPivotWorld;

        NodeMeshUse(int meshIndex, Matrix4 worldTransform, Matrix4 wheelPivotWorld) {
            this.meshIndex = meshIndex;
            this.worldTransform = worldTransform;
            this.wheelPivotWorld = wheelPivotWorld;
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
