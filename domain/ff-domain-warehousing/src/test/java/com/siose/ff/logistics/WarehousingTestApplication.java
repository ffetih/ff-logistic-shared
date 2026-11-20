package com.siose.ff.logistics;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The warehousing area on its own, for its tests: the same chassis as the modulith and the
 * warehousing service (ff-base-*), and nothing else of the business. It sits in the same package
 * as both real applications, so component scanning finds the area exactly as they do.
 */
@SpringBootApplication
public class WarehousingTestApplication {
}
