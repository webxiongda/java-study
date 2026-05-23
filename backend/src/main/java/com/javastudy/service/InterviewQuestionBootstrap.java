package com.javastudy.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InterviewQuestionBootstrap implements ApplicationRunner {
    private final InterviewQuestionSeederService seeder;

    public InterviewQuestionBootstrap(InterviewQuestionSeederService seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seedIfEmpty();
    }
}
