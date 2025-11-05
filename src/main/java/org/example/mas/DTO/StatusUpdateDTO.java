package org.example.mas.DTO;

import lombok.Data;

@Data
public class StatusUpdateDTO {
    private String key;
    private String value;

    public StatusUpdateDTO() {}

    public StatusUpdateDTO(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
