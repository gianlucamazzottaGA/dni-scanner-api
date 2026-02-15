package com.dniscanner.parser;

import com.dniscanner.dto.DniDataResponse;
import com.dniscanner.exception.ParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DniParser {

    private static final Logger logger = LoggerFactory.getLogger(DniParser.class);

    // Patrones regex para DNI argentino
    private static final Pattern DNI_PATTERN = Pattern.compile("\\b(\\d{1,3}\\.?\\d{3}\\.?\\d{3}|\\d{7,8})\\b");
    private static final Pattern FECHA_PATTERN = Pattern.compile("\\b(\\d{2})\\s+([A-Z]{3})[/\\s]+[A-Z]{3}\\s+(\\d{4})\\b");
    private static final Pattern FECHA_PATTERN_NUMERIC = Pattern.compile("\\b(\\d{2})[/-](\\d{2})[/-](\\d{4})\\b");

    // Palabras clave comunes en DNI argentinos
    private static final String[] KEYWORDS = {
            "REPUBLICA", "ARGENTINA", "DOCUMENTO", "NACIONAL", "IDENTIDAD",
            "DNI", "APELLIDO", "NOMBRE", "SEXO", "NACIONALIDAD", "EJEMPLAR"
    };

    /**
     * Parsea el texto extraído del OCR y devuelve los datos estructurados del DNI
     */
    public DniDataResponse parse(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            throw new ParsingException("El texto OCR está vacío");
        }

        logger.info("=== INICIANDO PARSING DE DNI ===");
        logger.info("Texto OCR recibido (longitud: {}): {}", ocrText.length(), ocrText);

        DniDataResponse response = DniDataResponse.builder().build();

        try {
            // Normalizar el texto
            String normalizedText = normalizeText(ocrText);
            logger.info("Texto normalizado: {}", normalizedText);

            // Extraer DNI
            String dni = extractDni(normalizedText);
            logger.info("DNI extraído: {}", dni);
            response.setDni(dni);

            // Extraer fecha de nacimiento
            String fecha = extractFechaNacimiento(normalizedText);
            logger.info("Fecha extraída: {}", fecha);
            response.setFechaNacimiento(fecha);

            // Extraer nombre y apellido
            extractNombreApellido(normalizedText, response);
            logger.info("Nombre extraído: {}", response.getNombre());
            logger.info("Apellido extraído: {}", response.getApellido());

            logger.info("=== PARSING COMPLETADO: DNI={}, Nombre={}, Apellido={}, Fecha={} ===",
                    response.getDni(), response.getNombre(), response.getApellido(),
                    response.getFechaNacimiento());

            return response;

        } catch (Exception e) {
            logger.error("Error durante el parsing: {}", e.getMessage(), e);
            throw new ParsingException("No se pudieron extraer los datos del DNI correctamente", e);
        }
    }

    /**
     * Normaliza el texto removiendo caracteres especiales y exceso de espacios
     * pero PRESERVANDO los saltos de línea
     */
    private String normalizeText(String text) {
        // Normalizar cada línea individualmente
        String[] lines = text.split("\\n");
        StringBuilder normalized = new StringBuilder();

        for (String line : lines) {
            // Limpiar espacios múltiples dentro de cada línea
            String cleanLine = line.replaceAll("\\s+", " ")
                    .replaceAll("[^\\p{L}\\p{N}\\s/.-]", "")
                    .trim();
            if (!cleanLine.isEmpty()) {
                normalized.append(cleanLine).append("\n");
            }
        }

        return normalized.toString().trim();
    }

    /**
     * Extrae el número de DNI del texto
     */
    private String extractDni(String text) {
        Matcher matcher = DNI_PATTERN.matcher(text);

        while (matcher.find()) {
            String potentialDni = matcher.group(1);

            // Limpiar puntos del DNI
            String cleanDni = potentialDni.replaceAll("\\.", "");

            // El DNI argentino tiene 7 u 8 dígitos
            if (cleanDni.length() >= 7 && cleanDni.length() <= 8) {
                // Validar que no sea una fecha (evitar falsos positivos)
                if (!isLikelyDate(text, matcher.start())) {
                    logger.debug("DNI encontrado: {} (limpiado: {})", potentialDni, cleanDni);
                    return cleanDni;
                }
            }
        }

        logger.warn("No se pudo extraer el DNI del texto");
        return null;
    }

    /**
     * Verifica si el número está en contexto de fecha
     */
    private boolean isLikelyDate(String text, int position) {
        int start = Math.max(0, position - 3);
        int end = Math.min(text.length(), position + 15);
        String context = text.substring(start, end);
        return context.contains("/") || context.contains("-");
    }

    /**
     * Extrae la fecha de nacimiento del texto
     */
    private String extractFechaNacimiento(String text) {
        // Mapa de meses en español/inglés a número
        java.util.Map<String, String> monthMap = new java.util.HashMap<>();
        monthMap.put("ENE", "01"); monthMap.put("JAN", "01");
        monthMap.put("FEB", "02");
        monthMap.put("MAR", "03");
        monthMap.put("ABR", "04"); monthMap.put("APR", "04");
        monthMap.put("MAY", "05");
        monthMap.put("JUN", "06");
        monthMap.put("JUL", "07");
        monthMap.put("AGO", "08"); monthMap.put("AUG", "08");
        monthMap.put("SEP", "09");
        monthMap.put("OCT", "10");
        monthMap.put("NOV", "11");
        monthMap.put("DIC", "12"); monthMap.put("DEC", "12");

        // Buscar primero el patrón "Fecha de nacimiento" con formato de mes en letras
        String[] lines = text.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].toUpperCase();

            if (line.contains("FECHA DE NACIMIENTO") || line.contains("DATE OF BIRTH")) {
                // Buscar en la misma línea o la siguiente
                String searchText = line;
                if (i + 1 < lines.length) {
                    searchText = line + " " + lines[i + 1];
                }

                // Buscar patrón: "05 NOV/ NOV 2001"
                Matcher matcher = FECHA_PATTERN.matcher(searchText);
                if (matcher.find()) {
                    String day = matcher.group(1);
                    String month = matcher.group(2);
                    String year = matcher.group(3);

                    String monthNum = monthMap.get(month);
                    if (monthNum != null) {
                        String fecha = String.format("%s/%s/%s", day, monthNum, year);
                        logger.debug("Fecha de nacimiento encontrada: {}", fecha);
                        return fecha;
                    }
                }
            }
        }

        // Si no se encontró, buscar patrón numérico dd/MM/yyyy
        Matcher matcher = FECHA_PATTERN_NUMERIC.matcher(text);
        while (matcher.find()) {
            String day = matcher.group(1);
            String month = matcher.group(2);
            String year = matcher.group(3);

            // Validaciones básicas de fecha
            int dayInt = Integer.parseInt(day);
            int monthInt = Integer.parseInt(month);
            int yearInt = Integer.parseInt(year);

            if (dayInt >= 1 && dayInt <= 31 &&
                    monthInt >= 1 && monthInt <= 12 &&
                    yearInt >= 1900 && yearInt <= 2010) { // Rango razonable para fecha de nacimiento

                String fecha = String.format("%s/%s/%s", day, month, year);
                logger.debug("Fecha de nacimiento encontrada: {}", fecha);
                return fecha;
            }
        }

        logger.warn("No se pudo extraer la fecha de nacimiento del texto");
        return null;
    }

    /**
     * Extrae nombre y apellido del texto usando heurísticas
     */
    private void extractNombreApellido(String text, DniDataResponse response) {
        String[] lines = text.split("\\n");
        logger.info("Extrayendo nombre y apellido. Total de líneas: {}", lines.length);

        String apellido = null;
        String nombre = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String lineUpper = line.toUpperCase();

            logger.debug("Línea {}: '{}'", i, line);

            // Buscar la palabra "APELLIDO" (con o sin formato bilingüe)
            if ((lineUpper.contains("APELLIDO") || lineUpper.contains("SURNAME")) && apellido == null) {
                logger.info("Keyword APELLIDO/SURNAME encontrado en línea {}", i);

                // Intentar extraer de la misma línea
                String extracted = extractFromBilingualLine(line, "APELLIDO", "SURNAME");
                if (extracted != null && isValidName(extracted)) {
                    apellido = cleanName(extracted);
                    logger.info("Apellido extraído de la misma línea: '{}'", apellido);
                } else if (i + 1 < lines.length) {
                    // Si no está en la misma línea, buscar en la siguiente
                    String nextLine = lines[i + 1].trim();
                    if (!nextLine.isEmpty() && isValidName(nextLine) && !containsKeywords(nextLine)) {
                        apellido = cleanName(nextLine);
                        logger.info("Apellido extraído de línea siguiente: '{}'", apellido);
                    }
                }
            }

            // Buscar la palabra "NOMBRE" (con o sin formato bilingüe)
            if ((lineUpper.contains("NOMBRE") || lineUpper.contains("NAME")) &&
                    !lineUpper.contains("APELLIDO") && !lineUpper.contains("SURNAME") && nombre == null) {
                logger.info("Keyword NOMBRE/NAME encontrado en línea {}", i);

                // Intentar extraer de la misma línea
                String extracted = extractFromBilingualLine(line, "NOMBRE", "NAME");
                if (extracted != null && isValidName(extracted)) {
                    nombre = cleanName(extracted);
                    logger.info("Nombre extraído de la misma línea: '{}'", nombre);
                } else if (i + 1 < lines.length) {
                    // Si no está en la misma línea, buscar en la siguiente
                    String nextLine = lines[i + 1].trim();
                    if (!nextLine.isEmpty() && isValidName(nextLine) && !containsKeywords(nextLine)) {
                        nombre = cleanName(nextLine);
                        logger.info("Nombre extraído de línea siguiente: '{}'", nombre);
                    }
                }
            }
        }

        // Si no se encontraron con keywords, intentar heurística alternativa
        if (apellido == null || nombre == null) {
            logger.info("No se encontraron todos los datos con keywords, usando heurística alternativa");
            extractNombreApellidoHeuristic(text, response);
            return;
        }

        response.setApellido(apellido);
        response.setNombre(nombre);

        logger.info("Nombre y apellido extraídos exitosamente: {} {}", nombre, apellido);
    }

    /**
     * Extrae el valor de una línea con formato bilingüe (ej: "Apellido / Surname")
     */
    private String extractFromBilingualLine(String line, String keywordSpanish, String keywordEnglish) {
        String lineUpper = line.toUpperCase();

        // Buscar después de la palabra clave en español
        int spanishIndex = lineUpper.indexOf(keywordSpanish.toUpperCase());
        if (spanishIndex != -1) {
            String afterSpanish = line.substring(spanishIndex + keywordSpanish.length()).trim();

            // Remover el separador "/" y la traducción en inglés
            // Ejemplo: "Apellido / Surname" -> extraer solo lo que viene después
            afterSpanish = afterSpanish.replaceFirst("^[\\s]*[/]?[\\s]*[A-Za-z]+[\\s]*", "").trim();

            if (!afterSpanish.isEmpty() && !afterSpanish.matches("^[/\\s]+.*")) {
                return afterSpanish;
            }
        }

        return null;
    }

    /**
     * Extrae el valor después de una palabra clave
     */
    private String extractValueAfterKeyword(String[] lines, int keywordLineIndex, String keyword) {
        String line = lines[keywordLineIndex].trim();

        // Intentar extraer en la misma línea después del keyword
        int keywordIndex = line.toUpperCase().indexOf(keyword);
        if (keywordIndex != -1) {
            String afterKeyword = line.substring(keywordIndex + keyword.length()).trim();

            // Limpiar el separador "/" y el texto en inglés
            afterKeyword = afterKeyword.replaceAll("^[/\\s]+[A-Za-z]+[\\s]*", "").trim();

            if (!afterKeyword.isEmpty() && isValidName(afterKeyword)) {
                return cleanName(afterKeyword);
            }
        }

        // Si no está en la misma línea, buscar en la siguiente
        if (keywordLineIndex + 1 < lines.length) {
            String nextLine = lines[keywordLineIndex + 1].trim();
            if (!nextLine.isEmpty() && isValidName(nextLine)) {
                return cleanName(nextLine);
            }
        }

        return null;
    }

    /**
     * Heurística alternativa para extraer nombre y apellido
     */
    private void extractNombreApellidoHeuristic(String text, DniDataResponse response) {
        String[] lines = text.split("\\n");

        logger.info("Usando heurística alternativa para extraer nombre y apellido");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Buscar líneas que parezcan nombres (solo letras, espacios y acentos)
            // Y que no contengan keywords del DNI
            if (isValidName(line) && !containsKeywords(line) && line.length() > 3) {

                // Si aún no tenemos apellido y la línea tiene más de una palabra
                if (response.getApellido() == null && line.split("\\s+").length >= 1) {
                    String cleanedName = cleanName(line);

                    // Verificar que no sea un campo conocido
                    String upper = line.toUpperCase();
                    if (!upper.contains("ARGENTINA") && !upper.contains("EJEMPLAR") &&
                            !upper.contains("SEXO") && !upper.contains("SEX")) {

                        response.setApellido(cleanedName);
                        logger.info("Apellido encontrado por heurística: '{}'", cleanedName);
                        continue;
                    }
                }

                // Si ya tenemos apellido pero no nombre
                if (response.getApellido() != null && response.getNombre() == null &&
                        line.split("\\s+").length >= 1) {
                    String cleanedName = cleanName(line);

                    // Verificar que no sea el mismo que el apellido
                    if (!cleanedName.equalsIgnoreCase(response.getApellido())) {
                        response.setNombre(cleanedName);
                        logger.info("Nombre encontrado por heurística: '{}'", cleanedName);
                        break;
                    }
                }
            }
        }

        logger.debug("Resultado de heurística - Nombre: {}, Apellido: {}",
                response.getNombre(), response.getApellido());
    }

    /**
     * Valida si una cadena parece un nombre válido
     */
    private boolean isValidName(String text) {
        if (text == null || text.length() < 2) {
            return false;
        }

        // Solo letras, espacios, acentos y guiones
        if (!text.matches("[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s-]+")) {
            return false;
        }

        // Máximo 6 palabras (para evitar líneas muy largas)
        String[] words = text.trim().split("\\s+");
        if (words.length > 6) {
            return false;
        }

        // Cada palabra debe tener al menos 2 caracteres
        for (String word : words) {
            if (word.length() < 2) {
                return false;
            }
        }

        return true;
    }

    /**
     * Limpia un nombre removiendo espacios extras y normalizando
     */
    private String cleanName(String name) {
        String cleaned = name.replaceAll("\\s+", " ").trim();

        // Capitalizar primera letra de cada palabra
        String[] words = cleaned.toLowerCase().split("\\s+");
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

        return result.toString();
    }

    /**
     * Verifica si el texto contiene palabras clave del DNI
     */
    private boolean containsKeywords(String text) {
        String upperText = text.toUpperCase();
        for (String keyword : KEYWORDS) {
            if (upperText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}