package com.nyerahworks.balancepoint;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Imports the replacement dirt bike while preserving the approved viewer pose.
 *
 * Authored glTF transforms are baked first and the entire motorcycle receives one
 * rigid normalization into Balance Point coordinates. Moving assemblies are then
 * converted to pivot-local coordinates only; zero steering and zero wheel spin
 * reconstruct the exact approved neutral scene.
 *
 * The replacement source does not provide a steering node. Instead of hand-moving
 * meshes, Stage 2 derives a front-end rig from source topology:
 *  - connected components in the Cube_* source meshes are separated by shared
 *    geometric vertices;
 *  - components whose geometric centers occupy the source front-end region become
 *    the steering assembly;
 *  - the two long METAL fork components define the steering/rake axis by a 2-D
 *    least-squares principal-axis fit in the bike Y/Z plane.
 */
final class ViewerFaithfulDirtBikeLoader {
    static final String ASSET_PATH = "models/dirt_bike_off_road_bike_low_poly.glb";

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION = 2;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;

    private static final float GAME_ROOT_LIFT = 0.31f;

    private static final String WHEEL_A = "Cylinder.005_TYRE_0";
    private static final String WHEEL_B = "Cylinder.003_TYRE_0";
    private static final String WHEEL_A_PREFIX = "Cylinder.005_";
    private static final String WHEEL_B_PREFIX = "Cylinder.003_";

    private static final float STEERING_REGION_FRACTION = 0.67f;
    private static final String FORK_AXIS_MESH = "Cube_METAL_0";
    private static final float CONNECTIVITY_QUANTIZATION = 100000f;

    private ViewerFaithfulDirtBikeLoader() {}

    static DirtBikeMeshLoader.LoadedBike load(Array<Model> ownedModels) throws IOException {
        byte[] bytes = Gdx.files.internal(ASSET_PATH).readBytes();
        ParsedGlb glb = parseGlb(bytes);

        JsonValue nodes = glb.json.get("nodes");
        JsonValue meshes = glb.json.get("meshes");
        if (nodes == null || nodes.size == 0 || meshes == null || meshes.size == 0) {
            throw new IOException("Replacement dirt bike GLB has no nodes/meshes");
        }

        Matrix4[] nodeWorlds = buildNodeWorldTransforms(glb.json);
        Bounds wheelA = findMeshBounds(glb, nodeWorlds, WHEEL_A);
        Bounds wheelB = findMeshBounds(glb, nodeWorlds, WHEEL_B);
        if (wheelA == null || wheelB == null) {
            throw new IOException("Replacement dirt bike wheel meshes were not found");
        }

        boolean wheelAIsRear = wheelA.center.z <= wheelB.center.z;
        Bounds rear = wheelAIsRear ? wheelA : wheelB;
        Bounds front = wheelAIsRear ? wheelB : wheelA;
        String rearPrefix = wheelAIsRear ? WHEEL_A_PREFIX : WHEEL_B_PREFIX;
        String frontPrefix = wheelAIsRear ? WHEEL_B_PREFIX : WHEEL_A_PREFIX;

        float wheelbase = rear.center.dst(front.center);
        float rearRadius = ((rear.max.y - rear.min.y) + (rear.max.z - rear.min.z)) * 0.25f;
        float frontRadius = ((front.max.y - front.min.y) + (front.max.z - front.min.z)) * 0.25f;

        if (wheelbase < 1.20f || wheelbase > 1.60f) {
            throw new IOException("Replacement dirt bike wheelbase is implausible: " + wheelbase);
        }
        if (rearRadius < 0.25f || rearRadius > 0.45f
                || frontRadius < 0.25f || frontRadius > 0.45f
                || Math.abs(rearRadius - frontRadius) > 0.02f) {
            throw new IOException("Replacement dirt bike wheel radii are inconsistent: rear="
                    + rearRadius + " front=" + frontRadius);
        }

        Vector3 normalization = new Vector3(
                -rear.center.x,
                -GAME_ROOT_LIFT - rear.min.y,
                -rear.center.z);

        Vector3 rearAxleOffset = new Vector3(rear.center).add(normalization);
        Vector3 frontAxleOffset = new Vector3(front.center).add(normalization);
        Vector3 rearRebase = new Vector3(rearAxleOffset).scl(-1f);
        Vector3 frontRebase = new Vector3(frontAxleOffset).scl(-1f);

        float steeringRegionStartZ = rearAxleOffset.z + wheelbase * STEERING_REGION_FRACTION;

        Array<MeshData> bodyParts = new Array<>();
        Array<PrimitivePiece> steeringPieces = new Array<>();
        Array<MeshData> rearWheelParts = new Array<>();
        Array<MeshData> frontWheelParts = new Array<>();
        ForkAxisAccumulator forkAxis = new ForkAxisAccumulator();

        int connectedComponentCount = 0;
        int steeringComponentCount = 0;

        for (int nodeIndex = 0; nodeIndex < nodes.size; nodeIndex++) {
            Matrix4 world = nodeWorlds[nodeIndex];
            if (world == null) continue;

            JsonValue node = nodes.get(nodeIndex);
            if (!node.has("mesh")) continue;

            int meshIndex = node.getInt("mesh");
            JsonValue mesh = arrayItem(glb.json, "meshes", meshIndex);
            JsonValue primitives = mesh.get("primitives");
            if (primitives == null || primitives.size == 0) continue;

            String meshName = mesh.getString("name",
                    node.getString("name", "mesh-" + meshIndex));

            for (int primitiveIndex = 0; primitiveIndex < primitives.size; primitiveIndex++) {
                JsonValue primitive = primitives.get(primitiveIndex);
                if (primitive.getInt("mode", GL20.GL_TRIANGLES) != GL20.GL_TRIANGLES) {
                    throw new IOException("Only triangle primitives are supported: " + meshName);
                }

                JsonValue attributes = primitive.get("attributes");
                if (attributes == null || !attributes.has("POSITION") || !primitive.has("indices")) {
                    throw new IOException("Mesh is missing POSITION/indices: " + meshName);
                }

                float[] positions = readPositions(glb, attributes.getInt("POSITION"));
                short[] indices = readIndices(glb, primitive.getInt("indices"),
                        positions.length / 3);

                transformPositions(positions, world);
                translatePositions(positions, normalization);

                if (linearDeterminant(world) < 0f) {
                    flipTriangleWinding(indices);
                }

                SourceMaterial sourceMaterial = sourceMaterial(glb.json, primitive);
                String partName = meshName + "-n" + nodeIndex + "-p" + primitiveIndex;

                if (meshName.startsWith(rearPrefix)) {
                    translatePositions(positions, rearRebase);
                    rearWheelParts.add(makeMeshData(partName, positions, indices, sourceMaterial));
                    continue;
                }

                if (meshName.startsWith(frontPrefix)) {
                    translatePositions(positions, frontRebase);
                    frontWheelParts.add(makeMeshData(partName, positions, indices, sourceMaterial));
                    continue;
                }

                if (meshName.startsWith("Cube_")) {
                    Array<PrimitivePiece> pieces = splitConnectedComponents(
                            meshName, partName, positions, indices, sourceMaterial);
                    connectedComponentCount += pieces.size;

                    for (PrimitivePiece piece : pieces) {
                        if (isSteeringComponent(piece, steeringRegionStartZ)) {
                            steeringPieces.add(piece);
                            steeringComponentCount++;
                            if (isForkAxisComponent(piece, steeringRegionStartZ,
                                    frontAxleOffset)) {
                                forkAxis.add(piece.positions);
                            }
                        } else {
                            bodyParts.add(makeMeshData(piece));
                        }
                    }
                } else {
                    bodyParts.add(makeMeshData(partName, positions, indices, sourceMaterial));
                }
            }
        }

        if (bodyParts.size == 0) {
            throw new IOException("Replacement dirt bike produced no static body geometry");
        }
        if (rearWheelParts.size != 3 || frontWheelParts.size != 3) {
            throw new IOException("Expected 3 meshes per wheel assembly, got rear="
                    + rearWheelParts.size + " front=" + frontWheelParts.size);
        }
        if (steeringComponentCount < 12) {
            throw new IOException("Steering topology extraction found too few components: "
                    + steeringComponentCount);
        }

        SteeringAxisFit axisFit = forkAxis.finish(frontAxleOffset);
        Vector3 steeringPivot = axisFit.pivot;
        Vector3 steeringAxis = axisFit.axis;
        Vector3 steeringRebase = new Vector3(steeringPivot).scl(-1f);

        Array<MeshData> steeringParts = new Array<>();
        for (PrimitivePiece piece : steeringPieces) {
            translatePositions(piece.positions, steeringRebase);
            steeringParts.add(makeMeshData(piece));
        }

        Model bodyModel = buildModel(bodyParts);
        Model steeringModel = buildModel(steeringParts);
        Model rearWheelModel = buildModel(rearWheelParts);
        Model frontWheelModel = buildModel(frontWheelParts);
        Model emptyModel = buildEmptyModel();

        ownedModels.add(bodyModel);
        ownedModels.add(steeringModel);
        ownedModels.add(rearWheelModel);
        ownedModels.add(frontWheelModel);
        ownedModels.add(emptyModel);

        float wheelRadius = (rearRadius + frontRadius) * 0.5f;
        Gdx.app.log("BalancePoint", "Topology-rigged dirt bike loaded: bodyParts="
                + bodyParts.size + " cubeComponents=" + connectedComponentCount
                + " steeringComponents=" + steeringComponentCount
                + " rearWheelParts=" + rearWheelParts.size
                + " frontWheelParts=" + frontWheelParts.size
                + " wheelbase=" + wheelbase
                + " wheelRadius=" + wheelRadius
                + " steeringPivot=" + steeringPivot
                + " steeringAxis=" + steeringAxis
                + " forkAxisMiss=" + axisFit.frontAxleMiss
                + " rigidTranslation=" + normalization);

        return new DirtBikeMeshLoader.LoadedBike(
                bodyModel,
                emptyModel,
                steeringModel,
                frontWheelModel,
                rearWheelModel,
                rearAxleOffset,
                steeringPivot,
                steeringAxis,
                frontAxleOffset,
                wheelRadius);
    }

    private static boolean isSteeringComponent(PrimitivePiece piece, float regionStartZ) {
        return piece.meshName.startsWith("Cube_")
                && piece.bounds.center.z > regionStartZ;
    }

    private static boolean isForkAxisComponent(PrimitivePiece piece,
                                               float regionStartZ,
                                               Vector3 frontAxleOffset) {
        if (!FORK_AXIS_MESH.equals(piece.meshName)) return false;
        Bounds b = piece.bounds;
        float height = b.max.y - b.min.y;
        float depth = b.max.z - b.min.z;
        return b.center.z > regionStartZ
                && Math.abs(b.center.x - frontAxleOffset.x) < 0.15f
                && height > 0.45f
                && depth > 0.20f;
    }

    private static Array<PrimitivePiece> splitConnectedComponents(String meshName,
                                                                  String partName,
                                                                  float[] positions,
                                                                  short[] indices,
                                                                  SourceMaterial material)
            throws IOException {
        int triangleCount = indices.length / 3;
        UnionFind union = new UnionFind(triangleCount);
        Map<PositionKey, Integer> firstTriangleAtPosition = new HashMap<>();

        for (int triangle = 0; triangle < triangleCount; triangle++) {
            int base = triangle * 3;
            for (int corner = 0; corner < 3; corner++) {
                int vertex = indices[base + corner] & 0xffff;
                int p = vertex * 3;
                PositionKey key = new PositionKey(
                        positions[p], positions[p + 1], positions[p + 2]);
                Integer other = firstTriangleAtPosition.get(key);
                if (other == null) {
                    firstTriangleAtPosition.put(key, triangle);
                } else {
                    union.union(triangle, other);
                }
            }
        }

        Map<Integer, IntArray> groups = new HashMap<>();
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            int root = union.find(triangle);
            IntArray list = groups.get(root);
            if (list == null) {
                list = new IntArray();
                groups.put(root, list);
            }
            list.add(triangle);
        }

        Array<PrimitivePiece> result = new Array<>();
        int componentIndex = 0;
        for (IntArray triangles : groups.values()) {
            int vertexCount = triangles.size * 3;
            if (vertexCount > 32767) {
                throw new IOException("Connected component exceeds libGDX short index limit: "
                        + meshName + " vertices=" + vertexCount);
            }

            float[] componentPositions = new float[vertexCount * 3];
            short[] componentIndices = new short[vertexCount];
            int outVertex = 0;

            for (int i = 0; i < triangles.size; i++) {
                int triangle = triangles.get(i);
                int indexBase = triangle * 3;
                for (int corner = 0; corner < 3; corner++) {
                    int sourceVertex = indices[indexBase + corner] & 0xffff;
                    int source = sourceVertex * 3;
                    int target = outVertex * 3;
                    componentPositions[target] = positions[source];
                    componentPositions[target + 1] = positions[source + 1];
                    componentPositions[target + 2] = positions[source + 2];
                    componentIndices[outVertex] = (short)outVertex;
                    outVertex++;
                }
            }

            Bounds bounds = new Bounds();
            bounds.include(componentPositions);
            bounds.finish();
            result.add(new PrimitivePiece(
                    meshName,
                    partName + "-c" + componentIndex++,
                    componentPositions,
                    componentIndices,
                    material,
                    bounds));
        }
        return result;
    }

    private static MeshData makeMeshData(PrimitivePiece piece) {
        return makeMeshData(piece.name, piece.positions, piece.indices, piece.material);
    }

    private static MeshData makeMeshData(String name,
                                         float[] positions,
                                         short[] indices,
                                         SourceMaterial material) {
        float[] normals = generateNormals(positions, indices);
        return new MeshData(name, positions, normals, indices,
                material.color, material.doubleSided);
    }

    private static ParsedGlb parseGlb(byte[] bytes) throws IOException {
        if (bytes.length < 20) throw new IOException("Replacement dirt bike GLB is too small");

        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = in.getInt();
        int version = in.getInt();
        int declaredLength = in.getInt();
        if (magic != GLB_MAGIC) throw new IOException("Bad replacement dirt bike GLB magic");
        if (version != GLB_VERSION) throw new IOException("Unsupported GLB version: " + version);
        if (declaredLength != bytes.length) {
            throw new IOException("Truncated GLB: header=" + declaredLength + " file=" + bytes.length);
        }

        String jsonText = null;
        byte[] binary = null;
        while (in.remaining() >= 8) {
            int chunkLength = in.getInt();
            int chunkType = in.getInt();
            if (chunkLength < 0 || chunkLength > in.remaining()) {
                throw new IOException("Invalid GLB chunk length");
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
            throw new IOException("Replacement dirt bike GLB is missing JSON/BIN data");
        }
        JsonValue json = new JsonReader().parse(jsonText);
        JsonValue asset = json.get("asset");
        if (asset == null || !asset.getString("version", "").startsWith("2")) {
            throw new IOException("Replacement dirt bike is not glTF 2.x");
        }
        return new ParsedGlb(json, binary);
    }

    private static Matrix4[] buildNodeWorldTransforms(JsonValue json) throws IOException {
        JsonValue nodes = json.get("nodes");
        Matrix4[] worlds = new Matrix4[nodes.size];
        boolean[] visiting = new boolean[nodes.size];

        JsonValue scenes = json.get("scenes");
        if (scenes == null || scenes.size == 0) {
            throw new IOException("Replacement dirt bike has no glTF scene");
        }
        int sceneIndex = json.getInt("scene", 0);
        if (sceneIndex < 0 || sceneIndex >= scenes.size) {
            throw new IOException("Invalid default scene index: " + sceneIndex);
        }
        JsonValue roots = scenes.get(sceneIndex).get("nodes");
        if (roots == null || roots.size == 0) {
            throw new IOException("Replacement dirt bike default scene has no roots");
        }

        Matrix4 identity = new Matrix4();
        for (int i = 0; i < roots.size; i++) {
            traverseNode(nodes, roots.getInt(i), identity, worlds, visiting);
        }
        return worlds;
    }

    private static void traverseNode(JsonValue nodes,
                                     int nodeIndex,
                                     Matrix4 parentWorld,
                                     Matrix4[] worlds,
                                     boolean[] visiting) throws IOException {
        if (nodeIndex < 0 || nodeIndex >= nodes.size) {
            throw new IOException("Invalid glTF node index: " + nodeIndex);
        }
        if (worlds[nodeIndex] != null) return;
        if (visiting[nodeIndex]) throw new IOException("Cycle in glTF node hierarchy");

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
            if (matrix.size != 16) throw new IOException("glTF node matrix must have 16 values");
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) values[i] = matrix.getFloat(i);
            return new Matrix4(values);
        }

        float tx = 0f, ty = 0f, tz = 0f;
        JsonValue translation = node.get("translation");
        if (translation != null) {
            requireSize(translation, 3, "translation");
            tx = translation.getFloat(0);
            ty = translation.getFloat(1);
            tz = translation.getFloat(2);
        }

        float qx = 0f, qy = 0f, qz = 0f, qw = 1f;
        JsonValue rotation = node.get("rotation");
        if (rotation != null) {
            requireSize(rotation, 4, "rotation");
            qx = rotation.getFloat(0);
            qy = rotation.getFloat(1);
            qz = rotation.getFloat(2);
            qw = rotation.getFloat(3);
        }

        float sx = 1f, sy = 1f, sz = 1f;
        JsonValue scale = node.get("scale");
        if (scale != null) {
            requireSize(scale, 3, "scale");
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

    private static void requireSize(JsonValue value, int expected, String label) throws IOException {
        if (value.size != expected) {
            throw new IOException("glTF node " + label + " must contain " + expected + " values");
        }
    }

    private static Bounds findMeshBounds(ParsedGlb glb,
                                         Matrix4[] nodeWorlds,
                                         String wantedMeshName) throws IOException {
        JsonValue nodes = glb.json.get("nodes");
        for (int nodeIndex = 0; nodeIndex < nodes.size; nodeIndex++) {
            JsonValue node = nodes.get(nodeIndex);
            if (!node.has("mesh") || nodeWorlds[nodeIndex] == null) continue;

            int meshIndex = node.getInt("mesh");
            JsonValue mesh = arrayItem(glb.json, "meshes", meshIndex);
            if (!wantedMeshName.equals(mesh.getString("name", ""))) continue;

            Bounds bounds = new Bounds();
            JsonValue primitives = mesh.get("primitives");
            for (int p = 0; p < primitives.size; p++) {
                JsonValue attributes = primitives.get(p).get("attributes");
                if (attributes == null || !attributes.has("POSITION")) continue;
                float[] positions = readPositions(glb, attributes.getInt("POSITION"));
                transformPositions(positions, nodeWorlds[nodeIndex]);
                bounds.include(positions);
            }
            bounds.finish();
            return bounds;
        }
        return null;
    }

    private static float[] readPositions(ParsedGlb glb, int accessorIndex) throws IOException {
        JsonValue accessor = arrayItem(glb.json, "accessors", accessorIndex);
        if (accessor.getInt("componentType") != 5126
                || !"VEC3".equals(accessor.getString("type"))) {
            throw new IOException("POSITION accessor must be FLOAT VEC3");
        }
        if (accessor.has("sparse")) {
            throw new IOException("Sparse POSITION accessors are not supported");
        }

        int count = accessor.getInt("count");
        AccessView view = accessView(glb, accessor, 12);
        ByteBuffer bin = ByteBuffer.wrap(glb.binary).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int offset = view.offset + i * view.stride;
            requireRange(offset, 12, glb.binary.length);
            result[i * 3] = bin.getFloat(offset);
            result[i * 3 + 1] = bin.getFloat(offset + 4);
            result[i * 3 + 2] = bin.getFloat(offset + 8);
        }
        return result;
    }

    private static short[] readIndices(ParsedGlb glb, int accessorIndex, int vertexCount)
            throws IOException {
        JsonValue accessor = arrayItem(glb.json, "accessors", accessorIndex);
        if (!"SCALAR".equals(accessor.getString("type"))) {
            throw new IOException("Index accessor must be SCALAR");
        }
        if (accessor.has("sparse")) throw new IOException("Sparse indices are not supported");

        int componentType = accessor.getInt("componentType");
        int componentSize;
        if (componentType == 5121) componentSize = 1;
        else if (componentType == 5123) componentSize = 2;
        else if (componentType == 5125) componentSize = 4;
        else throw new IOException("Unsupported index component type: " + componentType);

        int count = accessor.getInt("count");
        if (count <= 0 || count % 3 != 0) throw new IOException("Invalid triangle index count");

        AccessView view = accessView(glb, accessor, componentSize);
        ByteBuffer bin = ByteBuffer.wrap(glb.binary).order(ByteOrder.LITTLE_ENDIAN);
        short[] result = new short[count];
        for (int i = 0; i < count; i++) {
            int offset = view.offset + i * view.stride;
            requireRange(offset, componentSize, glb.binary.length);
            long value;
            if (componentType == 5121) value = bin.get(offset) & 0xffL;
            else if (componentType == 5123) value = bin.getShort(offset) & 0xffffL;
            else value = bin.getInt(offset) & 0xffffffffL;

            if (value >= vertexCount || value > 32767L) {
                throw new IOException("Mesh index cannot fit libGDX short index buffer: " + value);
            }
            result[i] = (short)value;
        }
        return result;
    }

    private static AccessView accessView(ParsedGlb glb, JsonValue accessor, int elementSize)
            throws IOException {
        int viewIndex = accessor.getInt("bufferView");
        JsonValue view = arrayItem(glb.json, "bufferViews", viewIndex);
        if (view.getInt("buffer", 0) != 0) throw new IOException("External GLB buffers unsupported");

        int offset = view.getInt("byteOffset", 0) + accessor.getInt("byteOffset", 0);
        int stride = view.getInt("byteStride", elementSize);
        if (stride < elementSize) throw new IOException("Invalid GLB byte stride");
        return new AccessView(offset, stride);
    }

    private static JsonValue arrayItem(JsonValue root, String name, int index) throws IOException {
        JsonValue array = root.get(name);
        if (array == null || index < 0 || index >= array.size) {
            throw new IOException("Invalid " + name + " index: " + index);
        }
        return array.get(index);
    }

    private static SourceMaterial sourceMaterial(JsonValue json, JsonValue primitive)
            throws IOException {
        Color color = new Color(Color.WHITE);
        boolean doubleSided = false;

        if (primitive.has("material")) {
            JsonValue materials = json.get("materials");
            int materialIndex = primitive.getInt("material");
            if (materials == null || materialIndex < 0 || materialIndex >= materials.size) {
                throw new IOException("Invalid material index: " + materialIndex);
            }
            JsonValue material = materials.get(materialIndex);
            doubleSided = material.getBoolean("doubleSided", false);
            JsonValue pbr = material.get("pbrMetallicRoughness");
            if (pbr != null) {
                JsonValue factor = pbr.get("baseColorFactor");
                if (factor != null) {
                    if (factor.size != 4) throw new IOException("baseColorFactor must have 4 values");
                    color.set(factor.getFloat(0), factor.getFloat(1), factor.getFloat(2),
                            factor.getFloat(3));
                }
            }
        }
        return new SourceMaterial(color, doubleSided);
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

    private static float[] generateNormals(float[] positions, short[] indices) {
        float[] normals = new float[positions.length];
        for (int i = 0; i < indices.length; i += 3) {
            int ia = (indices[i] & 0xffff) * 3;
            int ib = (indices[i + 1] & 0xffff) * 3;
            int ic = (indices[i + 2] & 0xffff) * 3;

            float abx = positions[ib] - positions[ia];
            float aby = positions[ib + 1] - positions[ia + 1];
            float abz = positions[ib + 2] - positions[ia + 2];
            float acx = positions[ic] - positions[ia];
            float acy = positions[ic + 1] - positions[ia + 1];
            float acz = positions[ic + 2] - positions[ia + 2];
            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;

            normals[ia] += nx; normals[ia + 1] += ny; normals[ia + 2] += nz;
            normals[ib] += nx; normals[ib + 1] += ny; normals[ib + 2] += nz;
            normals[ic] += nx; normals[ic + 1] += ny; normals[ic + 2] += nz;
        }
        for (int i = 0; i < normals.length; i += 3) {
            float x = normals[i], y = normals[i + 1], z = normals[i + 2];
            float length = (float)Math.sqrt(x * x + y * y + z * z);
            if (length > 0.000001f) {
                normals[i] = x / length;
                normals[i + 1] = y / length;
                normals[i + 2] = z / length;
            } else {
                normals[i] = 0f;
                normals[i + 1] = 1f;
                normals[i + 2] = 0f;
            }
        }
        return normals;
    }

    private static Model buildModel(Array<MeshData> parts) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        for (MeshData part : parts) {
            int vertexCount = part.positions.length / 3;
            float[] vertices = new float[vertexCount * 6];
            for (int i = 0; i < vertexCount; i++) {
                int p = i * 3;
                int v = i * 6;
                vertices[v] = part.positions[p];
                vertices[v + 1] = part.positions[p + 1];
                vertices[v + 2] = part.positions[p + 2];
                vertices[v + 3] = part.normals[p];
                vertices[v + 4] = part.normals[p + 1];
                vertices[v + 5] = part.normals[p + 2];
            }

            Mesh mesh = new Mesh(true, vertexCount, part.indices.length,
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                    new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"));
            mesh.setVertices(vertices);
            mesh.setIndices(part.indices);

            Material material = new Material(ColorAttribute.createDiffuse(part.color));
            if (part.doubleSided) material.set(IntAttribute.createCullFace(GL20.GL_NONE));
            builder.part(part.name, mesh, GL20.GL_TRIANGLES, material);
        }
        return builder.end();
    }

    private static Model buildEmptyModel() {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        return builder.end();
    }

    private static void requireRange(int offset, int length, int total) throws IOException {
        if (offset < 0 || length < 0 || offset > total - length) {
            throw new IOException("GLB buffer read out of range");
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

    private static final class SourceMaterial {
        final Color color;
        final boolean doubleSided;

        SourceMaterial(Color color, boolean doubleSided) {
            this.color = new Color(color);
            this.doubleSided = doubleSided;
        }
    }

    private static final class PrimitivePiece {
        final String meshName;
        final String name;
        final float[] positions;
        final short[] indices;
        final SourceMaterial material;
        final Bounds bounds;

        PrimitivePiece(String meshName,
                       String name,
                       float[] positions,
                       short[] indices,
                       SourceMaterial material,
                       Bounds bounds) {
            this.meshName = meshName;
            this.name = name;
            this.positions = positions;
            this.indices = indices;
            this.material = material;
            this.bounds = bounds;
        }
    }

    private static final class MeshData {
        final String name;
        final float[] positions;
        final float[] normals;
        final short[] indices;
        final Color color;
        final boolean doubleSided;

        MeshData(String name, float[] positions, float[] normals, short[] indices,
                 Color color, boolean doubleSided) {
            this.name = name;
            this.positions = positions;
            this.normals = normals;
            this.indices = indices;
            this.color = new Color(color);
            this.doubleSided = doubleSided;
        }
    }

    private static final class Bounds {
        final Vector3 min = new Vector3(Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        final Vector3 max = new Vector3(Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        final Vector3 center = new Vector3();

        void include(float[] positions) {
            for (int i = 0; i < positions.length; i += 3) {
                min.x = Math.min(min.x, positions[i]);
                min.y = Math.min(min.y, positions[i + 1]);
                min.z = Math.min(min.z, positions[i + 2]);
                max.x = Math.max(max.x, positions[i]);
                max.y = Math.max(max.y, positions[i + 1]);
                max.z = Math.max(max.z, positions[i + 2]);
            }
        }

        void finish() throws IOException {
            if (min.x == Float.POSITIVE_INFINITY) throw new IOException("Empty mesh bounds");
            center.set(min).add(max).scl(0.5f);
        }
    }

    private static final class PositionKey {
        final int x;
        final int y;
        final int z;

        PositionKey(float x, float y, float z) {
            this.x = Math.round(x * CONNECTIVITY_QUANTIZATION);
            this.y = Math.round(y * CONNECTIVITY_QUANTIZATION);
            this.z = Math.round(z * CONNECTIVITY_QUANTIZATION);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PositionKey)) return false;
            PositionKey key = (PositionKey)other;
            return x == key.x && y == key.y && z == key.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }

    private static final class UnionFind {
        final int[] parent;
        final byte[] rank;

        UnionFind(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int i = 0; i < size; i++) parent[i] = i;
        }

        int find(int value) {
            int root = value;
            while (parent[root] != root) root = parent[root];
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }

        void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) return;
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }
    }

    private static final class ForkAxisAccumulator {
        long count;
        int componentCount;
        double sumY;
        double sumZ;
        double sumYY;
        double sumZZ;
        double sumYZ;

        void add(float[] positions) {
            componentCount++;
            for (int i = 0; i < positions.length; i += 3) {
                double y = positions[i + 1];
                double z = positions[i + 2];
                count++;
                sumY += y;
                sumZ += z;
                sumYY += y * y;
                sumZZ += z * z;
                sumYZ += y * z;
            }
        }

        SteeringAxisFit finish(Vector3 frontAxle) throws IOException {
            if (componentCount != 2 || count < 100) {
                throw new IOException("Expected two long fork-axis components, got "
                        + componentCount + " components / " + count + " vertices");
            }

            double meanY = sumY / count;
            double meanZ = sumZ / count;
            double covYY = sumYY / count - meanY * meanY;
            double covZZ = sumZZ / count - meanZ * meanZ;
            double covYZ = sumYZ / count - meanY * meanZ;

            double theta = 0.5 * Math.atan2(2.0 * covYZ, covYY - covZZ);
            Vector3 axis = new Vector3(
                    0f,
                    (float)Math.cos(theta),
                    (float)Math.sin(theta)).nor();
            if (axis.y < 0f) axis.scl(-1f);

            if (axis.y < 0.85f || axis.z > -0.25f || axis.z < -0.60f) {
                throw new IOException("Derived steering axis is implausible: " + axis);
            }

            Vector3 linePoint = new Vector3(frontAxle.x, (float)meanY, (float)meanZ);
            Vector3 delta = new Vector3(frontAxle).sub(linePoint);
            float along = delta.dot(axis);
            Vector3 pivot = linePoint.mulAdd(axis, along);
            float miss = pivot.dst(frontAxle);

            if (miss > 0.015f) {
                throw new IOException("Derived fork axis misses front axle by " + miss + " m");
            }

            return new SteeringAxisFit(pivot, axis, miss);
        }
    }

    private static final class SteeringAxisFit {
        final Vector3 pivot;
        final Vector3 axis;
        final float frontAxleMiss;

        SteeringAxisFit(Vector3 pivot, Vector3 axis, float frontAxleMiss) {
            this.pivot = new Vector3(pivot);
            this.axis = new Vector3(axis);
            this.frontAxleMiss = frontAxleMiss;
        }
    }
}
