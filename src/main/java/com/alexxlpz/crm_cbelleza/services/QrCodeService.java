package com.alexxlpz.crm_cbelleza.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Códigos QR del enlace corto de cada centro (cartel para el mostrador de «Mi Centro»).
 * Se dibujan en el servidor: el SVG va dentro de la página y también se descarga en SVG o PNG para imprenta o redes.
 */
@Service
public class QrCodeService {

    /** Margen blanco alrededor del código, en módulos (el estándar pide 4 para que lo lean todas las cámaras). */
    static final int QUIET_ZONE = 4;
    /** Lado del PNG descargable: sobra para un cartel A4 impreso o una historia de Instagram. */
    public static final int PNG_SIZE = 1200;
    private static final int MAX_TEXT = 300;

    /** Módulos del código, con corrección de errores media (aguanta un cartel algo gastado o con reflejos). */
    public BitMatrix matrix(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Texto del QR vacío o demasiado largo");
        }
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, QUIET_ZONE,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException e) {
            throw new IllegalArgumentException("No se ha podido crear el QR", e);
        }
    }

    /**
     * SVG escalable con un solo trazado (cada módulo oscuro es un cuadrado de 1×1). Los colores son fijos
     * (negro sobre blanco) para que el código se lea también en modo oscuro y al imprimir.
     */
    public String svg(String text, String title) {
        BitMatrix m = matrix(text);
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y)) {
                    // Une los módulos seguidos de la fila en un solo rectángulo: el SVG pesa mucho menos
                    int run = 1;
                    while (x + run < m.getWidth() && m.get(x + run, y)) {
                        run++;
                    }
                    path.append('M').append(x).append(' ').append(y).append('h').append(run).append("v1h-").append(run).append('z');
                    x += run - 1;
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + m.getWidth() + " " + m.getHeight()
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"" + escape(title) + "\">"
                + "<title>" + escape(title) + "</title>"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
                + "<path fill=\"#1a1816\" d=\"" + path + "\"/></svg>";
    }

    /** PNG cuadrado de {@link #PNG_SIZE} px con los módulos ajustados a píxeles enteros (bordes nítidos). */
    public byte[] png(String text) {
        BitMatrix m = matrix(text);
        int scale = Math.max(1, PNG_SIZE / m.getWidth());
        int side = m.getWidth() * scale;
        int offset = (PNG_SIZE - side) / 2;
        BufferedImage image = new BufferedImage(PNG_SIZE, PNG_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, PNG_SIZE, PNG_SIZE);
            g.setColor(new Color(0x1a1816));
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    if (m.get(x, y)) {
                        g.fillRect(offset + x * scale, offset + y * scale, scale, scale);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
