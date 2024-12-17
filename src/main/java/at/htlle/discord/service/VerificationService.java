package at.htlle.discord.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class VerificationService
{
    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);

    private final Map<String, String> verificationCodes = new HashMap<>();

    private final Random random = new Random();

    public String generateVerificationCode(String email)
    {
        String code = String.format("%06d", random.nextInt(1000000));
        verificationCodes.put(code, email);

        logger.info("Generated verification code for email: {}", email);
        return code;
    }

    public String getEmailByCode(String code)
    {
        String email = verificationCodes.get(code);

        if (email != null) {
            logger.info("Successfully retrieved email for code: {}", code);
        } else {
            logger.warn("No email found for code: {}", code);
        }

        return email;
    }

    public void removeCode(String code)
    {
        String email = verificationCodes.remove(code);

        if (email != null) {
            logger.info("Removed verification code for email: {}", email);
        } else {
            logger.warn("Attempted to remove non-existing code: {}", code);
        }
    }
}