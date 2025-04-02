package com.crypto.arbitrage.dao;

import com.crypto.arbitrage.data.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BotRepository extends JpaRepository<Bot, Long> {
  List<Bot> findAllByActiveTrue();
}
