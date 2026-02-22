package org.meshforge.demo;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.KHRPortabilitySubset;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VKCapabilitiesDevice;
import org.lwjgl.vulkan.VKCapabilitiesInstance;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderFeaturesEXT;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.meshforge.api.Ops;
import org.meshforge.api.Packers;
import org.meshforge.api.Pipelines;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.packer.MeshPacker;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_infer_from_source;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;

/**
 * Headless Vulkan mesh-shader smoke renderer:
 * emits one tiny triangle per meshlet into an offscreen image and saves PNG + checksum.
 */
public final class MeshletDispatchDemo {
    private static final int WIDTH = 1024;
    private static final int HEIGHT = 1024;

    private static final String MESH_SHADER_SOURCE = """
        #version 460
        #extension GL_EXT_mesh_shader : require
        #pragma shader_stage(mesh)

        layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
        layout(max_vertices = 3, max_primitives = 1) out;
        layout(triangles) out;

        struct MeshletDesc {
            uint firstTriangle;
            uint triangleCount;
            uint firstIndex;
            uint indexCount;
            uint uniqueVertexCount;
            vec3 bmin;
            vec3 bmax;
            vec3 coneAxis;
            float coneCutoff;
        };

        layout(set = 0, binding = 0, std430) readonly buffer Meshlets {
            MeshletDesc meshlets[];
        } meshBuf;

        layout(push_constant) uniform Push {
            vec4 centerScale; // xyz center, w scale
            uint meshletCount;
            float triSize;
        } pc;

        layout(location = 0) out vec3 outColor[];

        void main() {
            uint mid = gl_GlobalInvocationID.x;
            if (mid >= pc.meshletCount) {
                return;
            }

            MeshletDesc m = meshBuf.meshlets[mid];
            vec3 c = (m.bmin + m.bmax) * 0.5;
            vec3 ndc = (c - pc.centerScale.xyz) / max(pc.centerScale.w, 1e-5);

            float s = pc.triSize;
            vec3 v0 = vec3(ndc.x - s, ndc.y - s * 0.6, 0.0);
            vec3 v1 = vec3(ndc.x + s, ndc.y - s * 0.6, 0.0);
            vec3 v2 = vec3(ndc.x, ndc.y + s, 0.0);

            SetMeshOutputsEXT(3, 1);
            gl_MeshVerticesEXT[0].gl_Position = vec4(v0, 1.0);
            gl_MeshVerticesEXT[1].gl_Position = vec4(v1, 1.0);
            gl_MeshVerticesEXT[2].gl_Position = vec4(v2, 1.0);

            vec3 col = vec3(
                float((mid * 37u) % 255u) / 255.0,
                float((mid * 67u) % 255u) / 255.0,
                float((mid * 97u) % 255u) / 255.0
            );

            outColor[0] = col;
            outColor[1] = col;
            outColor[2] = col;
            gl_PrimitiveTriangleIndicesEXT[0] = uvec3(0, 1, 2);
        }
        """;

    private static final String FRAGMENT_SHADER_SOURCE = """
        #version 460
        #pragma shader_stage(fragment)
        layout(location = 0) in vec3 inColor;
        layout(location = 0) out vec4 outColor;
        void main() {
            outColor = vec4(inColor, 1.0);
        }
        """;

    private MeshletDispatchDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MeshletDispatchDemo <path-to-obj>");
            return;
        }

        if (!VulkanPreflight.checkVulkanLoader()) {
            VulkanPreflight.printMacOsSetupHint();
            return;
        }

        PackedMesh packed = loadPacked(Path.of(args[0]));
        if (!packed.hasMeshlets()) {
            System.out.println("Mesh has no meshlets; pack with realtimeWithMeshlets first.");
            return;
        }

        try (VulkanContext ctx = VulkanContext.create()) {
            RenderOutput out = ctx.renderMeshletTriangles(packed);
            writePng(out.rgba(), WIDTH, HEIGHT, Path.of("perf/results/meshlet_output.png"));
            CRC32 crc = new CRC32();
            crc.update(out.rgba());
            System.out.println("Rendered offscreen meshlet image:");
            System.out.println("  Device: " + ctx.deviceName);
            System.out.println("  Meshlets: " + packed.meshletsOrNull().meshletCount());
            System.out.println("  Output: perf/results/meshlet_output.png");
            System.out.printf("  RGBA CRC32: 0x%08x%n", crc.getValue());
            System.out.printf("  Avg luminance: %.5f%n", out.avgLuminance());
        } catch (UnsupportedOperationException ex) {
            System.out.println("Mesh-shader dispatch stub skipped: " + ex.getMessage());
        } catch (Throwable ex) {
            System.out.println("Mesh-shader dispatch stub unavailable: " + ex.getMessage());
            System.out.println("Hint: install/configure a Vulkan loader (for macOS typically MoltenVK/libvulkan).");
        }
    }

    private static PackedMesh loadPacked(Path path) throws Exception {
        var mesh = MeshLoaders.defaults().load(path);
        mesh = Pipelines.realtimeFast(mesh);
        mesh = MeshPipeline.run(mesh, Ops.clusterizeMeshlets(128, 64));
        mesh = MeshPipeline.run(mesh, Ops.optimizeMeshletOrder());
        return MeshPacker.pack(mesh, Packers.realtimeWithMeshlets());
    }

    private static void writePng(ByteBuffer rgba, int width, int height, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer src = rgba.duplicate();
        src.clear();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = ((y * width) + x) * 4;
                int r = src.get(i) & 0xFF;
                int g = src.get(i + 1) & 0xFF;
                int b = src.get(i + 2) & 0xFF;
                int a = src.get(i + 3) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, height - 1 - y, argb);
            }
        }
        ImageIO.write(img, "png", path.toFile());
    }

    private static final class VulkanContext implements AutoCloseable {
        private final VkInstance instance;
        private final VkPhysicalDevice physicalDevice;
        private final VkDevice device;
        private final VkQueue queue;
        private final int queueFamilyIndex;
        private final long commandPool;
        private final String deviceName;
        private final VKCapabilitiesDevice deviceCapabilities;
        private final VkPhysicalDeviceMemoryProperties memoryProps;

        private VulkanContext(
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            VkQueue queue,
            int queueFamilyIndex,
            long commandPool,
            String deviceName,
            VKCapabilitiesDevice deviceCapabilities,
            VkPhysicalDeviceMemoryProperties memoryProps
        ) {
            this.instance = instance;
            this.physicalDevice = physicalDevice;
            this.device = device;
            this.queue = queue;
            this.queueFamilyIndex = queueFamilyIndex;
            this.commandPool = commandPool;
            this.deviceName = deviceName;
            this.deviceCapabilities = deviceCapabilities;
            this.memoryProps = memoryProps;
        }

        private static VulkanContext create() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkInstance instance = createInstance(stack);
                VKCapabilitiesInstance instanceCaps = instance.getCapabilities();

                PhysicalSelection selection = pickPhysicalDevice(instance, stack);
                if (selection == null) {
                    VK10.vkDestroyInstance(instance, null);
                    throw new UnsupportedOperationException("no Vulkan physical device with graphics queue + VK_EXT_mesh_shader");
                }

                DeviceCreationResult created = createDevice(
                    selection.device,
                    selection.queueFamilyIndex,
                    selection.deviceExtensions,
                    instanceCaps,
                    stack
                );

                VkQueue queue = getQueue(created.device, selection.queueFamilyIndex, stack);
                long commandPool = createCommandPool(created.device, selection.queueFamilyIndex, stack);

                VkPhysicalDeviceMemoryProperties mem = VkPhysicalDeviceMemoryProperties.calloc();
                VK10.vkGetPhysicalDeviceMemoryProperties(selection.device, mem);

                return new VulkanContext(
                    instance,
                    selection.device,
                    created.device,
                    queue,
                    selection.queueFamilyIndex,
                    commandPool,
                    selection.deviceName,
                    created.capabilities,
                    mem
                );
            }
        }

        private RenderOutput renderMeshletTriangles(PackedMesh packed) {
            ByteBuffer descriptorSrc = packed.meshletDescriptorBufferOrNull();
            if (descriptorSrc == null || descriptorSrc.remaining() == 0) {
                throw new IllegalArgumentException("No meshlet descriptor buffer present");
            }

            long descriptorSetLayout = VK10.VK_NULL_HANDLE;
            long descriptorPool = VK10.VK_NULL_HANDLE;
            long descriptorSet = VK10.VK_NULL_HANDLE;
            long pipelineLayout = VK10.VK_NULL_HANDLE;
            long renderPass = VK10.VK_NULL_HANDLE;
            long pipeline = VK10.VK_NULL_HANDLE;
            long meshShaderModule = VK10.VK_NULL_HANDLE;
            long fragShaderModule = VK10.VK_NULL_HANDLE;
            BufferAllocation ssbo = null;
            ImageAllocation colorImage = null;
            BufferAllocation readback = null;
            long imageView = VK10.VK_NULL_HANDLE;
            long framebuffer = VK10.VK_NULL_HANDLE;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ssbo = createBuffer(
                    descriptorSrc.remaining(),
                    VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );
                upload(ssbo, descriptorSrc);

                colorImage = createColorAttachmentImage(WIDTH, HEIGHT);
                imageView = createImageView(colorImage.image, VK10.VK_FORMAT_R8G8B8A8_UNORM);

                readback = createBuffer(
                    WIDTH * HEIGHT * 4L,
                    VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                );

                descriptorSetLayout = createDescriptorSetLayout();
                pipelineLayout = createPipelineLayout(descriptorSetLayout);
                descriptorPool = createDescriptorPool();
                descriptorSet = allocateDescriptorSet(descriptorPool, descriptorSetLayout);
                updateDescriptorSet(descriptorSet, ssbo.buffer, descriptorSrc.remaining());

                meshShaderModule = createShaderModule(compileToSpirv(MESH_SHADER_SOURCE, "meshlet_triangle.mesh.glsl"));
                fragShaderModule = createShaderModule(compileToSpirv(FRAGMENT_SHADER_SOURCE, "meshlet_triangle.frag.glsl"));

                renderPass = createRenderPass();
                framebuffer = createFramebuffer(renderPass, imageView, WIDTH, HEIGHT);
                pipeline = createPipeline(renderPass, pipelineLayout, meshShaderModule, fragShaderModule);

                VkCommandBuffer cmd = allocateCommandBuffer(stack);
                beginCommandBuffer(cmd, stack);

                transitionImage(
                    cmd,
                    colorImage.image,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    0,
                    VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                    VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                );

                beginRenderPass(cmd, renderPass, framebuffer, stack);
                VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
                VK10.vkCmdBindDescriptorSets(
                    cmd,
                    VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout,
                    0,
                    stack.longs(descriptorSet),
                    null
                );

                PushConstants pc = computePushConstants(packed);
                ByteBuffer push = stack.malloc(24);
                push.putFloat(pc.centerX).putFloat(pc.centerY).putFloat(pc.centerZ).putFloat(pc.scale);
                push.putInt(pc.meshletCount).putFloat(pc.triSize);
                push.flip();
                VK10.vkCmdPushConstants(cmd, pipelineLayout, EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT, 0, push);

                EXTMeshShader.vkCmdDrawMeshTasksEXT(cmd, pc.meshletCount, 1, 1);
                VK10.vkCmdEndRenderPass(cmd);

                transitionImage(
                    cmd,
                    colorImage.image,
                    VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                    VK10.VK_ACCESS_TRANSFER_READ_BIT,
                    VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
                );

                copyImageToBuffer(cmd, colorImage.image, readback.buffer, WIDTH, HEIGHT, stack);

                endAndSubmit(cmd, stack);
                ByteBuffer pixels = map(readback, (int) (WIDTH * HEIGHT * 4L));
                double avgLum = averageLuminance(pixels);
                return new RenderOutput(pixels, avgLum);
            } finally {
                if (framebuffer != VK10.VK_NULL_HANDLE) VK10.vkDestroyFramebuffer(device, framebuffer, null);
                if (pipeline != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipeline(device, pipeline, null);
                if (renderPass != VK10.VK_NULL_HANDLE) VK10.vkDestroyRenderPass(device, renderPass, null);
                if (meshShaderModule != VK10.VK_NULL_HANDLE) VK10.vkDestroyShaderModule(device, meshShaderModule, null);
                if (fragShaderModule != VK10.VK_NULL_HANDLE) VK10.vkDestroyShaderModule(device, fragShaderModule, null);
                if (pipelineLayout != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
                if (descriptorPool != VK10.VK_NULL_HANDLE) VK10.vkDestroyDescriptorPool(device, descriptorPool, null);
                if (descriptorSetLayout != VK10.VK_NULL_HANDLE) VK10.vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
                if (imageView != VK10.VK_NULL_HANDLE) VK10.vkDestroyImageView(device, imageView, null);
                if (colorImage != null) destroy(colorImage);
                if (ssbo != null) destroy(ssbo);
                if (readback != null) destroy(readback);
            }
        }

        private PushConstants computePushConstants(PackedMesh packed) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            var meshlets = packed.meshletsOrNull();
            for (var meshlet : meshlets.asList()) {
                var b = meshlet.bounds();
                minX = Math.min(minX, b.minX());
                minY = Math.min(minY, b.minY());
                minZ = Math.min(minZ, b.minZ());
                maxX = Math.max(maxX, b.maxX());
                maxY = Math.max(maxY, b.maxY());
                maxZ = Math.max(maxZ, b.maxZ());
            }
            float cx = (minX + maxX) * 0.5f;
            float cy = (minY + maxY) * 0.5f;
            float cz = (minZ + maxZ) * 0.5f;
            float ex = maxX - minX;
            float ey = maxY - minY;
            float ez = maxZ - minZ;
            float scale = Math.max(1e-4f, Math.max(ex, Math.max(ey, ez)) * 0.55f);
            return new PushConstants(cx, cy, cz, scale, meshlets.meshletCount(), 0.01f);
        }

        private static VkInstance createInstance(MemoryStack stack) {
            VkApplicationInfo app = VkApplicationInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("MeshForgeMeshletRenderStub"))
                .applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("MeshForge"))
                .engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK12.VK_API_VERSION_1_2);

            PointerBuffer extBuffer = stack.mallocPointer(1);
            extBuffer.put(stack.UTF8(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME));
            extBuffer.flip();

            VkInstanceCreateInfo info = VkInstanceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(app)
                .ppEnabledExtensionNames(extBuffer)
                .flags(KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR);

            PointerBuffer pInstance = stack.mallocPointer(1);
            int err = VK10.vkCreateInstance(info, null, pInstance);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkCreateInstance failed: " + err);
            }
            return new VkInstance(pInstance.get(0), info);
        }

        private static PhysicalSelection pickPhysicalDevice(VkInstance instance, MemoryStack stack) {
            IntBuffer pCount = stack.ints(0);
            int err = VK10.vkEnumeratePhysicalDevices(instance, pCount, null);
            if (err != VK10.VK_SUCCESS || pCount.get(0) == 0) {
                return null;
            }
            PointerBuffer devices = stack.mallocPointer(pCount.get(0));
            VK10.vkEnumeratePhysicalDevices(instance, pCount, devices);

            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice pd = new VkPhysicalDevice(devices.get(i), instance);
                int queueFamily = findGraphicsQueueFamily(pd, stack);
                if (queueFamily < 0) {
                    continue;
                }
                List<String> exts = enumerateDeviceExtensions(pd, stack);
                if (!exts.contains(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME)) {
                    continue;
                }
                var props = org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceProperties(pd, props);
                String name = props.deviceNameString();
                return new PhysicalSelection(pd, queueFamily, exts, name);
            }
            return null;
        }

        private static int findGraphicsQueueFamily(VkPhysicalDevice physicalDevice, MemoryStack stack) {
            IntBuffer pQueueFamilyCount = stack.ints(0);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, null);
            var props = org.lwjgl.vulkan.VkQueueFamilyProperties.calloc(pQueueFamilyCount.get(0), stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, props);
            for (int i = 0; i < props.capacity(); i++) {
                if ((props.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    return i;
                }
            }
            return -1;
        }

        private static List<String> enumerateDeviceExtensions(VkPhysicalDevice physicalDevice, MemoryStack stack) {
            IntBuffer pExtCount = stack.ints(0);
            VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, pExtCount, null);
            VkExtensionProperties.Buffer exts = VkExtensionProperties.calloc(pExtCount.get(0), stack);
            VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, pExtCount, exts);
            List<String> names = new ArrayList<>(exts.capacity());
            for (int i = 0; i < exts.capacity(); i++) {
                names.add(exts.get(i).extensionNameString());
            }
            return names;
        }

        private static DeviceCreationResult createDevice(
            VkPhysicalDevice physicalDevice,
            int queueFamilyIndex,
            List<String> availableExtensions,
            VKCapabilitiesInstance instanceCaps,
            MemoryStack stack
        ) {
            var pQueuePriority = stack.floats(1.0f);
            VkDeviceQueueCreateInfo.Buffer qci = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(queueFamilyIndex)
                .pQueuePriorities(pQueuePriority);

            List<String> enabledExts = new ArrayList<>();
            enabledExts.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);
            if (availableExtensions.contains(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME)) {
                enabledExts.add(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME);
            }

            PointerBuffer ppEnabledExts = stack.mallocPointer(enabledExts.size());
            for (String ext : enabledExts) {
                ppEnabledExts.put(stack.UTF8(ext));
            }
            ppEnabledExts.flip();

            VkPhysicalDeviceMeshShaderFeaturesEXT meshFeatures = VkPhysicalDeviceMeshShaderFeaturesEXT.calloc(stack)
                .sType(EXTMeshShader.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_FEATURES_EXT)
                .meshShader(true)
                .taskShader(false);

            VkDeviceCreateInfo dci = VkDeviceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(qci)
                .ppEnabledExtensionNames(ppEnabledExts)
                .pNext(meshFeatures.address());

            PointerBuffer pDevice = stack.mallocPointer(1);
            int err = VK10.vkCreateDevice(physicalDevice, dci, null, pDevice);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDevice failed: " + err);
            }
            VkDevice device = new VkDevice(pDevice.get(0), physicalDevice, dci);
            VKCapabilitiesDevice caps = device.getCapabilities();
            return new DeviceCreationResult(device, caps);
        }

        private static VkQueue getQueue(VkDevice device, int queueFamilyIndex, MemoryStack stack) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(device, queueFamilyIndex, 0, pQueue);
            return new VkQueue(pQueue.get(0), device);
        }

        private static long createCommandPool(VkDevice device, int queueFamilyIndex, MemoryStack stack) {
            VkCommandPoolCreateInfo cpci = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .queueFamilyIndex(queueFamilyIndex)
                .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            LongBuffer pCommandPool = stack.mallocLong(1);
            int err = VK10.vkCreateCommandPool(device, cpci, null, pCommandPool);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkCreateCommandPool failed: " + err);
            }
            return pCommandPool.get(0);
        }

        private VkCommandBuffer allocateCommandBuffer(MemoryStack stack) {
            VkCommandBufferAllocateInfo ai = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
            PointerBuffer pCmd = stack.mallocPointer(1);
            int err = VK10.vkAllocateCommandBuffers(device, ai, pCmd);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateCommandBuffers failed: " + err);
            }
            return new VkCommandBuffer(pCmd.get(0), device);
        }

        private void beginCommandBuffer(VkCommandBuffer cmd, MemoryStack stack) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            int err = VK10.vkBeginCommandBuffer(cmd, begin);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkBeginCommandBuffer failed: " + err);
            }
        }

        private void endAndSubmit(VkCommandBuffer cmd, MemoryStack stack) {
            int err = VK10.vkEndCommandBuffer(cmd);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkEndCommandBuffer failed: " + err);
            }
            PointerBuffer pCmd = stack.pointers(cmd.address());
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(pCmd);
            err = VK10.vkQueueSubmit(queue, submit, VK10.VK_NULL_HANDLE);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkQueueSubmit failed: " + err);
            }
            VK10.vkQueueWaitIdle(queue);
            VK10.vkFreeCommandBuffers(device, commandPool, pCmd);
        }

        private void beginRenderPass(VkCommandBuffer cmd, long renderPass, long framebuffer, MemoryStack stack) {
            VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
            clearValues.get(0).color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1.0f);
            VkRenderPassBeginInfo rpbi = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass)
                .framebuffer(framebuffer)
                .renderArea(VkRect2D.calloc(stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(VkExtent2D.calloc(stack).set(WIDTH, HEIGHT)))
                .pClearValues(clearValues);
            VK10.vkCmdBeginRenderPass(cmd, rpbi, VK10.VK_SUBPASS_CONTENTS_INLINE);
        }

        private long createDescriptorSetLayout() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
                bindings.get(0)
                    .binding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT);

                VkDescriptorSetLayoutCreateInfo ci = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings);
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateDescriptorSetLayout(device, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateDescriptorSetLayout failed: " + err);
                }
                return out.get(0);
            }
        }

        private long createPipelineLayout(long descriptorSetLayout) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
                pushRange.get(0)
                    .stageFlags(EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                    .offset(0)
                    .size(24);

                VkPipelineLayoutCreateInfo ci = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(pushRange);
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreatePipelineLayout(device, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreatePipelineLayout failed: " + err);
                }
                return out.get(0);
            }
        }

        private long createDescriptorPool() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(1, stack);
                sizes.get(0).type(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
                VkDescriptorPoolCreateInfo ci = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(sizes)
                    .maxSets(1);
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateDescriptorPool(device, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateDescriptorPool failed: " + err);
                }
                return out.get(0);
            }
        }

        private long allocateDescriptorSet(long descriptorPool, long descriptorSetLayout) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetAllocateInfo ai = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkAllocateDescriptorSets(device, ai, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkAllocateDescriptorSets failed: " + err);
                }
                return out.get(0);
            }
        }

        private void updateDescriptorSet(long descriptorSet, long buffer, int sizeBytes) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack);
                info.get(0).buffer(buffer).offset(0).range(sizeBytes);

                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
                writes.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(info);
                VK10.vkUpdateDescriptorSets(device, writes, null);
            }
        }

        private long createRenderPass() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkAttachmentDescription.Buffer atts = VkAttachmentDescription.calloc(1, stack);
                atts.get(0)
                    .format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

                VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack);
                colorRef.get(0).attachment(0).layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

                VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
                subpasses.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef);

                VkRenderPassCreateInfo ci = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(atts)
                    .pSubpasses(subpasses);
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateRenderPass(device, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateRenderPass failed: " + err);
                }
                return out.get(0);
            }
        }

        private long createFramebuffer(long renderPass, long imageView, int width, int height) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkFramebufferCreateInfo ci = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .pAttachments(stack.longs(imageView))
                    .width(width)
                    .height(height)
                    .layers(1);
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateFramebuffer(device, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateFramebuffer failed: " + err);
                }
                return out.get(0);
            }
        }

        private long createPipeline(long renderPass, long pipelineLayout, long meshModule, long fragModule) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                    .module(meshModule)
                    .pName(stack.UTF8("main"));
                stages.get(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .pViewports(VkViewport.calloc(1, stack)
                        .x(0.0f)
                        .y(0.0f)
                        .width((float) WIDTH)
                        .height((float) HEIGHT)
                        .minDepth(0.0f)
                        .maxDepth(1.0f))
                    .scissorCount(1)
                    .pScissors(VkRect2D.calloc(1, stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(VkExtent2D.calloc(stack).set(WIDTH, HEIGHT)));

                VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                    .cullMode(VK10.VK_CULL_MODE_NONE)
                    .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .lineWidth(1.0f);

                VkPipelineMultisampleStateCreateInfo msaa = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

                VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                blendAttachment.get(0)
                    .colorWriteMask(
                        VK10.VK_COLOR_COMPONENT_R_BIT |
                            VK10.VK_COLOR_COMPONENT_G_BIT |
                            VK10.VK_COLOR_COMPONENT_B_BIT |
                            VK10.VK_COLOR_COMPONENT_A_BIT
                    )
                    .blendEnable(false);

                VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .pAttachments(blendAttachment);

                VkGraphicsPipelineCreateInfo.Buffer ci = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                ci.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pViewportState(viewportState)
                    .pRasterizationState(raster)
                    .pMultisampleState(msaa)
                    .pColorBlendState(blend)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);

                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateGraphicsPipelines(device, VK10.VK_NULL_HANDLE, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateGraphicsPipelines failed: " + err);
                }
                return out.get(0);
            }
        }

        private long createShaderModule(ByteBuffer spirvBytes) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo ci = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(spirvBytes.order(java.nio.ByteOrder.nativeOrder()));
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateShaderModule(device, ci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateShaderModule failed: " + err);
                }
                return out.get(0);
            }
        }

        private static ByteBuffer compileToSpirv(String source, String name) {
            long compiler = shaderc_compiler_initialize();
            if (compiler == MemoryUtil.NULL) {
                throw new IllegalStateException("shaderc_compiler_initialize failed");
            }
            long result = shaderc_compile_into_spv(
                compiler,
                source,
                shaderc_glsl_infer_from_source,
                name,
                "main",
                MemoryUtil.NULL
            );
            if (result == MemoryUtil.NULL) {
                shaderc_compiler_release(compiler);
                throw new IllegalStateException("shaderc_compile_into_spv failed");
            }
            try {
                int status = shaderc_result_get_compilation_status(result);
                if (status != shaderc_compilation_status_success) {
                    String msg = shaderc_result_get_error_message(result);
                    throw new IllegalStateException("Shader compile failed (" + name + "): " + msg);
                }
                ByteBuffer bytes = shaderc_result_get_bytes(result);
                ByteBuffer out = MemoryUtil.memAlloc(bytes.remaining());
                out.put(bytes).flip();
                return out;
            } finally {
                shaderc_result_release(result);
                shaderc_compiler_release(compiler);
            }
        }

        private BufferAllocation createBuffer(long size, int usage, int memoryFlags) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCreateInfo bci = VkBufferCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(usage | VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer pBuffer = stack.mallocLong(1);
                int err = VK10.vkCreateBuffer(device, bci, null, pBuffer);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateBuffer failed: " + err);
                }
                long buffer = pBuffer.get(0);

                VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
                VK10.vkGetBufferMemoryRequirements(device, buffer, req);
                int memoryType = findMemoryType(req.memoryTypeBits(), memoryFlags);
                VkMemoryAllocateInfo mai = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(req.size())
                    .memoryTypeIndex(memoryType);
                LongBuffer pMem = stack.mallocLong(1);
                err = VK10.vkAllocateMemory(device, mai, null, pMem);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkAllocateMemory(buffer) failed: " + err);
                }
                long memory = pMem.get(0);
                err = VK10.vkBindBufferMemory(device, buffer, memory, 0);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkBindBufferMemory failed: " + err);
                }
                return new BufferAllocation(buffer, memory, size);
            }
        }

        private ImageAllocation createColorAttachmentImage(int width, int height) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageCreateInfo ici = VkImageCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .extent(e -> e.width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer pImage = stack.mallocLong(1);
                int err = VK10.vkCreateImage(device, ici, null, pImage);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateImage failed: " + err);
                }
                long image = pImage.get(0);

                VkMemoryRequirements req = VkMemoryRequirements.calloc(stack);
                VK10.vkGetImageMemoryRequirements(device, image, req);
                int memoryType = findMemoryType(req.memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                VkMemoryAllocateInfo mai = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(req.size())
                    .memoryTypeIndex(memoryType);
                LongBuffer pMem = stack.mallocLong(1);
                err = VK10.vkAllocateMemory(device, mai, null, pMem);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkAllocateMemory(image) failed: " + err);
                }
                long memory = pMem.get(0);
                err = VK10.vkBindImageMemory(device, image, memory, 0);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkBindImageMemory failed: " + err);
                }
                return new ImageAllocation(image, memory);
            }
        }

        private long createImageView(long image, int format) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageViewCreateInfo ivci = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1));
                LongBuffer out = stack.mallocLong(1);
                int err = VK10.vkCreateImageView(device, ivci, null, out);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateImageView failed: " + err);
                }
                return out.get(0);
            }
        }

        private int findMemoryType(int typeBits, int propertyFlags) {
            for (int i = 0; i < memoryProps.memoryTypeCount(); i++) {
                boolean typeOk = (typeBits & (1 << i)) != 0;
                boolean propsOk = (memoryProps.memoryTypes(i).propertyFlags() & propertyFlags) == propertyFlags;
                if (typeOk && propsOk) {
                    return i;
                }
            }
            throw new IllegalStateException("No suitable Vulkan memory type for flags " + propertyFlags);
        }

        private void upload(BufferAllocation allocation, ByteBuffer source) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pp = stack.mallocPointer(1);
                int err = VK10.vkMapMemory(device, allocation.memory, 0, allocation.size, 0, pp);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkMapMemory(upload) failed: " + err);
                }
                ByteBuffer dst = MemoryUtil.memByteBuffer(pp.get(0), (int) allocation.size);
                ByteBuffer src = source.duplicate();
                src.clear();
                dst.put(src);
                dst.flip();
                VK10.vkUnmapMemory(device, allocation.memory);
            }
        }

        private ByteBuffer map(BufferAllocation allocation, int byteSize) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pp = stack.mallocPointer(1);
                int err = VK10.vkMapMemory(device, allocation.memory, 0, byteSize, 0, pp);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkMapMemory(readback) failed: " + err);
                }
                ByteBuffer src = MemoryUtil.memByteBuffer(pp.get(0), byteSize);
                ByteBuffer out = MemoryUtil.memAlloc(byteSize);
                out.put(src).flip();
                VK10.vkUnmapMemory(device, allocation.memory);
                return out;
            }
        }

        private void transitionImage(
            VkCommandBuffer cmd,
            long image,
            int oldLayout,
            int newLayout,
            int srcAccessMask,
            int dstAccessMask,
            int srcStageMask,
            int dstStageMask
        ) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
                barrier.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(srcAccessMask)
                    .dstAccessMask(dstAccessMask)
                    .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1));

                VK10.vkCmdPipelineBarrier(
                    cmd,
                    srcStageMask,
                    dstStageMask,
                    0,
                    null,
                    null,
                    barrier
                );
            }
        }

        private void copyImageToBuffer(VkCommandBuffer cmd, long image, long buffer, int width, int height, MemoryStack stack) {
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0)
                .bufferOffset(0)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageSubresource(s -> s.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0).layerCount(1))
                .imageOffset(o -> o.set(0, 0, 0))
                .imageExtent(e -> e.width(width).height(height).depth(1));
            VK10.vkCmdCopyImageToBuffer(cmd, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, region);
        }

        private static double averageLuminance(ByteBuffer rgba) {
            ByteBuffer b = rgba.duplicate();
            b.clear();
            double sum = 0.0;
            int px = b.remaining() / 4;
            for (int i = 0; i < px; i++) {
                int o = i * 4;
                int r = b.get(o) & 0xFF;
                int g = b.get(o + 1) & 0xFF;
                int bl = b.get(o + 2) & 0xFF;
                sum += (0.2126 * r + 0.7152 * g + 0.0722 * bl) / 255.0;
            }
            return px == 0 ? 0.0 : (sum / px);
        }

        private void destroy(BufferAllocation allocation) {
            VK10.vkDestroyBuffer(device, allocation.buffer, null);
            VK10.vkFreeMemory(device, allocation.memory, null);
        }

        private void destroy(ImageAllocation image) {
            VK10.vkDestroyImage(device, image.image, null);
            VK10.vkFreeMemory(device, image.memory, null);
        }

        @Override
        public void close() {
            VK10.vkDestroyCommandPool(device, commandPool, null);
            VK10.vkDestroyDevice(device, null);
            VK10.vkDestroyInstance(instance, null);
            memoryProps.free();
        }

        private record PhysicalSelection(
            VkPhysicalDevice device,
            int queueFamilyIndex,
            List<String> deviceExtensions,
            String deviceName
        ) {
        }

        private record DeviceCreationResult(VkDevice device, VKCapabilitiesDevice capabilities) {
        }

        private record BufferAllocation(long buffer, long memory, long size) {
        }

        private record ImageAllocation(long image, long memory) {
        }

        private record PushConstants(float centerX, float centerY, float centerZ, float scale, int meshletCount, float triSize) {
        }
    }

    private record RenderOutput(ByteBuffer rgba, double avgLuminance) {
    }
}
