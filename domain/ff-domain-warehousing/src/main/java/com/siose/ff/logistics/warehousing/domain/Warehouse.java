package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.persistence.TenantAwareEntity;
import com.siose.ff.logistics.standards.Gs1Standards;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A warehouse, cross-dock or hub. Its GLN is the GS1 number partners use for it; the GS1
 * company prefix is the operator's, and the same prefix starts every SSCC printed here.
 */
@Entity
@Table(name = "wms_warehouses")
public class Warehouse extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false, length = 13)
    private String gln;

    @Column(name = "gs1_company_prefix", nullable = false, length = 10)
    private String gs1CompanyPrefix;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 80)
    private String city;

    private double latitude;

    private double longitude;

    private int palletCapacity;

    protected Warehouse() {
    }

    /**
     * @throws IllegalArgumentException if the GLN's check digit is wrong, or the GLN does not
     *                                  start with the company prefix: a GS1 member numbers its own
     *                                  locations, so its GLNs carry its prefix
     */
    public Warehouse(String code, String name, String gln, String gs1CompanyPrefix, String countryCode,
            String city, double latitude, double longitude, int palletCapacity) {
        if (!Gs1Standards.isValidGln13(gln)) {
            throw new IllegalArgumentException("GLN " + gln + " is not 13 digits with a valid GS1 check digit");
        }
        if (gs1CompanyPrefix == null || !gs1CompanyPrefix.matches("\\d{6,10}")) {
            throw new IllegalArgumentException("GS1 company prefix must be 6 to 10 digits");
        }
        if (!gln.startsWith(gs1CompanyPrefix)) {
            throw new IllegalArgumentException(
                    "GLN " + gln + " does not start with the GS1 company prefix " + gs1CompanyPrefix);
        }
        if (palletCapacity < 1) {
            throw new IllegalArgumentException("Pallet capacity must be at least 1");
        }
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.gln = gln;
        this.gs1CompanyPrefix = gs1CompanyPrefix;
        this.countryCode = countryCode.trim().toUpperCase();
        this.city = city.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.palletCapacity = palletCapacity;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getGln() {
        return gln;
    }

    public String getGs1CompanyPrefix() {
        return gs1CompanyPrefix;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCity() {
        return city;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getPalletCapacity() {
        return palletCapacity;
    }
}
