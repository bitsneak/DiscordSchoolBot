package at.htlle.discord.model;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.model.enums.VerificationState;
import lombok.*;
import net.dv8tion.jda.api.entities.User;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class VerificationClient {

    @NonNull
    private VerificationState state = VerificationState.AWAITING_EMAIL;

    @NonNull
    private User user;

    @NonNull
    private Client client;

    private String code;

    private Timestamp codeTimestamp;
}
