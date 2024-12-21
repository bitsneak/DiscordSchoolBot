package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.entity.Enrolment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long>
{
    Optional<Client> findByDiscordId(String discordId);
    List<Client> findByEnrolment(Enrolment enrolment);
}
