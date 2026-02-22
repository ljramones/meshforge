package org.meshforge.demo;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.KHRPortabilitySubset;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VKCapabilitiesDevice;
import org.lwjgl.vulkan.VKCapabilitiesInstance;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderFeaturesEXT;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.meshforge.api.Ops;
import org.meshforge.api.Packers;
import org.meshforge.api.Pipelines;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.packer.MeshPacker;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal headless Vulkan mesh-shader readiness demo.
 * This validates VK_EXT_mesh_shader support and command queue submission using meshlet-packed data.
 */
public final class MeshletDispatchDemo {
    private MeshletDispatchDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MeshletDispatchDemo <path-to-obj>");
            return;
        }

        PackedMesh packed = loadPacked(Path.of(args[0]));
        if (!packed.hasMeshlets()) {
            System.out.println("Mesh has no meshlets; pack with realtimeWithMeshlets first.");
            return;
        }

        try (VulkanContext ctx = VulkanContext.create()) {
            int meshletCount = packed.meshletsOrNull().meshletCount();
            boolean hasEntryPoint = ctx.deviceCapabilities.vkCmdDrawMeshTasksEXT != 0L;

            ctx.submitEmptyWork();

            System.out.println("Vulkan mesh-shader readiness:");
            System.out.println("  Device: " + ctx.deviceName);
            System.out.println("  VK_EXT_mesh_shader: enabled");
            System.out.println("  vkCmdDrawMeshTasksEXT entrypoint: " + (hasEntryPoint ? "available" : "missing"));
            System.out.println("  Meshlet descriptors: " + meshletCount);
            System.out.println("  Command queue submit: ok");
            System.out.println("Ready: can bind descriptor/vertex/index buffers and dispatch mesh tasks in next step.");
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

    private static final class VulkanContext implements AutoCloseable {
        private final VkInstance instance;
        private final VkPhysicalDevice physicalDevice;
        private final VkDevice device;
        private final VkQueue queue;
        private final int queueFamilyIndex;
        private final long commandPool;
        private final String deviceName;
        private final VKCapabilitiesDevice deviceCapabilities;

        private VulkanContext(
            VkInstance instance,
            VkPhysicalDevice physicalDevice,
            VkDevice device,
            VkQueue queue,
            int queueFamilyIndex,
            long commandPool,
            String deviceName,
            VKCapabilitiesDevice deviceCapabilities
        ) {
            this.instance = instance;
            this.physicalDevice = physicalDevice;
            this.device = device;
            this.queue = queue;
            this.queueFamilyIndex = queueFamilyIndex;
            this.commandPool = commandPool;
            this.deviceName = deviceName;
            this.deviceCapabilities = deviceCapabilities;
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

                return new VulkanContext(
                    instance,
                    selection.device,
                    created.device,
                    queue,
                    selection.queueFamilyIndex,
                    commandPool,
                    selection.deviceName,
                    created.capabilities
                );
            }
        }

        private static VkInstance createInstance(MemoryStack stack) {
            VkApplicationInfo app = VkApplicationInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("MeshForgeMeshletDispatchStub"))
                .applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("MeshForge"))
                .engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK12.VK_API_VERSION_1_2);

            List<String> exts = new ArrayList<>();
            exts.add(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);

            PointerBuffer extBuffer = stack.mallocPointer(exts.size());
            for (String ext : exts) {
                extBuffer.put(stack.UTF8(ext));
            }
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

        private void submitEmptyWork() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkCommandBufferAllocateInfo cbai = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
                PointerBuffer pCmd = stack.mallocPointer(1);
                int err = VK10.vkAllocateCommandBuffers(device, cbai, pCmd);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkAllocateCommandBuffers failed: " + err);
                }
                VkCommandBuffer cmd = new VkCommandBuffer(pCmd.get(0), device);

                VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
                err = VK10.vkBeginCommandBuffer(cmd, begin);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkBeginCommandBuffer failed: " + err);
                }
                err = VK10.vkEndCommandBuffer(cmd);
                if (err != VK10.VK_SUCCESS) {
                    throw new IllegalStateException("vkEndCommandBuffer failed: " + err);
                }

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
        }

        @Override
        public void close() {
            VK10.vkDestroyCommandPool(device, commandPool, null);
            VK10.vkDestroyDevice(device, null);
            VK10.vkDestroyInstance(instance, null);
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
    }
}
