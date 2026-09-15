package com.minibank.client.repository;

import com.minibank.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
