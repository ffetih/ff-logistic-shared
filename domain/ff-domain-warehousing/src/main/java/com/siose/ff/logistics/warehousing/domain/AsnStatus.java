package com.siose.ff.logistics.warehousing.domain;

/** Where an inbound delivery stands. */
public enum AsnStatus {

    /** Announced by the supplier; nothing received yet. */
    EXPECTED,

    /** The first pallet has been built; some lines still wait for goods. */
    RECEIVING,

    /** Every line has received what was announced. Nothing more can be received against it. */
    RECEIVED
}
