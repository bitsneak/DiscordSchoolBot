package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long>
{
    Optional<Client> findByDiscordId(Long discordId);
}
