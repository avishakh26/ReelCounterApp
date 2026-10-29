package com.reelcounter.core.domain.profile

import com.reelcounter.core.domain.Outcome

/**
 * Turns a picked image into an upload-ready avatar: square, 512 px, re-encoded. Re-encoding also
 * strips metadata such as the photo's GPS location.
 */
interface AvatarImageProcessor {
    /** [sourceUri] is a content URI string from the system photo picker. */
    suspend fun process(sourceUri: String): Outcome<AvatarImage, ProfileError>
}
