package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Role;
import at.htlle.discord.model.enums.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long>
{
    Optional<Role> findByName(Roles name);
}
