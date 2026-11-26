package com.luiscoronel.jervy.audio

/** No se pudo abrir o leer el micrófono. Es un error fatal para el servicio. */
class MicrophoneUnavailableException(
    message: String,
) : Exception(message)

/** No se pudo iniciar el texto a voz. Es un error fatal para el servicio. */
class SpeakerUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** No se pudo copiar o cargar el modelo de la palabra clave. Es un error fatal para el servicio. */
class ModelUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
