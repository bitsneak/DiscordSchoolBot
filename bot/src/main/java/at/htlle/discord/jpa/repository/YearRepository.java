package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.model.enums.Years;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface YearRepository extends JpaRepository<Year, Long> {
    Optional<Year> findByYear(Years year);
}
