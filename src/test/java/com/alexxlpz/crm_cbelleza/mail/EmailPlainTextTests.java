package com.alexxlpz.crm_cbelleza.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Los correos salen con versión en texto plano además del HTML y con «Cuquora» como nombre del remitente. */
class EmailPlainTextTests {

    private static final String HTML = """
            <!DOCTYPE html><html><head><meta charset="UTF-8"><title>No sale</title><style>p{color:red}</style></head>
            <body><div>
              <p>Cuquora</p>
              <h2>¡Tu cita está confirmada!</h2>
              <span>Hola, Sofía</span>
            </div>
            <!-- comentario -->
            <p>Centro Histórico te espera el lunes &amp; más.</p>
            <p><strong>Tratamiento:</strong> <span>Manicura (50 min)</span></p>
            <p><a href="https://cuquora.com/client/appointments" style="x">Ver mis citas</a></p>
            <p>Mensaje del centro: &laquo;Ven 5&nbsp;minutos antes&raquo;</p>
            </body></html>""";

    @Test
    void plainTextKeepsTheContentAndTheLinks() {
        String text = HtmlToText.convert(HTML);
        assertEquals("""
                Cuquora

                ¡Tu cita está confirmada!

                Hola, Sofía

                Centro Histórico te espera el lunes & más.

                Tratamiento: Manicura (50 min)

                Ver mis citas: https://cuquora.com/client/appointments

                Mensaje del centro: «Ven 5 minutos antes»""", text);
        assertFalse(text.contains("No sale"));
        assertFalse(text.contains("color:red"));
    }

    @Test
    void emptyHtmlGivesEmptyText() {
        assertEquals("", HtmlToText.convert(null));
        assertEquals("", HtmlToText.convert("  "));
    }

    @Test
    void smtpSendsPlainTextAndHtmlFromCuquora() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        new SmtpEmailGateway(sender, "citas@cuquora.com")
                .send(new EmailMessage("sofia@gmail.com", "carlos@cbelleza.com", "Centro Histórico", "Cita confirmada", HTML));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage mime = sent.getValue();
        mime.saveChanges();
        InternetAddress from = (InternetAddress) mime.getFrom()[0];
        assertEquals("citas@cuquora.com", from.getAddress());
        assertEquals("Cuquora", from.getPersonal());
        assertEquals("carlos@cbelleza.com", ((InternetAddress) mime.getReplyTo()[0]).getAddress());

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        mime.writeTo(raw);
        String body = raw.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("multipart/alternative"), body);
        assertTrue(body.contains("text/plain"), body);
        assertTrue(body.contains("text/html"), body);
    }
}
