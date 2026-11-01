package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.Pet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long>, JpaSpecificationExecutor<Pet> {

    // open-in-view đã tắt nên luôn nạp kèm loài
    @EntityGraph(attributePaths = "species")
    Optional<Pet> findWithSpeciesById(Long id);

    @Override
    @EntityGraph(attributePaths = "species")
    Page<Pet> findAll(Specification<Pet> spec, Pageable pageable);
}
