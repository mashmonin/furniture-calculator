package com.example.furniturecalculator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.UpdateCheckDto;
import com.example.furniturecalculator.service.UpdateCheckService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/update-check")
@RequiredArgsConstructor
public class UpdateCheckController {

    private final UpdateCheckService updateCheckService;

    @GetMapping
    public UpdateCheckDto getUpdateCheck() {
        return updateCheckService.getResult();
    }
}
