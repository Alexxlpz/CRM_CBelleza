package com.alexxlpz.productapi;

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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Fotos de envase: solo JPEG o PNG (se mira el contenido, no lo que diga quien la manda) y de 5 MB como mucho.
 * Antes de guardarlas se reducen a {@value #MAX_SIDE} px por el lado largo en JPEG, como hace Cuquora con las suyas:
 * así la base de datos y la copia diaria no crecen con fotos de varios megas.
 */
public final class Images {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    /** Lado largo de la foto guardada: de sobra para la tarjeta y el detalle del producto. */
    static final int MAX_SIDE = 800;
    /** Una foto que ya cabe y pesa menos que esto se guarda tal cual: recomprimirla solo le quitaría calidad. */
    static final int SMALL_BYTES = 300 * 1024;
    /** Tope de píxeles: una foto preparada para ocupar gigas al abrirla se rechaza antes de leerla. */
    static final long MAX_PIXELS = 16_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    /** De una en una: con 16 megapíxeles abiertos ya son unos 64 MB, y el contenedor tiene 256. */
    private static final Object ONE_AT_A_TIME = new Object();

    private Images() {
    }

    /** El tipo de la foto, o vacío si no es un JPEG o PNG o si pesa demasiado. */
    public static Optional<String> contentType(byte[] data) {
        if (data == null || data.length < 8 || data.length > MAX_BYTES) return Optional.empty();
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return Optional.of("image/png");
        }
        return Optional.empty();
    }

    /**
     * La foto lista para guardar: la misma si ya es pequeña y, si no, reducida a {@value #MAX_SIDE} px en JPEG
     * (lo transparente de un PNG queda en blanco). Vacío si no se puede abrir o tiene demasiados píxeles.
     */
    public static Optional<byte[]> shrink(byte[] data) {
        if (contentType(data).isEmpty()) return Optional.empty();
        synchronized (ONE_AT_A_TIME) {
            try {
                BufferedImage image = read(data);
                if (image == null) return Optional.empty();
                int width = image.getWidth();
                int height = image.getHeight();
                if (Math.max(width, height) <= MAX_SIDE && data.length <= SMALL_BYTES) return Optional.of(data);
                double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(width, height));
                int targetWidth = Math.max(1, (int) Math.round(width * scale));
                int targetHeight = Math.max(1, (int) Math.round(height * scale));
                // Reducir a la mitad varias veces antes del último paso da bordes limpios sin librerías externas
                BufferedImage current = image;
                while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
                    current = scaled(current, current.getWidth() / 2, current.getHeight() / 2);
                }
                return Optional.of(writeJpeg(scaled(current, targetWidth, targetHeight)));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    private static BufferedImage read(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Copia la imagen al tamaño pedido sobre fondo blanco (las zonas transparentes de un PNG no salen negras). */
    private static BufferedImage scaled(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
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
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
