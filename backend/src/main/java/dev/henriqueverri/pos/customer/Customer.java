package dev.henriqueverri.pos.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(length = 20)
  private String document;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Customer() {}

  public Customer(String name, String email, String document, Instant now) {
    this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    update(name, email, document, now);
  }

  public void update(String name, String email, String document, Instant now) {
    this.name = name.trim();
    this.email = normalizeEmail(email);
    this.document = document == null || document.isBlank() ? null : document.trim();
    this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
  }

  /** E-mails are unique case-insensitively, so they are stored lower-cased. */
  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getDocument() {
    return document;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
