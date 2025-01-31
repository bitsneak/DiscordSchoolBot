package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {
    Optional<Enrolment> findByName(String name);
    Optional<Enrolment> findByTeacher(Teacher teacher);
}
