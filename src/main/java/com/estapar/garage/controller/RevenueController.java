package com.estapar.garage.controller;

import com.estapar.garage.dto.RevenueResponseDto;
import com.estapar.garage.service.RevenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService revenueService;

    @GetMapping("/revenue")
    public RevenueResponseDto revenue(
            @RequestParam(value = "sector", required = false) String sectorParam,
            @RequestParam(value = "date", required = false) String dateParam,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String sector = sectorParam != null ? sectorParam : body.get("sector");
        String date = dateParam != null ? dateParam : body.get("date");
        return revenueService.revenue(sector, LocalDate.parse(date));
    }
}