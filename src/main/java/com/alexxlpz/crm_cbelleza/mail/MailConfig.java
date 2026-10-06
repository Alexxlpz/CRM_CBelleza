package com.alexxlpz.crm_cbelleza.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;

/** Elige el canal de correo: SMTP si hay servidor configurado, si no la simulación por consola. */
@Configuration
@EnableAsync
public class MailConfig {

    @Bean
    public EmailGateway emailGateway(ObjectProvider<JavaMailSender> mailSender,
                                     @Value("${spring.mail.host:}") String mailHost,
                                     @Value("${crm.mail.from-email}") String fromEmail) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender != null && !mailHost.isBlank()) {
            return new SmtpEmailGateway(sender, fromEmail);
        }
        return new LoggingEmailGateway();
    }
}
