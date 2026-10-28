// Copyright 2025 Kyant; SPDX-License-Identifier: Apache-2.0
// Android adaptation for DockCreater; see docs/liquid-glass-port.md.
package com.kyant.backdrop.catalog.utils

internal suspend fun awaitFrame() {
    kotlinx.coroutines.android.awaitFrame()
}
