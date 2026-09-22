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
 * The GLB scene graph supplies the authored object transforms. DirtBike.layout.json
 * supplies the game's authoritative named anchors plus the one documented source-rig
 * correction required by the static chassis geometry. Keeping those values in a
 * human-readable manifest means placement is measurable and reproducible rather than
 * being tuned by eye in runtime code.
 */
final class DirtBikeMeshLoader {
    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;
    private static final int GLB_VERSION = 2;
    private static final String LAYOUT_PATH = "models/DirtBike.layout.json";
    private static final float ANCHOR_EPSILON = 0.001f;

    private DirtBikeMeshLoader() {}

    static ModelInstance[] load(Array<Model> ownedModels,
                                Material bodyMaterial,
                                Material engineMaterial,
                                Material wheelMaterial) throws IOException {
        byte[] bytes = Gdx.files.internal("models/DirtBike.glb").readBytes();
        ParsedGlb glb = parseGlb(bytes);
        JsonValue layout = readLayout();

        JsonValue meshes = glb.json.get("meshes");
        JsonValue nodes = glb.json.get("nodes");
        if (meshes == null || meshes.size == 0) {
            throw new IOException("DirtBike.glb contains no meshes");
        }
        if (nodes == null || nodes.size == 0) {
            throw new IOException("DirtBike.glb contains no nodes");
        }

        Matrix4[] nodeWorlds = buildNodeWorldTransforms(glb.json);

        JsonValue anchors = layout.get("anchors");
        if (anchors == null) throw new IOException("DirtBike layout has no anchors");
        Vector3 rearPivotWorld = readVec3(anchors.get("rearAxle"), "rearAxle");
        Vector3 frontPivotWorld = readVec3(anchors.get("frontAxle"), "frontAxle");
        Vector3 steeringHead = readVec3(anchors.get("steeringHead"), "steeringHead");

        // The manifest is the game-facing source of truth, but fail loudly if the
        // binary asset's named pivots ever drift away from it.
        requireAnchorMatches("RearWheelPivot", rearPivotWorld,
                findNamedNodeOrigin(glb.json, nodeWorlds, "RearWheelPivot"));
        requireAnchorMatches("FrontWheelPivot", frontPivotWorld,
                findNamedNodeOrigin(glb.json, nodeWorlds, "FrontWheelPivot"));
        requireAnchorMatches("SteeringPivot", steeringHead,
                findNamedNodeOrigin(glb.json, nodeWorlds, "SteeringPivot"));

        float declaredWheelbase = layout.getFloat("wheelbase", -1f);
        float measuredWheelbase = frontPivotWorld.dst(rearPivotWorld);
        if (declaredWheelbase <= 0f || Math.abs(declaredWheelbase - measuredWheelbase) > ANCHOR_EPSILON) {
            throw new IOException("DirtBike layout wheelbase does not match axle anchors");
        }

        JsonValue correctionJson = layout.get("staticGeometryCorrection");
        if (correctionJson == null) {
            throw new IOException("DirtBike layout has no staticGeometryCorrection");
        }
        Vector3 staticCorrection = readVec3(correctionJson.get("translation"),
                "staticGeometryCorrection.translation");
        JsonValue correctedMeshes = correctionJson.get("meshes");
        if (correctedMeshes == null) {
            throw new IOException("DirtBike layout correction has no mesh list");
        }

        Array<MeshData> bodyParts = new Array<>();
        Array<MeshData> engineParts = new Array<>();
        Array<MeshData> frontWheelParts = new Array<>();
        Array<MeshData> rearWheelParts = new Array<>();

        for (int nodeIndex = 0; nodeIndex < nodes.size; nodeIndex++) {
            Matrix4 world = nodeWorlds[nodeIndex];
            if (world == null) continue;

            JsonValue node = nodes.get(nodeIndex);
            if (!node.has("mesh")) continue;

            int meshIndex = node.getInt("mesh");
            JsonValue meshJson = getArrayItem(glb.json, "meshes", meshIndex);
            String name = meshJson.getString("name",
                    node.getString("name", "mesh-" + meshIndex));

            // BalancePointGame still owns the animated steering hardware. These exact
            // GLB meshes are retained in the manifest and will replace it next.
            if (isProceduralSteeringPart(name)) continue;

            JsonValue primitives = meshJson.get("primitives");
            if (primitives == null || primitives.size == 0) {
                throw new IOException("DirtBike mesh has no primitives: " + name);
            }

            for (int primitiveIndex = 0; primitiveIndex < primitives.size; primitiveIndex++) {
                JsonValue primitive = primitives.get(primitiveIndex);
                if (primitive.getInt("mode", GL20.GL_TRIANGLES) != GL20.GL_TRIANGLES) {
                    throw new IOException("DirtBike mesh is not triangles: " + name);
                }

                JsonValue attributes = primitive.get("attributes");
                if (attributes == null || !attributes.has("POSITION") || !primitive.has("indices")) {
                    throw new IOException("DirtBike mesh is missing POSITION/indices: " + name);
                }

                float[] positions = readPositions(glb, attributes.getInt("POSITION"));
                short[] indices = readIndices(glb, primitive.getInt("indices"),
                        positions.length / 3);

                // POSITION accessors are node-local. First get every vertex into the
                // exact authored world/bike coordinate system.
                transformPositions(positions, world);
                if (linearDeterminant(world) < 0f) {
                    flipTriangleWinding(indices);
                }

                // The old import pipeline documented that these static source-rig
                // meshes sit 0.28 m below the visual axle reference. Apply the exact
                // correction from the manifest, never to either wheel.
                if (containsString(correctedMeshes, name)) {
                    translatePositions(positions, staticCorrection);
                }

                Material fallback;
                if ("Engine_GEO".equals(name)) {
                    fallback = engineMaterial;
                } else if ("FrontWheel_GEO".equals(name) || "RearWheel_GEO".equals(name)) {
                    fallback = wheelMaterial;
                } else {
                    fallback = bodyMaterial;
                }

                Color color = attributes.has("COLOR_0")
                        ? readFirstColor(glb, attributes.getInt("COLOR_0"))
                        : diffuseColor(fallback);

                boolean frontWheel = "FrontWheel_GEO".equals(name);
                boolean rearWheel = "RearWheel_GEO".equals(name);

                // Static geometry is relative to the rear axle. Each wheel is relative
                // to its own exact axle so runtime spin remains centered on the hub.
                Vector3 rebase = frontWheel ? frontPivotWorld : rearPivotWorld;
                rebasePositions(positions, rebase);

                String partName = name + "-n" + nodeIndex + "-p" + primitiveIndex;
                MeshData part = new MeshData(partName, positions, indices, color);
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

        Gdx.app.log("BalancePoint", "DirtBike exact layout: rear=" + rearPivotWorld
                + " front=" + frontPivotWorld + " steering=" + steeringHead
                + " staticCorrection=" + staticCorrection);

        return new ModelInstance[] {
                new ModelInstance(body),
                new ModelInstance(engine),
                new ModelInstance(front),
                new ModelInstance(rear)
        };
    }

    private static JsonValue readLayout() throws IOException {
        try {
            JsonValue layout = new JsonReader().parse(
                    Gdx.files.internal(LAYOUT_PATH).readString("UTF-8"));
            if (layout.getInt("version", 0) != 1) {
                throw new IOException("Unsupported DirtBike layout version");
            }
            String coordinateSystem = layout.getString("coordinateSystem", "");
            if (!"X right, Y up, Z forward".equals(coordinateSystem)) {
                throw new IOException("Unexpected DirtBike layout coordinate system: "
                        + coordinateSystem);
            }
            return layout;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not read DirtBike layout", e);
        }
    }

    private static Vector3 readVec3(JsonValue value, String label) throws IOException {
        if (value == null || value.size != 3) {
            throw new IOException("DirtBike layout " + label + " must contain 3 values");
        }
        return new Vector3(value.getFloat(0), value.getFloat(1), value.getFloat(2));
    }

    private static boolean containsString(JsonValue array, String wanted) {
        for (int i = 0; i < array.size; i++) {
            if (wanted.equals(array.getString(i))) return true;
        }
        return false;
    }

    private static void requireAnchorMatches(String label, Vector3 expected, Vector3 actual)
            throws IOException {
        if (actual == null) {
            throw new IOException("DirtBike GLB is missing named anchor " + label);
        }
        if (expected.dst(actual) > ANCHOR_EPSILON) {
            throw new IOException("DirtBike anchor drift for " + label
                    + ": layout=" + expected + " glb=" + actual);
        }
    }

    private static boolean isProceduralSteeringPart(String name) {
        return "FrontShock_GEO".equals(name)
                || "Handle_GEO".equals(name)
                || "LeftLeaver_GEO".equals(name)
                || "RightLeaver_GEO".equals(name);
    }

    private static Matrix4[] buildNodeWorldTransforms(JsonValue json) throws IOException {
        JsonValue nodes = json.get("nodes");
        Matrix4[] worlds = new Matrix4[nodes.size];
        boolean[] visiting = new boolean[nodes.size];

        JsonValue scenes = json.get("scenes");
        boolean traversedScene = false;
        if (scenes != null && scenes.size > 0) {
            int sceneIndex = json.getInt("scene", 0);
            if (sceneIndex < 0 || sceneIndex >= scenes.size) {
                throw new IOException("Invalid DirtBike default scene index: " + sceneIndex);
            }
            JsonValue roots = scenes.get(sceneIndex).get("nodes");
            if (roots != null) {
                Matrix4 identity = new Matrix4();
                for (int i = 0; i < roots.size; i++) {
                    traverseNode(nodes, roots.getInt(i), identity, worlds, visiting);
                }
                traversedScene = roots.size > 0;
            }
        }

        if (!traversedScene) {
            boolean[] child = new boolean[nodes.size];
            for (int i = 0; i < nodes.size; i++) {
                JsonValue children = nodes.get(i).get("children");
                if (children == null) continue;
                for (int c = 0; c < children.size; c++) {
                    int childIndex = children.getInt(c);
                    if (childIndex < 0 || childIndex >= nodes.size) {
                        throw new IOException("Invalid DirtBike child node index: " + childIndex);
                    }
                    child[childIndex] = true;
                }
            }
            Matrix4 identity = new Matrix4();
            for (int i = 0; i < nodes.size; i++) {
                if (!child[i]) traverseNode(nodes, i, identity, worlds, visiting);
            }
        }

        return worlds;
    }

    private static void traverseNode(JsonValue nodes,
                                     int nodeIndex,
                                     Matrix4 parentWorld,
                                     Matrix4[] worlds,
                                     boolean[] visiting) throws IOException {
        if (nodeIndex < 0 || nodeIndex >= nodes.size) {
            throw new IOException("Invalid DirtBike node index: " + nodeIndex);
        }
        if (worlds[nodeIndex] != null) return;
        if (visiting[nodeIndex]) {
            throw new IOException("Cycle detected in DirtBike node hierarchy");
        }

        visiting[nodeIndex] = true;
        JsonValue node = nodes.get(nodeIndex);
        Matrix4 world = new Matrix4(parentWorld).mul(readNodeLocalTransform(node));
        worlds[nodeIndex] = world;

        JsonValue children = node.get("children");
        if (children != null) {
            for (int i = 0; i < children.size; i++) {
                traverseNode(nodes, children.getInt(i), world, worlds, visiting);
            }
        }
        visiting[nodeIndex] = false;
    }

    private static Matrix4 readNodeLocalTransform(JsonValue node) throws IOException {
        JsonValue matrix = node.get("matrix");
        if (matrix != null) {
            if (matrix.size != 16) {
                throw new IOException("DirtBike node matrix must contain 16 values");
            }
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) values[i] = matrix.getFloat(i);
            return new Matrix4(values);
        }

        float tx = 0f, ty = 0f, tz = 0f;
        JsonValue translation = node.get("translation");
        if (translation != null) {
            requireVectorSize(translation, 3, "translation");
            tx = translation.getFloat(0);
            ty = translation.getFloat(1);
            tz = translation.getFloat(2);
        }

        float qx = 0f, qy = 0f, qz = 0f, qw = 1f;
        JsonValue rotation = node.get("rotation");
        if (rotation != null) {
            requireVectorSize(rotation, 4, "rotation");
            qx = rotation.getFloat(0);
            qy = rotation.getFloat(1);
            qz = rotation.getFloat(2);
            qw = rotation.getFloat(3);
        }

        float sx = 1f, sy = 1f, sz = 1f;
        JsonValue scale = node.get("scale");
        if (scale != null) {
            requireVectorSize(scale, 3, "scale");
            sx = scale.getFloat(0);
            sy = scale.getFloat(1);
            sz = scale.getFloat(2);
        }

        Quaternion orientation = new Quaternion(qx, qy, qz, qw).nor();
        return new Matrix4().idt()
                .translate(tx, ty, tz)
                .rotate(orientation)
                .scale(sx, sy, sz);
    }

    private static void requireVectorSize(JsonValue value, int expected, String label)
            throws IOException {
        if (value.size != expected) {
            throw new IOException("DirtBike node " + label + " must contain "
                    + expected + " values");
        }
    }

    private static Vector3 findNamedNodeOrigin(JsonValue json,
                                               Matrix4[] nodeWorlds,
                                               String wantedName) {
        JsonValue nodes = json.get("nodes");
        for (int i = 0; i < nodes.size; i++) {
            if (nodeWorlds[i] == null) continue;
            String name = nodes.get(i).getString("name", "");
            if (wantedName.equalsIgnoreCase(name)) {
                return transformOrigin(nodeWorlds[i]);
            }
        }
        return null;
    }

    private static Vector3 transformOrigin(Matrix4 matrix) {
        float[] v = matrix.val;
        return new Vector3(v[Matrix4.M03], v[Matrix4.M13], v[Matrix4.M23]);
    }

    private static void transformPositions(float[] positions, Matrix4 transform) {
        float[] m = transform.val;
        for (int i = 0; i < positions.length; i += 3) {
            float x = positions[i];
            float y = positions[i + 1];
            float z = positions[i + 2];
            positions[i] = m[Matrix4.M00] * x + m[Matrix4.M01] * y
                    + m[Matrix4.M02] * z + m[Matrix4.M03];
            positions[i + 1] = m[Matrix4.M10] * x + m[Matrix4.M11] * y
                    + m[Matrix4.M12] * z + m[Matrix4.M13];
            positions[i + 2] = m[Matrix4.M20] * x + m[Matrix4.M21] * y
                    + m[Matrix4.M22] * z + m[Matrix4.M23];
        }
    }

    private static void translatePositions(float[] positions, Vector3 translation) {
        for (int i = 0; i < positions.length; i += 3) {
            positions[i] += translation.x;
            positions[i + 1] += translation.y;
            positions[i + 2] += translation.z;
        }
    }

    private static void rebasePositions(float[] positions, Vector3 pivot) {
        for (int i = 0; i < positions.length; i += 3) {
            positions[i] -= pivot.x;
            positions[i + 1] -= pivot.y;
            positions[i + 2] -= pivot.z;
        }
    }

    private static float linearDeterminant(Matrix4 matrix) {
        float[] m = matrix.val;
        return m[Matrix4.M00] * (m[Matrix4.M11] * m[Matrix4.M22]
                - m[Matrix4.M12] * m[Matrix4.M21])
                - m[Matrix4.M01] * (m[Matrix4.M10] * m[Matrix4.M22]
                - m[Matrix4.M12] * m[Matrix4.M20])
                + m[Matrix4.M02] * (m[Matrix4.M10] * m[Matrix4.M21]
                - m[Matrix4.M11] * m[Matrix4.M20]);
    }

    private static void flipTriangleWinding(short[] indices) {
        for (int i = 0; i < indices.length; i += 3) {
            short temp = indices[i + 1];
            indices[i + 1] = indices[i + 2];
            indices[i + 2] = temp;
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
