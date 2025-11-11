package com.tolox.user.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;

@Table(name = "app_user")
@Entity
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled=true;

    private String imageUrl;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "app_user_role_maping",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    private AuthProvider provider;

    private String providerId;

}
