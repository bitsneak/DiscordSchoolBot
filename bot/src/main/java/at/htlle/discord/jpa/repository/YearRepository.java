package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Year;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YearRepository extends JpaRepository<Year, Long> {
    Year findByYear(at.htlle.discord.model.enums.Year year);
}
