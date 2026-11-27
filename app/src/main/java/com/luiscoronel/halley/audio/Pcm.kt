package com.luiscoronel.halley.audio

private const val PCM_16_SCALE = 32_768f

/** PCM de 16 bits a muestras float en −1..1 (lo que espera sherpa-onnx). */
fun ShortArray.toFloatPcm(): FloatArray = FloatArray(size) { this[it] / PCM_16_SCALE }
