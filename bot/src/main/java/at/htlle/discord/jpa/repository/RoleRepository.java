package at.htlle.discord.jpa.repository;

import at.htlle.discord.jpa.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long>
{
    Role findByName(at.htlle.discord.model.enums.Role name);
}
