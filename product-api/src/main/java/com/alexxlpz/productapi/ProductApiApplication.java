package com.alexxlpz.productapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API de productos de Cuquora: nombre, marca, categoría y foto de cada producto por su código de barras.
 * Corre en su propio contenedor; solo la usa la app de Cuquora, por la red interna de Docker y con clave.
 */
@SpringBootApplication
public class ProductApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApiApplication.class, args);
    }
}
