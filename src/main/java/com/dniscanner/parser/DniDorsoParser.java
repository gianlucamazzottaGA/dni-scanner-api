package com.dniscanner.parser;

import com.dniscanner.dto.DniDataResponse;
import com.dniscanner.exception.ParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DniDorsoParser {

    private static final Logger logger = LoggerFactory.getLogger(DniDorsoParser.class);

    // Patrones regex para dorso del DNI
    private static final Pattern CUIL_PATTERN = Pattern.compile("\\b(\\d{2})\\s*-\\s*(\\d{7,8})\\s*-\\s*(\\d)\\b");
    private static final Pattern CUIL_PATTERN_NO_DASH = Pattern.compile("CUIL[:\\s]*(\\d{11})");

    /**
     * Parsea el texto extraído del OCR del dorso y agrega los datos al response
     */
    public void parseDorso(String ocrText, DniDataResponse response) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            logger.warn("El texto OCR del dorso está vacío");
            return;
        }

        logger.info("=== INICIANDO PARSING DE DORSO DEL DNI ===");
        logger.info("Texto OCR dorso recibido (longitud: {}): {}", ocrText.length(), ocrText);

        try {
            // Normalizar el texto preservando saltos de línea
            String normalizedText = normalizeText(ocrText);
            logger.info("Texto dorso normalizado: {}", normalizedText);

            // Extraer CUIL
            String cuil = extractCuil(normalizedText);
            logger.info("CUIL extraído: {}", cuil);
            response.setCuil(cuil);

            // Extraer Domicilio
            String domicilio = extractDomicilio(normalizedText);
            logger.info("Domicilio extraído: {}", domicilio);
            response.setDomicilio(domicilio);

            // Extraer Lugar de Nacimiento
            String lugarNacimiento = extractLugarNacimiento(normalizedText);
            logger.info("Lugar de nacimiento extraído: {}", lugarNacimiento);
            response.setLugarNacimiento(lugarNacimiento);

            logger.info("=== PARSING DORSO COMPLETADO: Domicilio={}, Lugar={}, CUIL={} ===",
                    domicilio, lugarNacimiento, cuil);

        } catch (Exception e) {
            logger.error("Error durante el parsing del dorso: {}", e.getMessage(), e);
            // No lanzamos excepción para no fallar todo el proceso si el dorso falla
        }
    }

    /**
     * Normaliza el texto preservando saltos de línea
     */
    private String normalizeText(String text) {
        String[] lines = text.split("\\n");
        StringBuilder normalized = new StringBuilder();

        for (String line : lines) {
            String cleanLine = line.replaceAll("\\s+", " ")
                    .trim();
            if (!cleanLine.isEmpty()) {
                normalized.append(cleanLine).append("\n");
            }
        }

        return normalized.toString().trim();
    }

    /**
     * Extrae el CUIL del texto
     * Formato esperado: 20-43862958-1 o 20 43862958 1 o CUIL: 20438629581
     */
    private String extractCuil(String text) {
        // Intentar con guiones
        Matcher matcher = CUIL_PATTERN.matcher(text);
        if (matcher.find()) {
            String prefix = matcher.group(1);
            String dni = matcher.group(2);
            String suffix = matcher.group(3);

            String cuil = prefix + "-" + dni + "-" + suffix;
            logger.debug("CUIL encontrado (con guiones): {}", cuil);
            return cuil;
        }

        // Intentar sin guiones (11 dígitos seguidos)
        matcher = CUIL_PATTERN_NO_DASH.matcher(text);
        if (matcher.find()) {
            String cuilDigits = matcher.group(1);
            // Formatear: XX-XXXXXXXX-X
            String cuil = cuilDigits.substring(0, 2) + "-" +
                    cuilDigits.substring(2, 10) + "-" +
                    cuilDigits.substring(10, 11);
            logger.debug("CUIL encontrado (sin guiones): {}", cuil);
            return cuil;
        }

        logger.warn("No se pudo extraer el CUIL del texto del dorso");
        return null;
    }

    /**
     * Extrae el domicilio del texto
     */
    private String extractDomicilio(String text) {
        String[] lines = text.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String lineUpper = line.toUpperCase();

            // Buscar la palabra "DOMICILIO"
            if (lineUpper.contains("DOMICILIO")) {
                logger.debug("Keyword DOMICILIO encontrado en línea {}: {}", i, line);

                StringBuilder domicilioBuilder = new StringBuilder();

                // Intentar extraer de la misma línea después de "DOMICILIO:"
                int domicilioIndex = lineUpper.indexOf("DOMICILIO");
                if (domicilioIndex != -1) {
                    String afterDomicilio = line.substring(domicilioIndex + "DOMICILIO".length()).trim();

                    // Remover : si existe
                    afterDomicilio = afterDomicilio.replaceFirst("^[:\\s]+", "").trim();

                    if (!afterDomicilio.isEmpty()) {
                        domicilioBuilder.append(afterDomicilio);
                        logger.debug("Domicilio parte 1 (misma línea): {}", afterDomicilio);
                    }
                }

                // Verificar si hay continuación en la siguiente línea
                // El domicilio continúa si la línea actual termina con "-" o si la siguiente línea
                // NO es un keyword conocido
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    String nextLineUpper = nextLine.toUpperCase();

                    // Verificar que la siguiente línea no sea otro campo
                    boolean isNotKeyword = !nextLineUpper.contains("LUGAR") &&
                            !nextLineUpper.contains("NACIMIENTO") &&
                            !nextLineUpper.contains("CUIL") &&
                            !nextLineUpper.contains("MINISTRO") &&
                            !nextLineUpper.contains("PULGAR") &&
                            nextLine.length() > 5;

                    // Si la línea actual termina con "-" o la siguiente parece ser continuación
                    if (domicilioBuilder.length() > 0 &&
                            (domicilioBuilder.toString().endsWith("-") || isNotKeyword)) {

                        if (!nextLine.isEmpty() && isNotKeyword) {
                            // Agregar espacio si no termina con guion
                            if (!domicilioBuilder.toString().endsWith("-")) {
                                domicilioBuilder.append(" ");
                            } else {
                                // Si termina con guion, quitarlo y agregar espacio
                                domicilioBuilder.setLength(domicilioBuilder.length() - 1);
                                domicilioBuilder.append(" ");
                            }

                            domicilioBuilder.append(nextLine);
                            logger.debug("Domicilio parte 2 (línea siguiente): {}", nextLine);
                        }
                    }
                }

                if (domicilioBuilder.length() > 5) {
                    String domicilio = cleanDomicilio(domicilioBuilder.toString());
                    logger.debug("Domicilio completo extraído: {}", domicilio);
                    return domicilio;
                }
            }
        }

        logger.warn("No se pudo extraer el domicilio del texto del dorso");
        return null;
    }

    /**
     * Extrae el lugar de nacimiento del texto
     */
    private String extractLugarNacimiento(String text) {
        String[] lines = text.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String lineUpper = line.toUpperCase();

            // Buscar "LUGAR DE NACIMIENTO"
            if (lineUpper.contains("LUGAR DE NACIMIENTO") ||
                    lineUpper.contains("LUGAR NACIMIENTO") ||
                    lineUpper.contains("LUGAR NAC")) {

                logger.debug("Keyword LUGAR DE NACIMIENTO encontrado en línea {}: {}", i, line);

                // Intentar extraer de la misma línea
                String[] keywords = {"LUGAR DE NACIMIENTO", "LUGAR NACIMIENTO", "LUGAR NAC"};
                for (String keyword : keywords) {
                    int keywordIndex = lineUpper.indexOf(keyword);
                    if (keywordIndex != -1) {
                        String afterKeyword = line.substring(keywordIndex + keyword.length()).trim();

                        // Remover : si existe
                        afterKeyword = afterKeyword.replaceFirst("^[:\\s]+", "").trim();

                        if (!afterKeyword.isEmpty() && afterKeyword.length() > 2) {
                            String lugar = cleanLugarNacimiento(afterKeyword);
                            logger.debug("Lugar de nacimiento extraído de la misma línea: {}", lugar);
                            return lugar;
                        }
                    }
                }

                // Si no está en la misma línea, buscar en la siguiente
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    if (!nextLine.isEmpty() && nextLine.length() > 2) {
                        String lugar = cleanLugarNacimiento(nextLine);
                        logger.debug("Lugar de nacimiento extraído de línea siguiente: {}", lugar);
                        return lugar;
                    }
                }
            }
        }

        logger.warn("No se pudo extraer el lugar de nacimiento del texto del dorso");
        return null;
    }

    /**
     * Limpia y formatea el domicilio
     */
    private String cleanDomicilio(String domicilio) {
        // Capitalizar correctamente
        String[] words = domicilio.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0) {
                // Capitalizar primera letra
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString().trim();
    }

    /**
     * Limpia y formatea el lugar de nacimiento
     */
    private String cleanLugarNacimiento(String lugar) {
        // Capitalizar correctamente
        String[] words = lugar.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString().trim();
    }
}