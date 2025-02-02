package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Scholar;
import at.htlle.discord.enums.Scholars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScholarRepository extends JpaRepository<Scholar, Long> {
    Optional<Scholar> findByName(Scholars name);
}
