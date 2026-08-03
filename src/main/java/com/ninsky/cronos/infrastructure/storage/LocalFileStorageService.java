package com.ninsky.cronos.infrastructure.storage;

import com.ninsky.cronos.infrastructure.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService {

    @Value("${app.storage.upload-dir:./uploads/recipes}")
    private String uploadDir;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("No se pudo crear el directorio donde se almacenarán los archivos subidos.", ex);
        }
    }

    /**
     * Guarda el archivo físicamente protegiendo contra Path Traversal.
     * @return El nombre ofuscado (UUID) con el que se guardó.
     */
    public String storeFile(MultipartFile file) {
        // 1. Limpiar el nombre original del archivo
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");

        try {
            // 2. ESCUDO ANTI-PATH TRAVERSAL (Check 1: Caracteres inválidos)
            if (originalFileName.contains("..")) {
                throw new SecurityException("Intento de Path Traversal detectado en el nombre del archivo: " + originalFileName);
            }

            // 3. Ofuscación de Nombre: Extraer extensión y generar UUID
            String fileExtension = "";
            int dotIndex = originalFileName.lastIndexOf(".");
            if (dotIndex > 0) {
                fileExtension = originalFileName.substring(dotIndex);
            }
            String obfuscatedFileName = UUID.randomUUID().toString() + fileExtension;

            // 4. Resolver ruta final
            Path targetLocation = this.fileStorageLocation.resolve(obfuscatedFileName).normalize();

            // 5. ESCUDO ANTI-PATH TRAVERSAL (Check 2: Validación de directorio raíz)
            // Garantiza que la ruta resolvida final empiece EXACTAMENTE con nuestra carpeta autorizada
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new SecurityException("Intento de almacenar el archivo fuera del directorio permitido.");
            }

            // 6. Guardar archivo en disco (Reemplaza si por un milagro de colisión UUID ya existe)
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("Archivo guardado físicamente de forma segura en: {}", targetLocation);

            return obfuscatedFileName;

        } catch (IOException ex) {
            throw new BusinessException("No se pudo almacenar el archivo " + originalFileName + ". Por favor intente nuevamente.");
        }
    }

    // Aquí puedes agregar un método loadFileAsResource(String fileName) para descargar
}
