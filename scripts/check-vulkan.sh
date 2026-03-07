#!/usr/bin/env bash

set -euo pipefail

echo "Vulkan preflight check for MeshForge demo..."

if [[ "${OSTYPE:-}" == darwin* ]]; then
  echo "macOS detected: checking MoltenVK / Vulkan SDK..."

  if command -v vkcube >/dev/null 2>&1; then
    echo "PASS vkcube found in PATH"
  else
    echo "FAIL vkcube not found in PATH"
    echo "Install Vulkan SDK: https://vulkan.lunarg.com/sdk/home"
    echo "Then add: export PATH=\"\$HOME/VulkanSDK/<version>/macOS/bin:\$PATH\""
    exit 1
  fi

  if [[ -z "${VK_ICD_FILENAMES:-}" ]]; then
    echo "FAIL VK_ICD_FILENAMES is not set"
    echo "Example:"
    echo "  export VK_ICD_FILENAMES=\"\$HOME/VulkanSDK/<version>/macOS/share/vulkan/icd.d/MoltenVK_icd.json\""
    exit 1
  fi

  if [[ ! -f "${VK_ICD_FILENAMES}" ]]; then
    echo "FAIL VK_ICD_FILENAMES points to missing file: ${VK_ICD_FILENAMES}"
    exit 1
  fi
  echo "PASS VK_ICD_FILENAMES points to: ${VK_ICD_FILENAMES}"

  if [[ -z "${DYLD_LIBRARY_PATH:-}" ]]; then
    echo "WARN DYLD_LIBRARY_PATH not set (often fine, but recommended)"
    echo "  export DYLD_LIBRARY_PATH=\"\$HOME/VulkanSDK/<version>/macOS/lib:\$DYLD_LIBRARY_PATH\""
  else
    echo "PASS DYLD_LIBRARY_PATH is set"
  fi
elif [[ "${OSTYPE:-}" == linux-gnu* ]]; then
  echo "Linux detected: checking vulkaninfo..."
  if command -v vulkaninfo >/dev/null 2>&1; then
    echo "PASS vulkaninfo found"
  else
    echo "FAIL vulkaninfo not found (install vulkan-tools)"
    exit 1
  fi
elif [[ "${OSTYPE:-}" == msys || "${OSTYPE:-}" == win32 || "${OSTYPE:-}" == cygwin ]]; then
  echo "Windows detected."
  echo "Install LunarG Vulkan SDK and ensure vulkan-1.dll is in PATH."
else
  echo "Unsupported platform: ${OSTYPE:-unknown}"
  exit 1
fi

echo
echo "Preflight passed."
echo "Run:"
echo "  mvn -q -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.MeshletDispatchDemo -Dexec.args=\"fixtures/obj/medium/suzanne.obj\" exec:java"

