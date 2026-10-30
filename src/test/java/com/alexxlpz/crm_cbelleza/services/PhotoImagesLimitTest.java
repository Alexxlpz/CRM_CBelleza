package com.alexxlpz.crm_cbelleza.services;

import com.alexxlpz.crm_cbelleza.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Un PNG de pocos KB puede ocupar cientos de MB al abrirlo: se rechaza por sus píxeles antes de leerlo. */
class PhotoImagesLimitTest {

    @Test
    void tinyPngWithTooManyPixelsIsRejectedBeforeDecoding() throws IOException {
        byte[] png = png(5000, 4000);
        assertTrue(png.length < 200_000, "el archivo es pequeño: lo que cuenta son los píxeles");

        var e = assertThrows(BusinessRuleException.class, () -> PhotoImages.fit(png, 1200, 1, 1, false));
        assertTrue(e.getMessage().contains("demasiados píxeles"));
        assertTrue(e.getMessage().contains(PhotoImages.MAX_PIXELS / 1_000_000 + " megapíxeles"));
    }

    @Test
    void photoBelowTheLimitStillWorks() throws IOException {
        byte[] jpeg = PhotoImages.fit(png(1600, 1200), 1200, 1, 1, false);
        assertTrue(jpeg.length > 0);
    }

    private static byte[] png(int width, int height) throws IOException {
        // En blanco y negro de 1 bit para que el propio test no gaste memoria
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
