package com.dniscanner.client;

import com.dniscanner.exception.OcrProcessingException;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class GoogleVisionClient {

    private static final Logger logger = LoggerFactory.getLogger(GoogleVisionClient.class);

    /**
     * Realiza OCR en una imagen usando Google Cloud Vision API
     *
     * @param imageBytes bytes de la imagen
     * @return texto extraído de la imagen
     */
    public String extractText(byte[] imageBytes) {
        logger.info("Iniciando extracción de texto con Google Vision API");

        if (imageBytes == null || imageBytes.length == 0) {
            throw new OcrProcessingException("Los bytes de la imagen están vacíos");
        }

        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {

            // Construir la imagen para Vision API
            ByteString byteString = ByteString.copyFrom(imageBytes);
            Image image = Image.newBuilder().setContent(byteString).build();

            // Configurar la feature para detección de texto
            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.TEXT_DETECTION)
                    .build();

            // Crear la request
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            // Ejecutar la detección de texto
            logger.debug("Enviando imagen a Google Vision API para análisis");
            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (responses.isEmpty()) {
                throw new OcrProcessingException("No se recibió respuesta de Google Vision API");
            }

            AnnotateImageResponse imageResponse = responses.get(0);

            // Verificar si hubo errores
            if (imageResponse.hasError()) {
                String errorMessage = imageResponse.getError().getMessage();
                logger.error("Error en Google Vision API: {}", errorMessage);
                throw new OcrProcessingException("Error en Vision API: " + errorMessage);
            }

            // Extraer el texto
            String extractedText = imageResponse.getTextAnnotationsList().isEmpty()
                    ? ""
                    : imageResponse.getTextAnnotations(0).getDescription();

            if (extractedText == null || extractedText.trim().isEmpty()) {
                logger.warn("No se detectó texto en la imagen");
                throw new OcrProcessingException("No se pudo detectar texto en la imagen. Asegúrese de que la imagen sea clara y contenga un DNI argentino.");
            }

            logger.info("Texto extraído exitosamente. Longitud: {} caracteres", extractedText.length());
            logger.debug("Texto extraído: {}", extractedText);

            return extractedText;

        } catch (IOException e) {
            logger.error("Error de I/O al comunicarse con Google Vision API: {}", e.getMessage(), e);
            throw new OcrProcessingException("Error al comunicarse con Google Vision API. Verifique las credenciales.", e);
        } catch (Exception e) {
            logger.error("Error inesperado en Google Vision API: {}", e.getMessage(), e);
            throw new OcrProcessingException("Error inesperado al procesar la imagen con OCR", e);
        }
    }

    /**
     * Valida que las credenciales de Google Cloud estén configuradas
     */
    public void validateCredentials() {
        String credentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        if (credentials == null || credentials.isEmpty()) {
            logger.error("Variable de entorno GOOGLE_APPLICATION_CREDENTIALS no está configurada");
            throw new OcrProcessingException(
                    "Las credenciales de Google Cloud no están configuradas. " +
                            "Configure la variable de entorno GOOGLE_APPLICATION_CREDENTIALS"
            );
        }

        logger.info("Credenciales de Google Cloud encontradas: {}", credentials);
    }
}