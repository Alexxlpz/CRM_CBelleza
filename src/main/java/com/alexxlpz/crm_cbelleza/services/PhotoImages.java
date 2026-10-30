package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Preparar una foto subida (del centro o de un profesional): se lee como imagen (no se fía del nombre ni del tipo
 * que manda el navegador), se endereza según la orientación EXIF de las fotos de móvil, se reduce y se guarda como
 * JPEG, sin librerías externas.
 */
final class PhotoImages {

    /** Tamaño máximo del fichero subido (también en spring.servlet.multipart.max-file-size). */
    static final int MAX_UPLOAD_MB = 8;
    /** Evita que una imagen pequeña en bytes pero enorme en píxeles agote la memoria al descomprimirla. */
    private static final long MAX_PIXELS = 50_000_000L;
    private static final Set<String> FORMATS = Set.of("jpeg", "png");
    private static final float JPEG_QUALITY = 0.85f;

    private PhotoImages() {
    }

    /** El fichero subido, si no pasa de {@value #MAX_UPLOAD_MB} MB. */
    static byte[] bytesOf(MultipartFile file) {
        if (file.getSize() > MAX_UPLOAD_MB * 1024L * 1024L) {
            throw new BusinessRuleException("La foto pesa más de " + MAX_UPLOAD_MB + " MB. Elige una más ligera.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("No se ha podido leer la foto. Prueba con otra en JPG o PNG.");
        }
    }

    /**
     * Endereza la foto, la reduce a {@code maxSide} px por el lado largo y la devuelve en JPEG. Con {@code square}
     * se queda antes con el cuadrado del centro (fotos de perfil, que salen en un círculo).
     */
    static byte[] fit(byte[] original, int maxSide, int minWidth, int minHeight, boolean square) {
        try {
            return fitImage(original, maxSide, minWidth, minHeight, square);
        } catch (IOException e) {
            throw new BusinessRuleException("No se ha podido leer la foto. Prueba con otra en JPG o PNG.");
        }
    }

    private static byte[] fitImage(byte[] original, int maxSide, int minWidth, int minHeight, boolean square)
            throws IOException {
        BufferedImage source = read(original);
        int orientation = ExifOrientation.read(original);
        boolean turned = orientation >= 5 && orientation <= 8;
        int width = turned ? source.getHeight() : source.getWidth();
        int height = turned ? source.getWidth() : source.getHeight();
        if (width < minWidth || height < minHeight) {
            throw new BusinessRuleException("La foto es muy pequeña (" + width + "×" + height
                    + " px). Usa una de al menos " + minWidth + "×" + minHeight + " px para que no salga borrosa.");
        }
        if (square && source.getWidth() != source.getHeight()) {
            // El cuadrado no depende de la orientación: se recorta antes de girar
            int side = Math.min(source.getWidth(), source.getHeight());
            source = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
            width = side;
            height = side;
        }
        double scale = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int rawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int rawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // Reducir a la mitad varias veces antes del último paso da bordes limpios sin librerías externas
        BufferedImage current = source;
        while (current.getWidth() / 2 >= rawWidth && current.getHeight() / 2 >= rawHeight) {
            current = scaled(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        current = scaled(current, rawWidth, rawHeight);
        return writeJpeg(oriented(current, orientation));
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new BusinessRuleException("El archivo no es una imagen. Sube la foto en JPG o PNG.");
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new BusinessRuleException("Sube la foto en JPG o PNG.");
                }
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new BusinessRuleException("La foto tiene demasiados píxeles. Usa una de menos de 50 megapíxeles.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Copia la imagen al tamaño pedido sobre fondo blanco (las zonas transparentes de un PNG no salen negras). */
    private static BufferedImage scaled(BufferedImage source, int width, int height) {
        return paint(source, width, height, new AffineTransform(), width, height);
    }

    /** Endereza la imagen según la orientación EXIF (1-8): 5-8 giran 90° e intercambian ancho y alto. */
    private static BufferedImage oriented(BufferedImage source, int orientation) {
        int w = source.getWidth();
        int h = source.getHeight();
        boolean turned = orientation >= 5 && orientation <= 8;
        int width = turned ? h : w;
        int height = turned ? w : h;
        // Matrices (m00, m10, m01, m11, m02, m12) que llevan cada píxel guardado a su sitio en la foto real
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, width, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, width, height);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, height);
            default -> null;
        };
        return t == null ? source : paint(source, width, height, t, w, h);
    }

    private static BufferedImage paint(BufferedImage source, int width, int height, AffineTransform transform,
                                       int drawWidth, int drawHeight) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.transform(transform);
            g.drawImage(source, 0, 0, drawWidth, drawHeight, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Lee la etiqueta de orientación (0x0112) del bloque EXIF de un JPEG; 1 si no hay o no es JPEG. */
    static final class ExifOrientation {
        private ExifOrientation() {
        }

        static int read(byte[] b) {
            if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int pos = 2;
            while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
                int marker = b[pos + 1] & 0xFF;
                int length = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
                if (marker == 0xDA || length < 2) {
                    return 1; // empieza la imagen: ya no hay más cabeceras
                }
                int start = pos + 4;
                if (marker == 0xE1 && start + 14 <= b.length && b[start] == 'E' && b[start + 1] == 'x'
                        && b[start + 2] == 'i' && b[start + 3] == 'f') {
                    return fromTiff(b, start + 6, Math.min(b.length, pos + 2 + length));
                }
                pos += 2 + length;
            }
            return 1;
        }

        private static int fromTiff(byte[] b, int tiff, int end) {
            boolean little = b[tiff] == 'I';
            int ifd = tiff + u32(b, tiff + 4, little);
            if (ifd + 2 > end || ifd < tiff) {
                return 1;
            }
            int entries = u16(b, ifd, little);
            for (int i = 0; i < entries; i++) {
                int entry = ifd + 2 + i * 12;
                if (entry + 12 > end) {
                    return 1;
                }
                if (u16(b, entry, little) == 0x0112) {
                    int value = u16(b, entry + 8, little);
                    return value >= 1 && value <= 8 ? value : 1;
                }
            }
            return 1;
        }

        private static int u16(byte[] b, int i, boolean little) {
            return little ? (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8)
                    : ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
        }

        private static int u32(byte[] b, int i, boolean little) {
            return little ? u16(b, i, true) | (u16(b, i + 2, true) << 16)
                    : (u16(b, i, false) << 16) | u16(b, i + 2, false);
        }
    }
}
