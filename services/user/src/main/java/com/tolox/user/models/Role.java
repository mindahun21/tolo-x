package com.tolox.user.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "role")
@Data
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Enumerated(EnumType.STRING)
    private ERole name;
}
