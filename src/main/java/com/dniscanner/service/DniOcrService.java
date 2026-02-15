package com.dniscanner.service;

import com.dniscanner.client.GoogleVisionClient;
import com.dniscanner.dto.DniDataResponse;
import com.dniscanner.exception.InvalidFileException;
import com.dniscanner.parser.DniParser;
import com.dniscanner.parser.DniDorsoParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class DniOcrService {

    private static final Logger logger = LoggerFactory.getLogger(DniOcrService.class);

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final GoogleVisionClient visionClient;
    private final DniParser dniParser;
    private final DniDorsoParser dniDorsoParser;

    public DniOcrService(GoogleVisionClient visionClient, DniParser dniParser, DniDorsoParser dniDorsoParser) {
        this.visionClient = visionClient;
        this.dniParser = dniParser;
        this.dniDorsoParser = dniDorsoParser;
    }

    /**
     * Procesa una imagen de DNI y extrae los datos estructurados
     */
    public DniDataResponse processDniImage(MultipartFile file) {
        logger.info("Iniciando procesamiento de imagen DNI: {}", file.getOriginalFilename());

        // Validar archivo
        validateFile(file);

        try {
            // Obtener bytes de la imagen
            byte[] imageBytes = file.getBytes();
            logger.debug("Imagen cargada: {} bytes", imageBytes.length);

            // Extraer texto con Google Vision OCR
            String ocrText = visionClient.extractText(imageBytes);

            // Parsear el texto para extraer datos del DNI
            DniDataResponse dniData = dniParser.parse(ocrText);

            logger.info("Procesamiento completado exitosamente para: {}", file.getOriginalFilename());
            return dniData;

        } catch (IOException e) {
            logger.error("Error al leer el archivo: {}", e.getMessage(), e);
            throw new InvalidFileException("No se pudo leer el archivo de imagen", e);
        }
    }

    /**
     * Procesa frente y dorso del DNI (opcional)
     */
    public DniDataResponse processDniImages(MultipartFile frente, MultipartFile dorso) {
        logger.info("Iniciando procesamiento de DNI completo (frente y dorso)");

        // Procesar frente (obligatorio)
        DniDataResponse response = processDniImage(frente);

        // Procesar dorso (opcional)
        if (dorso != null && !dorso.isEmpty()) {
            logger.info("Procesando dorso del DNI: {}", dorso.getOriginalFilename());

            try {
                validateFile(dorso);
                byte[] dorsoBytes = dorso.getBytes();

                // Extraer texto del dorso
                String ocrTextDorso = visionClient.extractText(dorsoBytes);

                // Parsear datos del dorso y agregarlos al response
                dniDorsoParser.parseDorso(ocrTextDorso, response);

                logger.info("Dorso procesado exitosamente");

            } catch (Exception e) {
                logger.error("Error al procesar el dorso: {}", e.getMessage(), e);
                // No fallar todo el proceso si el dorso falla
                logger.warn("Continuando con datos solo del frente");
            }
        } else {
            logger.info("No se proporcionó imagen del dorso, solo se procesará el frente");
        }

        return response;
    }

    /**
     * Método de debug para ver el texto extraído por OCR sin parsing
     */
    public java.util.Map<String, Object> debugOcrText(MultipartFile file) {
        logger.info("DEBUG: Iniciando extracción de texto OCR: {}", file.getOriginalFilename());

        // Validar archivo
        validateFile(file);

        try {
            // Obtener bytes de la imagen
            byte[] imageBytes = file.getBytes();

            // Extraer texto con Google Vision OCR
            String ocrText = visionClient.extractText(imageBytes);

            // Retornar información de debug
            java.util.Map<String, Object> debugInfo = new java.util.HashMap<>();
            debugInfo.put("filename", file.getOriginalFilename());
            debugInfo.put("fileSize", file.getSize());
            debugInfo.put("contentType", file.getContentType());
            debugInfo.put("ocrTextLength", ocrText.length());
            debugInfo.put("ocrText", ocrText);
            debugInfo.put("ocrTextLines", ocrText.split("\\n").length);

            logger.info("DEBUG: Texto extraído exitosamente");
            return debugInfo;

        } catch (IOException e) {
            logger.error("Error al leer el archivo: {}", e.getMessage(), e);
            throw new InvalidFileException("No se pudo leer el archivo de imagen", e);
        }
    }

    /**
     * Valida que el archivo sea válido para procesamiento
     */
    private void validateFile(MultipartFile file) {
        // Verificar que el archivo no esté vacío
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("El archivo está vacío");
        }

        // Verificar el nombre del archivo
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new InvalidFileException("El nombre del archivo es inválido");
        }

        // Verificar el tipo de contenido
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            logger.error("Tipo de archivo no permitido: {}", contentType);
            throw new InvalidFileException(
                    String.format("Tipo de archivo no permitido: %s. Solo se permiten: JPG, PNG", contentType)
            );
        }

        // Verificar el tamaño del archivo
        if (file.getSize() > MAX_FILE_SIZE) {
            logger.error("Archivo muy grande: {} bytes", file.getSize());
            throw new InvalidFileException(
                    String.format("El archivo es muy grande (%.2f MB). Tamaño máximo: 10MB",
                            file.getSize() / (1024.0 * 1024.0))
            );
        }

        // Verificar extensión del archivo
        String extension = getFileExtension(filename);
        if (!Arrays.asList("jpg", "jpeg", "png").contains(extension.toLowerCase())) {
            throw new InvalidFileException(
                    String.format("Extensión de archivo no permitida: %s. Solo se permiten: .jpg, .jpeg, .png", extension)
            );
        }

        logger.debug("Validación de archivo exitosa: {}", filename);
    }

    /**
     * Obtiene la extensión del archivo
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}