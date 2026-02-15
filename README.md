# DNI Scanner API - MVP Backend

Backend API profesional para escanear DNIs argentinos y extraer datos estructurados usando Google Cloud Vision OCR.

## 🚀 Tecnologías

- **Java 21**
- **Spring Boot 3.2.2**
- **Maven**
- **Google Cloud Vision API**
- **Lombok**

## 📋 Prerequisitos

- Java 21 o superior
- Maven 3.6+
- Cuenta de Google Cloud Platform
- Git

## 🔧 Configuración de Google Cloud Vision

### Paso 1: Crear Proyecto en Google Cloud

1. Ve a [Google Cloud Console](https://console.cloud.google.com/)
2. Crea un nuevo proyecto o selecciona uno existente
3. Anota el ID del proyecto

### Paso 2: Activar Vision API

1. En la consola de Google Cloud, ve a **APIs & Services** > **Library**
2. Busca "Cloud Vision API"
3. Haz clic en **Enable**

### Paso 3: Crear Service Account

1. Ve a **IAM & Admin** > **Service Accounts**
2. Haz clic en **Create Service Account**
3. Ingresa los siguientes datos:
   - **Name**: `dni-scanner-service`
   - **Description**: `Service account para DNI Scanner API`
4. Haz clic en **Create and Continue**
5. Asigna el rol: **Cloud Vision AI Service Agent**
6. Haz clic en **Done**

### Paso 4: Generar Clave JSON

1. En la lista de Service Accounts, encuentra la que creaste
2. Haz clic en los tres puntos (⋮) > **Manage Keys**
3. Haz clic en **Add Key** > **Create New Key**
4. Selecciona **JSON**
5. Haz clic en **Create**
6. Se descargará un archivo JSON (ej: `dni-scanner-service-abc123.json`)
7. **Guarda este archivo en un lugar seguro**

### Paso 5: Configurar Variable de Entorno

#### En Linux/Mac:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/ruta/completa/al/archivo/dni-scanner-service-abc123.json"
```

Para hacerlo permanente, agrega la línea anterior a tu `~/.bashrc` o `~/.zshrc`:

```bash
echo 'export GOOGLE_APPLICATION_CREDENTIALS="/ruta/completa/al/archivo/dni-scanner-service-abc123.json"' >> ~/.bashrc
source ~/.bashrc
```

#### En Windows (CMD):

```cmd
set GOOGLE_APPLICATION_CREDENTIALS=C:\ruta\completa\al\archivo\dni-scanner-service-abc123.json
```

#### En Windows (PowerShell):

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\ruta\completa\al\archivo\dni-scanner-service-abc123.json"
```

Para hacerlo permanente en Windows:
1. Busca "Variables de entorno" en el menú Inicio
2. Haz clic en "Editar las variables de entorno del sistema"
3. Haz clic en "Variables de entorno"
4. En "Variables de usuario", haz clic en "Nueva"
5. **Nombre**: `GOOGLE_APPLICATION_CREDENTIALS`
6. **Valor**: ruta completa al archivo JSON

### Verificar Configuración

```bash
echo $GOOGLE_APPLICATION_CREDENTIALS  # Linux/Mac
echo %GOOGLE_APPLICATION_CREDENTIALS%  # Windows CMD
```

## 🛠️ Instalación y Ejecución

### 1. Clonar o Descargar el Proyecto

```bash
cd dni-scanner-api
```

### 2. Compilar el Proyecto

```bash
mvn clean install
```

### 3. Ejecutar la Aplicación

```bash
mvn spring-boot:run
```

O ejecutar el JAR directamente:

```bash
java -jar target/dni-scanner-api-1.0.0.jar
```

La aplicación se iniciará en: **http://localhost:8080**

## 📡 Endpoints de la API

### 1. Escanear DNI

**Endpoint**: `POST /api/ocr/dni`

**Content-Type**: `multipart/form-data`

**Parámetros**:
- `file` (required): Archivo de imagen del DNI (JPG o PNG)

**Restricciones**:
- Tipos permitidos: `.jpg`, `.jpeg`, `.png`
- Tamaño máximo: 10MB

**Ejemplo de Request con cURL**:

```bash
curl -X POST http://localhost:8080/api/ocr/dni \
  -F "file=@/ruta/al/dni.jpg"
```

**Respuesta Exitosa** (200 OK):

```json
{
  "nombre": "Juan Carlos",
  "apellido": "Pérez",
  "dni": "12345678",
  "fechaNacimiento": "15/03/1985"
}
```

**Respuestas de Error**:

- **400 Bad Request**: Archivo inválido
```json
{
  "timestamp": "2024-02-15T10:30:00",
  "status": 400,
  "error": "Invalid File",
  "message": "Tipo de archivo no permitido: application/pdf. Solo se permiten: JPG, PNG",
  "path": "/api/ocr/dni"
}
```

- **413 Payload Too Large**: Archivo muy grande
```json
{
  "timestamp": "2024-02-15T10:30:00",
  "status": 413,
  "error": "File Too Large",
  "message": "El archivo excede el tamaño máximo permitido (10MB)",
  "path": "/api/ocr/dni"
}
```

- **422 Unprocessable Entity**: No se pudieron extraer datos
```json
{
  "timestamp": "2024-02-15T10:30:00",
  "status": 422,
  "error": "Parsing Error",
  "message": "No se pudieron extraer los datos del DNI correctamente",
  "path": "/api/ocr/dni"
}
```

- **500 Internal Server Error**: Error en OCR o servidor
```json
{
  "timestamp": "2024-02-15T10:30:00",
  "status": 500,
  "error": "OCR Processing Error",
  "message": "Error al comunicarse con Google Vision API",
  "path": "/api/ocr/dni"
}
```

### 2. Health Check

**Endpoint**: `GET /api/ocr/health`

**Respuesta**:
```
DNI Scanner API is running
```

## 🧪 Probar la API

### Usando cURL

```bash
# Escanear DNI
curl -X POST http://localhost:8080/api/ocr/dni \
  -F "file=@dni_frente.jpg" \
  -H "Accept: application/json"

# Health check
curl http://localhost:8080/api/ocr/health
```

### Usando Postman

1. Crea una nueva request POST
2. URL: `http://localhost:8080/api/ocr/dni`
3. En la pestaña **Body**, selecciona **form-data**
4. Agrega un campo:
   - Key: `file` (cambia el tipo a **File**)
   - Value: Selecciona una imagen de DNI
5. Haz clic en **Send**

### Usando HTTPie

```bash
http --form POST http://localhost:8080/api/ocr/dni file@dni_frente.jpg
```

## 📂 Estructura del Proyecto

```
src/main/java/com/dniscanner/
├── DniScannerApplication.java          # Clase principal
├── controller/
│   └── OcrController.java               # REST Controller
├── service/
│   └── DniOcrService.java               # Lógica de negocio
├── client/
│   └── GoogleVisionClient.java          # Cliente de Vision API
├── parser/
│   └── DniParser.java                   # Parser de texto OCR
├── dto/
│   ├── DniDataResponse.java             # DTO de respuesta
│   └── ErrorResponse.java               # DTO de error
├── exception/
│   ├── InvalidFileException.java        # Excepción de archivo inválido
│   ├── OcrProcessingException.java      # Excepción de OCR
│   ├── ParsingException.java            # Excepción de parsing
│   └── GlobalExceptionHandler.java      # Manejador global de excepciones
└── config/
    └── FileUploadConfig.java            # Configuración de CORS
```

## 🎯 Características Implementadas

✅ **Endpoint REST** con Spring Boot  
✅ **Validación de archivos** (tipo, tamaño, extensión)  
✅ **Integración con Google Cloud Vision API**  
✅ **Parser inteligente de DNI argentino**  
✅ **Extracción de datos**:
  - Nombre
  - Apellido
  - DNI (7-8 dígitos)
  - Fecha de nacimiento (formato dd/MM/yyyy)
✅ **Manejo robusto de errores** con @ControllerAdvice  
✅ **Logging estructurado** con SLF4J  
✅ **Código limpio y profesional**  
✅ **Arquitectura en capas** (Controller → Service → Client → Parser)  
✅ **DTOs para respuestas estructuradas**  
✅ **Configuración por variables de entorno** (sin hardcodeo de credenciales)

## 🔍 Lógica de Parsing

El `DniParser` implementa las siguientes estrategias:

1. **Extracción de DNI**: 
   - Busca números de 7-8 dígitos
   - Valida que no sea parte de una fecha
   
2. **Extracción de Fecha de Nacimiento**:
   - Regex para formato `dd/MM/yyyy`
   - Validación de rangos (día 1-31, mes 1-12, año 1900-2010)
   
3. **Extracción de Nombre y Apellido**:
   - Búsqueda de palabras clave ("APELLIDO", "NOMBRE")
   - Extracción del valor después del keyword
   - Heurística alternativa si no se encuentran keywords
   - Limpieza y normalización de nombres

## 🚨 Troubleshooting

### Error: "Las credenciales de Google Cloud no están configuradas"

**Solución**: Verifica que la variable de entorno esté configurada:
```bash
echo $GOOGLE_APPLICATION_CREDENTIALS
```

### Error: "Permission denied" al acceder al archivo JSON

**Solución**: Verifica los permisos del archivo:
```bash
chmod 600 /ruta/al/archivo.json
```

### Error: "Vision API has not been used in project"

**Solución**: Asegúrate de haber activado la Vision API en Google Cloud Console.

### El OCR no detecta texto

**Posibles causas**:
- Imagen de baja calidad
- DNI borroso o con reflejos
- Formato de imagen no soportado

**Solución**: Usa una imagen clara, bien iluminada, en formato JPG o PNG.

## 📝 Logs

La aplicación genera logs detallados en consola:

```
2024-02-15 10:30:00 - Recibida solicitud de escaneo de DNI: dni_frente.jpg
2024-02-15 10:30:00 - Validación de archivo exitosa: dni_frente.jpg
2024-02-15 10:30:00 - Imagen cargada: 245678 bytes
2024-02-15 10:30:00 - Iniciando extracción de texto con Google Vision API
2024-02-15 10:30:01 - Texto extraído exitosamente. Longitud: 156 caracteres
2024-02-15 10:30:01 - DNI encontrado: 12345678
2024-02-15 10:30:01 - Fecha de nacimiento encontrada: 15/03/1985
2024-02-15 10:30:01 - Nombre y apellido extraídos: Juan Carlos Pérez
2024-02-15 10:30:01 - Solicitud procesada exitosamente
```

## 🔐 Seguridad

- ✅ Sin hardcodeo de credenciales
- ✅ Credenciales por variable de entorno
- ✅ Validación estricta de archivos
- ✅ Límite de tamaño de archivo (10MB)
- ✅ Solo tipos de archivo permitidos (JPG, PNG)
- ✅ Manejo seguro de excepciones

## 🎯 Próximos Pasos (Fase 2)

- [ ] Agregar tests unitarios e integración
- [ ] Implementar caché de resultados
- [ ] Agregar soporte para DNI reverso
- [ ] Validación cruzada de datos
- [ ] Almacenamiento de resultados en BD
- [ ] API de consulta histórica
- [ ] Métricas y monitoreo
- [ ] Dockerización

## 📄 Licencia

Este es un proyecto MVP para demostración.

## 👨‍💻 Autor

Desarrollado como MVP backend profesional para escaneo de DNI argentinos.

---

**¡El proyecto está listo para ejecutar! 🚀**
