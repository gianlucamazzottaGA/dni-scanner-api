package com.dniscanner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DniDataResponse {

    // Datos del frente
    private String nombre;
    private String apellido;
    private String dni;
    private String fechaNacimiento;

    // Datos del dorso
    private String domicilio;
    private String lugarNacimiento;
    private String cuil;

}