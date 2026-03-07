package org.dynamisengine.meshforge.demo;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

/**
 * Minimal Vulkan loader preflight for MeshletDispatchDemo.
 */
public final class VulkanPreflight {
    private VulkanPreflight() {
    }

    public static boolean checkVulkanLoader() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo app = VkApplicationInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("MeshForgeVulkanPreflight"))
                .applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("MeshForge"))
                .engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK12.VK_API_VERSION_1_2);

            VkInstanceCreateInfo ci = VkInstanceCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(app)
                .flags(KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR);

            PointerBuffer pInstance = stack.mallocPointer(1);
            int err = VK10.vkCreateInstance(ci, null, pInstance);
            if (err != VK10.VK_SUCCESS) {
                System.out.println("Vulkan preflight failed: vkCreateInstance returned " + err + " (" + vkResult(err) + ")");
                return false;
            }

            VkInstance instance = new VkInstance(pInstance.get(0), ci);
            VK10.vkDestroyInstance(instance, null);
            System.out.println("Vulkan preflight OK: loader found and vkCreateInstance succeeded.");
            return true;
        } catch (UnsatisfiedLinkError e) {
            System.out.println("Vulkan preflight failed: Vulkan loader not found (" + e.getMessage() + ")");
            return false;
        } catch (Throwable t) {
            System.out.println("Vulkan preflight failed: " + t.getMessage());
            return false;
        }
    }

    public static void printMacOsSetupHint() {
        System.out.println("macOS setup hint:");
        System.out.println("  1) Install Vulkan SDK (LunarG) with MoltenVK.");
        System.out.println("  2) Export paths (example):");
        System.out.println("     export VK_ICD_FILENAMES=\"$HOME/VulkanSDK/<ver>/macOS/share/vulkan/icd.d/MoltenVK_icd.json\"");
        System.out.println("     export DYLD_LIBRARY_PATH=\"$HOME/VulkanSDK/<ver>/macOS/lib:$DYLD_LIBRARY_PATH\"");
        System.out.println("  3) Re-run this command.");
    }

    public static void main(String[] args) {
        boolean ok = checkVulkanLoader();
        if (!ok) {
            printMacOsSetupHint();
            System.out.println("Preflight status: FAILED");
            return;
        }
        System.out.println("Preflight status: OK");
    }

    private static String vkResult(int code) {
        return switch (code) {
            case VK10.VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
            case VK10.VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
            case VK10.VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
            case VK10.VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
            default -> "VK_RESULT_" + code;
        };
    }
}
