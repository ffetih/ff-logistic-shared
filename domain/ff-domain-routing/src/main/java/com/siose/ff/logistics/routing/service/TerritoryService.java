package com.siose.ff.logistics.routing.service;

import com.siose.ff.logistics.routing.domain.GeoConflictException;
import com.siose.ff.logistics.routing.domain.Territory;
import com.siose.ff.logistics.routing.domain.TerritoryRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Territories of the caller's tenant. */
@Service
public class TerritoryService {

    private final TerritoryRepository territories;

    TerritoryService(TerritoryRepository territories) {
        this.territories = territories;
    }

    public record NewTerritory(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") String territoryCode,
            @NotBlank @Size(max = 140) String name,
            @NotBlank String hubGln,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double centerLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double centerLongitude,
            @NotNull @Positive Double coverageRadiusKm,
            @NotNull @Positive Integer activePointCount,
            @NotNull @Positive Integer weeklyVisitTarget) {
    }

    public record TerritoryView(UUID id, String tenantId, String territoryCode, String name, String hubGln,
            double centerLatitude, double centerLongitude, double coverageRadiusKm, int activePointCount,
            int weeklyVisitTarget, double workloadBalanceScore, Instant createdAt) {
    }

    @Transactional
    public TerritoryView create(NewTerritory request) {
        Territory territory = new Territory(request.territoryCode(), request.name(), request.hubGln(),
                request.centerLatitude(), request.centerLongitude(), request.coverageRadiusKm(),
                request.activePointCount(), request.weeklyVisitTarget());
        if (territories.existsByTerritoryCode(territory.getTerritoryCode())) {
            throw GeoConflictException.territoryCodeTaken(territory.getTerritoryCode());
        }
        return view(territories.save(territory));
    }

    @Transactional(readOnly = true)
    public List<TerritoryView> list() {
        return territories.findAllByOrderByTerritoryCode().stream().map(TerritoryService::view).toList();
    }

    private static TerritoryView view(Territory t) {
        return new TerritoryView(t.getId(), t.getTenantId(), t.getTerritoryCode(), t.getName(), t.getHubGln(),
                t.getCenterLatitude(), t.getCenterLongitude(), t.getCoverageRadiusKm(), t.getActivePointCount(),
                t.getWeeklyVisitTarget(), t.getWorkloadBalanceScore(), t.getCreatedAt());
    }
}
