package com.siose.ff.logistics.warehousing.domain;

/** What a bin location is, which decides what may be put there. */
public enum BinKind {

    /** A slot in a pallet rack, one or more pallets high. */
    RACK,

    /** A marked area on the floor, for block stacking. */
    FLOOR,

    /** Next to a dock door: goods wait here between unloading and put-away. */
    STAGING
}
