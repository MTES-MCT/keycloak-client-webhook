/**
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO containing realm metadata included in webhook notifications.
 */
public class RealmDetailDTO {

    public RealmDetailDTO(String id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
    }

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("display_name")
    private String displayName;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
}
