package at.htlle.discord.service;

import at.htlle.discord.entity.JsonRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class JsonService
{
    private static final Logger logger = LogManager.getLogger(JsonService.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private List<JsonRecord> records;

    @Value("${data.json.file}")
    private String jsonFilePath;

    @Getter
    @Setter
    private TextChannel adminChannel;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init()
    {
        try
        {
            loadJson();
            logger.info("JSON loaded successfully from {}", jsonFilePath);
        }
        catch (Exception e)
        {
            logger.error("Error loading JSON: {}", e.getMessage(), e);
        }
    }

    public JsonRecord findRecordByEmail(String email)
    {
        logger.debug("Searching for record with email: {}", email);
        return records.stream().filter(record -> record.getEmail().equalsIgnoreCase(email)).findFirst().orElse(null);
    }

    public void updateUserInfo(JsonRecord record, Long userId, String username)
    {
        logger.info("Updating discord user info for email record: {}", record.getEmail());
        record.setDiscordId(userId);
        record.setDiscordName(username);
        record.setJoinedAt(LocalDateTime.now(ZoneOffset.UTC));
        saveJson();
    }

    public void clearUserInfo(Long discordId)
    {
        logger.info("Clearing discord user info for discordId: {}", discordId);
        records.stream()
                .filter(record ->
                {
                    Long id = record.getDiscordId();
                    return id != null && id.equals(discordId);
                })
                .findFirst()
                .ifPresent(record ->
                {
                    record.setDiscordId(null);
                    record.setDiscordName(null);
                    record.setJoinedAt(null);
                });
        saveJson();
    }

    public void sendJsonToAdminChannel()
    {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER).replace(":", "_");
        logger.info("Sending JSON to admin channel with timestamp: {}", timestamp);

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream())
        {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(byteArrayOutputStream, records);
            byte[] content = byteArrayOutputStream.toByteArray();

            adminChannel.sendFiles(Collections.singletonList(FileUpload.fromData(content, "user_data-" + timestamp + ".json"))).queue();
            logger.info("JSON file sent to admin channel successfully.");
        }
        catch (IOException e)
        {
            logger.error("Error while sending JSON to admin channel: {}", e.getMessage(), e);
        }
    }

    private void loadJson() throws IOException
    {
        logger.info("Loading JSON from {}", jsonFilePath);
        File jsonFile = new File(jsonFilePath);
        if (jsonFile.exists())
        {
            CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, JsonRecord.class);
            records = objectMapper.readValue(jsonFile, listType);
        }
        else
        {
            records = new ArrayList<>();
        }
    }

    private void saveJson()
    {
        logger.info("Saving JSON to {}", jsonFilePath);
        try
        {
            objectMapper.writeValue(new File(jsonFilePath), records);
        }
        catch (IOException e)
        {
            logger.error("Error writing JSON file: {}", e.getMessage(), e);
        }
    }
}
