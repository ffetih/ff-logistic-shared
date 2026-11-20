package com.siose.ff.logistics.routing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.Gs1Standards;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * An area that one hub serves: a circle around its centre, with the number of delivery points in
 * it and how many visits a week they need. Route plans name the territory they belong to.
 *
 * <p>The workload balance score (70 to 100) says how evenly the work is spread: dense territories
 * and frequent visits score higher. It is calculated when the territory is created.
 */
@Entity
// The unique index of V2, declared here too so the tests' generated schema has it.
@Table(name = "geo_territories", uniqueConstraints = @UniqueConstraint(name = "uq_geo_territories_code",
        columnNames = {"tenant_id", "territory_code"}))
public class Territory extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String territoryCode;

    @Column(nullable = false, length = 140)
    private String name;

    /** The GS1 location number of the hub that serves the territory. */
    @Column(nullable = false, length = 13)
    private String hubGln;

    private double centerLatitude;

    private double centerLongitude;

    private double coverageRadiusKm;

    private int activePointCount;

    private int weeklyVisitTarget;

    private double workloadBalanceScore;

    protected Territory() {
    }

    /**
     * @throws IllegalArgumentException if the hub GLN is not 13 digits with a valid check digit
     *                                  ({@code BR-GEO-01}), or a count or the radius is not positive
     */
    public Territory(String territoryCode, String name, String hubGln, double centerLatitude, double centerLongitude,
            double coverageRadiusKm, int activePointCount, int weeklyVisitTarget) {
        if (!Gs1Standards.isValidGln13(hubGln)) {
            throw new IllegalArgumentException("Hub GLN " + hubGln + " is not 13 digits with a valid GS1 check digit");
        }
        if (coverageRadiusKm <= 0 || activePointCount < 1 || weeklyVisitTarget < 1) {
            throw new IllegalArgumentException("Radius, delivery points and weekly visits must all be above 0");
        }
        this.territoryCode = territoryCode.trim().toUpperCase();
        this.name = name.trim();
        this.hubGln = hubGln;
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
        this.coverageRadiusKm = coverageRadiusKm;
        this.activePointCount = activePointCount;
        this.weeklyVisitTarget = weeklyVisitTarget;
        this.workloadBalanceScore = workloadBalanceScore(coverageRadiusKm, activePointCount, weeklyVisitTarget);
    }

    /**
     * {@code BR-GEO-02}: 70, plus up to 20 for density (delivery points per km of radius), plus up
     * to 10 for visit frequency (5 per weekly visit per point); at most 100, one decimal.
     */
    static double workloadBalanceScore(double radiusKm, int points, int weeklyVisits) {
        double density = Math.min(20.0, points / Math.max(1.0, radiusKm));
        double frequency = Math.min(10.0, (double) weeklyVisits / points * 5.0);
        return Math.min(100.0, Math.round((70.0 + density + frequency) * 10.0) / 10.0);
    }

    public UUID getId() {
        return id;
    }

    public String getTerritoryCode() {
        return territoryCode;
    }

    public String getName() {
        return name;
    }

    public String getHubGln() {
        return hubGln;
    }

    public double getCenterLatitude() {
        return centerLatitude;
    }

    public double getCenterLongitude() {
        return centerLongitude;
    }

    public double getCoverageRadiusKm() {
        return coverageRadiusKm;
    }

    public int getActivePointCount() {
        return activePointCount;
    }

    public int getWeeklyVisitTarget() {
        return weeklyVisitTarget;
    }

    public double getWorkloadBalanceScore() {
        return workloadBalanceScore;
    }
}
