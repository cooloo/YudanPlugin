package com.egogame.plugin.runtime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 只核对资源结构和引用，不解析渲染实体，不证明模型已经能够正确显示。 */
public final class VehicleModelPackageValidator {
    private static final Set<String> KNOWN_REQUIRED = new HashSet<>(Arrays.asList(
            "KHR_mesh_quantization", "KHR_materials_clearcoat", "KHR_texture_transform"));
    private VehicleModelPackageValidator() { }

    public static void validate(PluginManifest manifest, File directory) throws IOException {
        try {
            JSONArray models = manifest.contributions().getJSONArray("vehicleModels");
            if (models.length() == 0 || models.length() > 32) fail("模型贡献数量无效");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < models.length(); i++) {
                JSONObject contribution = models.getJSONObject(i);
                String id = contribution.getString("id");
                if (!id.matches("[a-z][a-z0-9.-]{0,99}") || !ids.add(id)) fail("模型标识无效或重复");
                String path = contribution.getString("descriptor");
                if (!path.startsWith("assets/") || !path.endsWith(".json")) fail("模型描述必须位于资源目录");
                JSONObject descriptor = new JSONObject(PluginPackageVerifier.readText(PluginPaths.resolve(directory, path), 65536));
                validateDescriptor(descriptor, directory);
            }
        } catch (JSONException e) { throw new PluginException("MODEL_DESCRIPTOR", "车模描述错误：" + e.getMessage(), e); }
    }
    private static void validateDescriptor(JSONObject descriptor, File directory) throws JSONException, IOException {
        if (descriptor.getInt("schemaVersion") != 1) fail("不支持的模型描述版本");
        String modelPath = descriptor.getString("model");
        if (!modelPath.startsWith("assets/") || !modelPath.endsWith(".glb")) fail("模型必须是包内 GLB");
        File glb = PluginPaths.resolve(directory, modelPath);
        if (!PluginPackageVerifier.hash(glb).equals(descriptor.getString("sha256"))) fail("模型哈希与节点映射不一致");
        JSONObject gltf = readGlb(glb);
        JSONArray nodes = gltf.optJSONArray("nodes");
        if (nodes == null || nodes.length() == 0 || nodes.length() > 4096) fail("节点数量无效或超限");
        Map<String, Integer> names = new HashMap<>();
        int[] parents = new int[nodes.length()];
        Arrays.fill(parents, -1);
        for (int i = 0; i < nodes.length(); i++) {
            String name = nodes.getJSONObject(i).optString("name");
            if (!name.isEmpty()) names.put(name, names.containsKey(name) ? -1 : i);
            JSONArray children = nodes.getJSONObject(i).optJSONArray("children");
            if (children != null) for (int j = 0; j < children.length(); j++) {
                int child = children.getInt(j);
                if (child < 0 || child >= nodes.length() || child == i) fail("节点层次引用无效");
                if (parents[child] != -1) fail("模型节点存在多个父节点");
                parents[child] = i;
            }
        }
        for (int i = 0; i < parents.length; i++) {
            int current = i, steps = 0;
            while (current >= 0) {
                if (++steps > parents.length) fail("模型节点包含循环");
                current = parents[current];
            }
        }
        // 描述中的部件绑定必须消歧；以名称引用时只有唯一名称可以通过。
        JSONObject parts = descriptor.optJSONObject("parts");
        if (parts != null) {
            java.util.Iterator<String> keys = parts.keys();
            while (keys.hasNext()) {
                JSONObject ref = parts.getJSONObject(keys.next());
                if (ref.has("name")) {
                    Integer node = names.get(ref.getString("name"));
                    if (node == null || node < 0) fail("部件名称不存在或不唯一");
                } else if (ref.has("path")) checkNodePath(ref.getJSONArray("path"), nodes);
                else fail("部件必须按唯一名称或结构路径引用");
            }
        }
        JSONArray required = descriptor.optJSONArray("requiredExtensions");
        Set<String> declared = new HashSet<>();
        if (required != null) for (int i = 0; i < required.length(); i++) declared.add(required.getString(i));
        JSONArray modelRequired = gltf.optJSONArray("extensionsRequired");
        if (modelRequired != null) for (int i = 0; i < modelRequired.length(); i++) {
            String extension = modelRequired.getString(i);
            if (!KNOWN_REQUIRED.contains(extension) || !declared.contains(extension)) fail("缺少必需 glTF 扩展声明：" + extension);
        }
        for (String extension : declared) if (!KNOWN_REQUIRED.contains(extension)) fail("未知的必需 glTF 扩展：" + extension);
        JSONArray groups = descriptor.optJSONArray("animationGroups");
        JSONArray animations = gltf.optJSONArray("animations");
        Set<String> groupIds = new HashSet<>();
        if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            if (!groupIds.add(group.getString("id"))) fail("动画组标识重复");
            double start = group.getDouble("startSeconds"), end = group.getDouble("endSeconds");
            if (!finite(start) || !finite(end) || start < 0 || end <= start) fail("动画组公共时间无效");
            JSONArray clips = group.getJSONArray("clips");
            if (clips.length() == 0) fail("动画组没有片段");
            for (int j = 0; j < clips.length(); j++) {
                JSONObject clip = clips.getJSONObject(j);
                int index = clip.getInt("index");
                double offset = clip.getDouble("offsetSeconds");
                if (animations == null || index < 0 || index >= animations.length() || !finite(offset)) fail("动画片段引用无效");
            }
            // 开闭方向必须由作者确认，安装器不根据片段名称推断。
            String direction = group.getString("openDirection");
            if (!direction.equals("forward") && !direction.equals("reverse") && !direction.equals("unverified")) fail("动画方向声明无效");
        }
        JSONArray slots = descriptor.optJSONArray("materialSlots");
        JSONArray materials = gltf.optJSONArray("materials");
        if (slots != null) for (int i = 0; i < slots.length(); i++) {
            int index = slots.getJSONObject(i).getInt("materialIndex");
            if (materials == null || index < 0 || index >= materials.length()) fail("材质槽引用无效");
        }
        JSONArray bindings = descriptor.optJSONArray("stateBindings");
        if (bindings != null) for (int i = 0; i < bindings.length(); i++) {
            JSONObject binding = bindings.getJSONObject(i);
            if (!groupIds.contains(binding.getString("group")) || binding.getString("property").isEmpty()
                    || !binding.has("unit") || !binding.has("unknownBehavior")) fail("只读状态绑定不完整");
            String unknown = binding.getString("unknownBehavior");
            if (!unknown.equals("hold") && !unknown.equals("neutral")) fail("未知状态处理无效");
        }
    }
    private static void checkNodePath(JSONArray path, JSONArray nodes) throws JSONException, IOException {
        if (path.length() == 0 || path.length() > 128) fail("节点路径无效");
        int parent = -1;
        for (int i = 0; i < path.length(); i++) {
            int index = path.getInt(i);
            if (index < 0 || index >= nodes.length()) fail("节点路径索引无效");
            if (parent >= 0) {
                JSONArray children = nodes.getJSONObject(parent).optJSONArray("children");
                boolean found = false;
                if (children != null) for (int j = 0; j < children.length(); j++) found |= children.getInt(j) == index;
                if (!found) fail("节点路径不是实际父子关系");
            }
            parent = index;
        }
    }
    static JSONObject readGlb(File file) throws IOException, JSONException {
        if (!file.isFile() || file.length() < 20 || file.length() > 256 * PluginPackageVerifier.MIB) fail("GLB 文件大小无效");
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (u32(input) != 0x46546c67L || u32(input) != 2 || u32(input) != input.length()) fail("GLB 头无效");
            long jsonLength = u32(input), jsonType = u32(input);
            if (jsonType != 0x4e4f534aL || jsonLength <= 0 || jsonLength % 4 != 0
                    || jsonLength > 16 * PluginPackageVerifier.MIB || jsonLength + 20 > input.length()) fail("GLB JSON 块无效");
            byte[] json = new byte[(int) jsonLength]; input.readFully(json);
            JSONObject gltf = new JSONObject(new String(json, StandardCharsets.UTF_8));
            if (!gltf.getJSONObject("asset").getString("version").equals("2.0")) fail("仅支持 glTF 2.0");
            long binaryLength = 0;
            if (input.getFilePointer() < input.length()) {
                binaryLength = u32(input);
                if (u32(input) != 0x004e4942L || binaryLength % 4 != 0 || input.getFilePointer() + binaryLength != input.length()) fail("GLB 二进制块无效");
            }
            long binaryStart = input.getFilePointer();
            JSONArray buffers = gltf.optJSONArray("buffers");
            if (buffers == null || buffers.length() != 1) fail("模型必须使用一个内嵌缓冲");
            JSONObject buffer = buffers.getJSONObject(0);
            long declaredLength = buffer.getLong("byteLength");
            if (buffer.has("uri") || declaredLength <= 0 || declaredLength > binaryLength || binaryLength - declaredLength > 3) fail("模型缓冲不可外置或越界");
            JSONArray views = gltf.optJSONArray("bufferViews");
            if (views != null) for (int i = 0; i < views.length(); i++) {
                JSONObject view = views.getJSONObject(i);
                long offset = view.optLong("byteOffset"), length = view.getLong("byteLength");
                if (view.getInt("buffer") != 0 || offset < 0 || length <= 0 || offset > declaredLength - length) fail("缓冲视图越界");
            }
            JSONArray images = gltf.optJSONArray("images");
            long decodedTextureBytes = 0;
            if (images != null) for (int i = 0; i < images.length(); i++) {
                JSONObject image = images.getJSONObject(i);
                int view = image.optInt("bufferView", -1);
                if (image.has("uri") || views == null || view < 0 || view >= views.length()
                        || !Arrays.asList("image/jpeg", "image/png", "image/webp").contains(image.getString("mimeType"))) fail("贴图必须为包内受支持图片");
                JSONObject imageView = views.getJSONObject(view);
                long imageSize = imageView.getLong("byteLength");
                if (imageSize > 16 * PluginPackageVerifier.MIB) fail("模型压缩贴图大小超限");
                byte[] encoded = new byte[(int) imageSize];
                input.seek(binaryStart + imageView.optLong("byteOffset"));
                input.readFully(encoded);
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 4096 || options.outHeight > 4096) fail("模型贴图尺寸无效或超限");
                decodedTextureBytes += (long) options.outWidth * options.outHeight * 4;
                if (decodedTextureBytes > 256 * PluginPackageVerifier.MIB) fail("模型基础纹理解码预算超限");
            }
            JSONArray accessors = gltf.optJSONArray("accessors");
            if (accessors != null) for (int i = 0; i < accessors.length(); i++) {
                JSONObject accessor = accessors.getJSONObject(i);
                if (accessor.has("sparse")) fail("首版资源校验暂不接受稀疏访问器");
                int viewIndex = accessor.optInt("bufferView", -1);
                if (views == null || viewIndex < 0 || viewIndex >= views.length()) fail("访问器缺少内嵌视图");
                JSONObject view = views.getJSONObject(viewIndex);
                long count = accessor.getLong("count"), offset = accessor.optLong("byteOffset");
                int bytes = componentBytes(accessor.getInt("componentType"));
                int components = components(accessor.getString("type"));
                String type = accessor.getString("type");
                long element = (long) bytes * components;
                if (type.startsWith("MAT")) {
                    int columns = Integer.parseInt(type.substring(3));
                    element = (long) ((bytes * columns + 3) / 4 * 4) * columns;
                }
                long stride = view.optLong("byteStride", element);
                if (count <= 0 || count > 10_000_000 || offset < 0 || stride < element || stride > 252
                        || offset + (count - 1) * stride + element > view.getLong("byteLength")) fail("访问器范围无效");
            }
            checkGeometry(gltf, accessors);
            return gltf;
        }
    }
    private static int componentBytes(int value) throws PluginException {
        switch (value) {
            case 5120: case 5121: return 1;
            case 5122: case 5123: return 2;
            case 5125: case 5126: return 4;
            default: throw new PluginException("GLB", "访问器分量类型不支持");
        }
    }
    private static int components(String type) throws PluginException {
        switch (type) {
            case "SCALAR": return 1; case "VEC2": return 2; case "VEC3": return 3;
            case "VEC4": case "MAT2": return 4; case "MAT3": return 9; case "MAT4": return 16;
            default: throw new PluginException("GLB", "访问器维度不支持");
        }
    }
    private static long u32(RandomAccessFile input) throws IOException {
        byte[] bytes = new byte[4]; input.readFully(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL;
    }
    private static void checkGeometry(JSONObject gltf, JSONArray accessors) throws JSONException, IOException {
        JSONArray meshes = gltf.optJSONArray("meshes");
        long vertices = 0, triangles = 0;
        if (meshes != null) {
            if (meshes.length() > 2048) fail("模型网格过多");
            for (int i = 0; i < meshes.length(); i++) {
                JSONArray primitives = meshes.getJSONObject(i).getJSONArray("primitives");
                if (primitives.length() > 256) fail("网格图元过多");
                for (int j = 0; j < primitives.length(); j++) {
                    JSONObject primitive = primitives.getJSONObject(j);
                    int positionIndex = primitive.getJSONObject("attributes").getInt("POSITION");
                    long count = accessorCount(accessors, positionIndex);
                    vertices += count;
                    long indices = primitive.has("indices") ? accessorCount(accessors, primitive.getInt("indices")) : count;
                    int mode = primitive.optInt("mode", 4);
                    if (mode == 4) triangles += indices / 3;
                    else if (mode == 5 || mode == 6) triangles += Math.max(0, indices - 2);
                }
            }
        }
        if (vertices > 2_000_000 || triangles > 1_000_000) fail("模型几何预算超限");
        JSONArray animations = gltf.optJSONArray("animations");
        JSONArray nodes = gltf.optJSONArray("nodes");
        if (animations != null) {
            if (animations.length() > 256) fail("模型动画数量超限");
            for (int i = 0; i < animations.length(); i++) {
                JSONObject animation = animations.getJSONObject(i);
                JSONArray samplers = animation.getJSONArray("samplers");
                JSONArray channels = animation.getJSONArray("channels");
                if (samplers.length() > 4096 || channels.length() > 4096) fail("模型动画通道超限");
                for (int j = 0; j < samplers.length(); j++) {
                    JSONObject sampler = samplers.getJSONObject(j);
                    accessorCount(accessors, sampler.getInt("input"));
                    accessorCount(accessors, sampler.getInt("output"));
                }
                for (int j = 0; j < channels.length(); j++) {
                    JSONObject channel = channels.getJSONObject(j);
                    int sampler = channel.getInt("sampler");
                    int node = channel.getJSONObject("target").getInt("node");
                    if (sampler < 0 || sampler >= samplers.length() || nodes == null || node < 0 || node >= nodes.length()) fail("动画引用无效");
                }
            }
        }
    }
    private static long accessorCount(JSONArray accessors, int index) throws JSONException, IOException {
        if (accessors == null || index < 0 || index >= accessors.length()) fail("几何访问器引用无效");
        return accessors.getJSONObject(index).getLong("count");
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static void fail(String reason) throws PluginException { throw new PluginException("GLB", reason); }
}
