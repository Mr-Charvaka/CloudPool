package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@Entity
@Table(name = "developer_table_fields")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DevTableField {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private DevTable table;

    @Column(nullable = false)
    private String fieldName; // SQL column name (e.g., email)

    @Column(nullable = false)
    private String fieldType; // e.g., VARCHAR, INTEGER, BOOLEAN, DOUBLE

    @Builder.Default
    private boolean isRequired = false;
}
