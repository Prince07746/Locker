package com.guardian.app.notify;

import com.guardian.app.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends alerts to the guardian's inbox through a transactional email relay
 * (Postmark / SES / Mailgun SMTP). We deliberately do NOT run our own MTA:
 * a VPS IP has no sending reputation and alerts would land in spam, which
 * for this product is a silent failure of the core feature.
 *
 * Sends are async so a slow relay never blocks an API request, but delivery
 * of an uninstall/tamper alert is still attempted immediately.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final AppProperties props;

    public NotificationService(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Async
    public void send(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.fromAddress());
            msg.setTo(toEmail);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Alert dispatched: subject='{}'", subject);
        } catch (Exception e) {
            // Never leak recipient address into logs; delivery is retried by the caller's cadence.
            log.error("Failed to dispatch alert: subject='{}'", subject, e);
        }
    }
}
