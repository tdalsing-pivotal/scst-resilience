package com.vmware.common;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.validation.constraints.*;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class MyObject {

    @NotBlank
    String id;

    @NotBlank
    String name;

    @NotBlank
    @Pattern(regexp = "^\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})$")
    String phone;

    @Min(0)
    int count;

    Double amount;
}
