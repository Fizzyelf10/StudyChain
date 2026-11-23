package com.studychain.api.listing;

public enum ListingStatus {
    /** Visible and for sale. */
    ACTIVE,
    /** Hidden from search because several people reported it. An admin decides. */
    UNDER_REVIEW,
    /** Taken down by the seller or an admin. */
    REMOVED
}
