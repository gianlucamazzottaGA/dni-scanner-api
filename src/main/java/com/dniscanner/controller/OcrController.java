package com.dniscanner.controller;

import com.dniscanner.dto.DniDataResponse;
import com.dniscanner.service.DniOcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private static final Logger logger = LoggerFactory.getLogger(OcrController.class);

    private final DniOcrService dniOcrService;

    public OcrController(DniOcrService dniOcrService) {
        this.dniOcrService = dniOcrService;
    }

    /**
     * Endpoint para escanear un DNI argentino (frente y opcionalmente dorso)
     *
     * POST /api/ocr/dni
     * Content-Type: multipart/form-data
     * Parameters:
     *   - frente (required): Imagen del frente del DNI (JPG o PNG)
     *   - dorso (optional): Imagen del dorso del DNI (JPG o PNG)
     *
     * @param frente archivo de imagen del frente del DNI
     * @param dorso archivo de imagen del dorso del DNI (opcional)
     * @return datos estructurados del DNI (nombre, apellido, dni, fechaNacimiento, domicilio, lugarNacimiento, cuil)
     */
    @PostMapping(value = "/dni", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DniDataResponse> scanDni(
            @RequestParam("frente") MultipartFile frente,
            @RequestParam(value = "dorso", required = false) MultipartFile dorso) {

        logger.info("Recibida solicitud de escaneo de DNI - Frente: {}, Dorso: {}",
                frente.getOriginalFilename(),
                dorso != null ? dorso.getOriginalFilename() : "No proporcionado");

        DniDataResponse response = dniOcrService.processDniImages(frente, dorso);

        logger.info("Solicitud procesada exitosamente");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * DEPRECATED: Endpoint anterior que recibe solo una imagen con nombre "file"
     * Se mantiene por compatibilidad, pero se recomienda usar el nuevo endpoint
     *
     * POST /api/ocr/dni/single
     * Content-Type: multipart/form-data
     * Parameter: file (imagen JPG o PNG)
     *
     * @param file archivo de imagen del DNI
     * @return datos estructurados del DNI
     */
    @PostMapping(value = "/dni/single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DniDataResponse> scanDniSingle(
            @RequestParam("file") MultipartFile file) {

        logger.info("Recibida solicitud de escaneo de DNI (single): {}", file.getOriginalFilename());
        logger.warn("Usando endpoint deprecated. Considere usar POST /api/ocr/dni con parámetros 'frente' y 'dorso'");

        DniDataResponse response = dniOcrService.processDniImage(file);

        logger.info("Solicitud procesada exitosamente");
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Endpoint de debug para ver el texto OCR sin parsing
     *
     * POST /api/ocr/debug
     * Content-Type: multipart/form-data
     * Parameter: file (imagen JPG o PNG)
     */
    @PostMapping(value = "/debug", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> debugOcr(
            @RequestParam("file") MultipartFile file) {

        logger.info("Recibida solicitud de DEBUG OCR: {}", file.getOriginalFilename());

        Map<String, Object> response = dniOcrService.debugOcrText(file);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Endpoint de health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("DNI Scanner API is running - Version 2.0 (Frente + Dorso support)");
    }
}