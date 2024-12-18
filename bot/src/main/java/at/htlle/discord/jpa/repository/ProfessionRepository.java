package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Profession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionRepository extends JpaRepository<Profession, Long>
{
    Profession findByName(at.htlle.discord.model.enums.Profession name);
}
