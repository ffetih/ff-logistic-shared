package com.siose.ff.logistics.transportation.domain;

/**
 * Incoterms 2020: who pays for which part of the journey and where the risk passes from seller
 * to buyer. The shipment records the one in the sales contract; the carrier needs it to know
 * whom to invoice.
 */
public enum Incoterm {

    /** Ex Works: the buyer collects at the seller's premises and carries everything. */
    EXW,
    /** Free Carrier: the seller hands over to the buyer's carrier at a named place. */
    FCA,
    /** Carriage Paid To: the seller pays the carriage, risk passes at the first carrier. */
    CPT,
    /** Carriage and Insurance Paid To: CPT with insurance by the seller. */
    CIP,
    /** Delivered at Place: the seller carries to the named place, not unloaded. */
    DAP,
    /** Delivered at Place Unloaded: as DAP, and the seller unloads. */
    DPU,
    /** Delivered Duty Paid: the seller carries, clears import and pays the duties. */
    DDP,
    /** Free Alongside Ship (sea and inland waterway). */
    FAS,
    /** Free on Board (sea and inland waterway). */
    FOB,
    /** Cost and Freight (sea and inland waterway). */
    CFR,
    /** Cost, Insurance and Freight (sea and inland waterway). */
    CIF
}
