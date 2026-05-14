/**
 * Data Transfer Object (DTO) representing organization details.
 * <p>
 * This class encapsulates basic organization information that is included in webhook
 * payloads when users belong to organizations. It provides organization identification,
 * display name, and alias information.
 * <p>
 * Fields are serialized to JSON with property names specified in @JsonProperty annotations.
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO containing organization metadata for webhook notifications.
 */
public class OrgDetailDTO {

    /**
     * Constructs an OrgDetailDTO with the specified organization details.
     *
     * @param id the unique identifier of the organization
     * @param name the display name of the organization
     * @param alias the unique alias or slug for the organization
     */
    public OrgDetailDTO(String id, String name, String alias) {
        this.id = id;
        this.name = name;
        this.alias = alias;
    }

    /** The unique identifier of the organization. */
    @JsonProperty("id")
    private String id;

    /** The display name of the organization. */
    @JsonProperty("name")
    private String name;

    /** The unique alias or slug for the organization. */
    @JsonProperty("alias")
    private String alias;
}
