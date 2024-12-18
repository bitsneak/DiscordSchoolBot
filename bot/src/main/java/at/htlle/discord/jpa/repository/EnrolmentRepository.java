package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Enrolment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {
    Enrolment findByName(at.htlle.discord.model.enums.Enrolment name);
}
