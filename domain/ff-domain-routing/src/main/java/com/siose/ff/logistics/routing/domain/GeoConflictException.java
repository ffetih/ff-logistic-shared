package com.siose.ff.logistics.routing.domain;

import com.siose.ff.base.web.ConflictException;

/** A request that clashes with geo data that already exists: 409, one problem type per case. */
public class GeoConflictException extends ConflictException {

    private final String slug;
    private final String title;

    private GeoConflictException(String slug, String title, String message) {
        super(message);
        this.slug = slug;
        this.title = title;
    }

    public static GeoConflictException territoryCodeTaken(String code) {
        return new GeoConflictException("territory-code-conflict", "Territory Code Conflict",
                "Territory " + code + " already exists");
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
