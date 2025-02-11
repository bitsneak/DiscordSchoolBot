package at.htlle.discord.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService
{
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public boolean sendVerificationEmail(String email, String verificationCode)
    {
        logger.info("Preparing to send verification email to: {}", email);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Email Verification - School-Discord-Server");
        message.setText("Your verification code is: " + verificationCode);

        try
        {
            mailSender.send(message);
            logger.info("Successfully sent verification email to: {}", email);
            return true;
        }
        catch (Exception e)
        {
            logger.error("Failed to send verification email to: {}. Error: {}", email, e.getMessage());
            return false;
        }
    }
}
