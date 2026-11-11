package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    /** Lucide icon name. */
    @Column(nullable = false, length = 40)
    private String icon = "folder";

    @Column(nullable = false, length = 9)
    private String color = "#8b5cf6";

    @Column(nullable = false)
    private int sortOrder = 0;
}
