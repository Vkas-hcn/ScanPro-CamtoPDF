package com.scanpro.cam.pdf.scan

object CamCaptureSession {
    private val photos = mutableListOf<String>()

    fun clear() {
        photos.clear()
    }

    fun add(path: String) {
        photos.add(path)
    }

    fun removeAt(index: Int) {
        if (index in photos.indices) {
            photos.removeAt(index)
        }
    }

    fun paths(): List<String> = photos.toList()

    fun size(): Int = photos.size

    fun lastOrNull(): String? = photos.lastOrNull()
}
