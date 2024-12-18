package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long>
{
    Client findByDiscordId(Long discordId);
}
