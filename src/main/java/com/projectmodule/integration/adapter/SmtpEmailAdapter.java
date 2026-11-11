package com.projectmodule.integration.adapter;

import com.projectmodule.integration.port.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends through the SMTP server named by {@code spring.mail.host}. With none set, Spring creates no
 * {@link JavaMailSender} and this reports itself disabled. Message contents are never logged, since
 * they can contain sign-in links.
 */
@Component
public class SmtpEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailAdapter.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailAdapter(ObjectProvider<JavaMailSender> mailSender,
                            @Value("${projectmodule.mail.from:noreply@workeasy360.com}") String fromAddress) {
        this.mailSender = mailSender.getIfAvailable();
        this.fromAddress = fromAddress;
    }

    @Override
    public boolean isEnabled() {
        return mailSender != null;
    }

    @Override
    public void send(String toAddress, String subject, String textBody) {
        if (mailSender == null) {
            log.debug("Email not configured; not sending \"{}\"", subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(textBody);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Could not send email \"{}\": {}", subject, e.getMessage());
        }
    }
}
