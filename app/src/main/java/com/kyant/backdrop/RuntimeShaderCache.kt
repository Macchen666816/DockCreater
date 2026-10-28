// Copyright 2025 Kyant; SPDX-License-Identifier: Apache-2.0
// Android-only adaptation for DockCreater; see docs/liquid-glass-port.md.
package com.kyant.backdrop



sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
        }
        error("RuntimeShader requires Android 13; lens and shader highlights must use their SDK guards")
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
