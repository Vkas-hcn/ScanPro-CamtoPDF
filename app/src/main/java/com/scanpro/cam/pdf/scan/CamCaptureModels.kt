package com.scanpro.cam.pdf.scan

enum class CamFlashMode {
    AUTO, ON, OFF
}

data class CamCaptureQuality(
    val label: String,
    val width: Int,
    val height: Int,
) {
    companion object {
        val presets = listOf(
            CamCaptureQuality("8M (3840×2160)", 3840, 2160),
            CamCaptureQuality("8M (3264×2448)", 3264, 2448),
            CamCaptureQuality("8M (3200×2400)", 3200, 2400),
            CamCaptureQuality("3M (1920×1440)", 1920, 1440),
            CamCaptureQuality("2M (1920×1080)", 1920, 1080),
            CamCaptureQuality("983K (1280×786)", 1280, 786),
            CamCaptureQuality("921K (1280×720)", 1280, 720),
            CamCaptureQuality("755K (1024×738)", 1024, 738),
        )
    }
}
