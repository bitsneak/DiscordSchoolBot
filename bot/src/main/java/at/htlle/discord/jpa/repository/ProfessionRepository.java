package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Profession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfessionRepository extends JpaRepository<Profession, Long> {
    Optional<Profession> findByName(String name);
}
