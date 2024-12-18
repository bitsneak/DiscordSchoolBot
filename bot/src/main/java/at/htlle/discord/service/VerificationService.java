package at.htlle.discord.service;

import at.htlle.discord.model.VerificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@Service
public class VerificationService
{
    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);

    private final int codeLength = 6;

    private final Duration codeExpirationTime = Duration.ofMinutes(10);

    public String generateVerificationCode(VerificationClient verificationClient)
    {
        String code = new Random().ints(codeLength, 0, 10)
                .mapToObj(String::valueOf)
                .reduce("", String::concat);
        verificationClient.setCode(code);
        verificationClient.setCodeTimestamp(Timestamp.from(Instant.now()));

        logger.info("Generated verification code for user: {}", verificationClient.getUser().getIdLong());
        return code;
    }

    public Boolean verifyCode(VerificationClient verificationClient, String code)
    {
        // checks if saved code is equal to the input code
        boolean isCodeValid = verificationClient.getCode().equals(code);
        // checks if saved timestamp is not older than the set expired minutes
        boolean isTimestampValid = Duration.between(verificationClient.getCodeTimestamp().toInstant(), Instant.now()).toMinutes() <= codeExpirationTime.toMinutes();

        if (isCodeValid && isTimestampValid) {
            verificationClient.setCode(null);
            verificationClient.setCodeTimestamp(null);

            logger.info("Verification code for user: {} is correct", verificationClient.getUser().getIdLong());
            return true;
        } else if (isCodeValid && !isTimestampValid) {
            verificationClient.setCode(null);
            verificationClient.setCodeTimestamp(null);

            logger.warn("Verification code for user {} is expired", verificationClient.getUser().getIdLong());
            return null;
        } else {
            logger.warn("Verification code for user {} is incorrect", verificationClient.getUser().getIdLong());
            return false;
        }
    }
}