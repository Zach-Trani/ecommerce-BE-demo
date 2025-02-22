package com.printed_parts.spring_boot.modules.product.entity;

import jakarta.persistence.*;
import java.util.Objects;
import java.math.BigDecimal;

@Entity
@Table(name = "PRODUCT")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "img_url")
    private String imgUrl;

    @Column(name = "description_short")
    private String descriptionShort;

    @Column(name = "description_long")
    private String descriptionLong;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "material")
    private String material;

    @Column(name = "size")
    private String size;

    // No-args constructor (required by JPA)
    public Product() {
    }

    // All-args constructor
    public Product(int id, String imgUrl, String descriptionShort, String descriptionLong, BigDecimal price, String material, String size) {
        this.id = id;
        this.imgUrl = imgUrl;
        this.descriptionShort = descriptionShort;
        this.descriptionLong = descriptionLong;
        this.price = price;
        this.material = material;
        this.size = size;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getImgUrl() {
        return imgUrl;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    public String getDescriptionShort() {
        return descriptionShort;
    }

    public void setDescriptionShort(String descriptionShort) {
        this.descriptionShort = descriptionShort;
    }

    public String getDescriptionLong() {
        return descriptionLong;
    }

    public void setDescriptionLong(String descriptionLong) {
        this.descriptionLong = descriptionLong;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    // equals() and hashCode()
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return id == product.id &&
                Objects.equals(imgUrl, product.imgUrl) &&
                Objects.equals(descriptionShort, product.descriptionShort) &&
                Objects.equals(descriptionLong, product.descriptionLong) &&
                Objects.equals(price, product.price) &&
                Objects.equals(material, product.material) &&
                Objects.equals(size, product.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, imgUrl, descriptionShort, descriptionLong, price, material, size);
    }

    // toString()
    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", imgUrl='" + imgUrl + '\'' +
                ", descriptionShort='" + descriptionShort + '\'' +
                ", descriptionLong='" + descriptionLong + '\'' +
                ", price=" + price +
                ", material='" + material + '\'' +
                ", size='" + size + '\'' +
                '}';
    }
}