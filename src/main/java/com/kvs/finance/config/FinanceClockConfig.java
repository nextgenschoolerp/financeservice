package com.kvs.finance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** "Today" for due dates, overdue status and late fees, in the school's time zone (finance.time-zone). */
@Configuration
public class FinanceClockConfig {

    @Bean
    public Clock financeClock(@Value("${finance.time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
