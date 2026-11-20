package com.siose.ff.logistics.warehousing.domain;

import com.siose.ff.base.web.ConflictException;

/**
 * A request that clashes with what the warehouse already holds: answered with 409 and a
 * problem type per case ({@code urn:siose-ff:problem:<slug>}), so a client can tell them apart.
 */
public class WmsConflictException extends ConflictException {

    private final String slug;
    private final String title;

    private WmsConflictException(String slug, String title, String message) {
        super(message);
        this.slug = slug;
        this.title = title;
    }

    public static WmsConflictException warehouseCodeTaken(String code) {
        return new WmsConflictException("warehouse-code-taken", "Warehouse Code Taken",
                "A warehouse with code " + code + " already exists");
    }

    public static WmsConflictException warehouseGlnTaken(String gln) {
        return new WmsConflictException("warehouse-gln-taken", "Warehouse GLN Taken",
                "GLN " + gln + " already belongs to another warehouse");
    }

    public static WmsConflictException binCodeTaken(String code) {
        return new WmsConflictException("bin-code-taken", "Bin Location Code Taken",
                "This warehouse already has a bin location " + code);
    }

    public static WmsConflictException asnNumberTaken(String asnNumber) {
        return new WmsConflictException("asn-number-taken", "ASN Number Taken",
                "An ASN with number " + asnNumber + " already exists");
    }

    static WmsConflictException asnAlreadyReceived(String asnNumber) {
        return new WmsConflictException("asn-already-received", "ASN Already Received",
                "ASN " + asnNumber + " is fully received; nothing more can be booked against it");
    }

    static WmsConflictException overReceipt(String asnNumber, int lineNumber, int quantity, int open) {
        return new WmsConflictException("over-receipt", "Over-Receipt",
                "ASN " + asnNumber + " line " + lineNumber + " expects " + open + " more units, not " + quantity);
    }

    public static WmsConflictException binFull(String binCode, int palletCapacity) {
        return new WmsConflictException("bin-full", "Bin Location Full",
                "Bin location " + binCode + " already holds " + palletCapacity + " pallets");
    }

    @Override
    public String problemSlug() {
        return slug;
    }

    @Override
    public String problemTitle() {
        return title;
    }
}
