package com.fluxtest.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fluxtest.fluxtestcommon.SomeEntity;

public interface SomeEntityRepository extends JpaRepository<SomeEntity, Long>{

}
