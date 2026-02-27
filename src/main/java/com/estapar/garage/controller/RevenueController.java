package com.estapar.garage.controller;

import com.estapar.garage.dto.RevenueResponseDto;
import com.estapar.garage.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService revenueService;

    @GetMapping("/revenue")
    public RevenueResponseDto revenue(
            @RequestParam("sector") String sector,
            @RequestParam("date") String date // yyyy-MM-dd
    ) {
        return revenueService.revenue(sector, LocalDate.parse(date));
    }
}