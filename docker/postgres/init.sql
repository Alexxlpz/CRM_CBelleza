-- ==============================================================================
-- CRM_CBelleza: PostgreSQL Schema & Demo Data Initialization Script
-- ==============================================================================

-- 1. Table Definitions
CREATE TABLE IF NOT EXISTS centers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(255),
    email VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    center_id BIGINT REFERENCES centers(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS treatments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price DOUBLE PRECISION NOT NULL,
    duration INTEGER NOT NULL,
    treatment_type VARCHAR(50) NOT NULL,
    center_id BIGINT NOT NULL REFERENCES centers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(255),
    price DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS inventories (
    id BIGSERIAL PRIMARY KEY,
    center_id BIGINT NOT NULL REFERENCES centers(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    stock INTEGER NOT NULL,
    CONSTRAINT uq_center_product UNIQUE (center_id, product_id)
);

CREATE TABLE IF NOT EXISTS appointments (
    id BIGSERIAL PRIMARY KEY,
    date_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    treatment_id BIGINT NOT NULL REFERENCES treatments(id) ON DELETE CASCADE,
    center_id BIGINT NOT NULL REFERENCES centers(id) ON DELETE CASCADE,
    client_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    guest_name VARCHAR(255),
    guest_phone VARCHAR(255),
    worker_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL,
    worker_message VARCHAR(500)
);

-- 2. Performance Indexes
CREATE INDEX IF NOT EXISTS idx_appointments_date_time ON appointments(date_time);
CREATE INDEX IF NOT EXISTS idx_appointments_center ON appointments(center_id);
CREATE INDEX IF NOT EXISTS idx_appointments_worker ON appointments(worker_id);
CREATE INDEX IF NOT EXISTS idx_appointments_client ON appointments(client_id);
CREATE INDEX IF NOT EXISTS idx_appointments_status ON appointments(status);
CREATE INDEX IF NOT EXISTS idx_treatments_center ON treatments(center_id);
CREATE INDEX IF NOT EXISTS idx_treatments_type ON treatments(treatment_type);
CREATE INDEX IF NOT EXISTS idx_inventories_center ON inventories(center_id);
CREATE INDEX IF NOT EXISTS idx_inventories_product ON inventories(product_id);

-- 3. Demo Data Seeding (idempotent with ON CONFLICT)

-- Centers
INSERT INTO centers (id, name, address, phone, email, latitude, longitude) VALUES
(1, 'Cuquora Centro Histórico', 'Calle Mayor 45, 28013 Madrid', '+34 910 111 222', 'centro@cbelleza.com', 40.4168, -3.7038),
(2, 'Cuquora Plaza Norte', 'Av. de la Ilustración 12, 28034 Madrid', '+34 910 222 333', 'plazanorte@cbelleza.com', 40.5401, -3.6143),
(3, 'Cuquora Sarrià-Sant Gervasi', 'Carrer Major de Sarrià 88, 08017 Barcelona', '+34 932 444 555', 'barcelona@cbelleza.com', 41.3984, 2.1221),
(4, 'Cuquora Las Condes', 'Av. Las Condes 8900, Santiago', '+56 2 2033 3444', 'lascondes@cbelleza.com', -33.4000, -70.5667)
ON CONFLICT (id) DO NOTHING;

-- Users (Workers & Clients with BCrypt hashed password for 'password123')
INSERT INTO users (id, name, email, phone, password, role, center_id) VALUES
-- Workers
(1, 'Carlos Mendoza', 'carlos@cbelleza.com', '600333444', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'WORKER', 1),
(2, 'Lucía Romero', 'lucia.romero@cbelleza.com', '600333555', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'WORKER', 1),
(3, 'Elena Rostova', 'elena@cbelleza.com', '600444555', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'WORKER', 2),
(4, 'Marco Bellini', 'marco@cbelleza.com', '600444666', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'WORKER', 3),
(5, 'Ana Valdés', 'ana@cbelleza.com', '600555666', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'WORKER', 4),
-- Clients
(6, 'Sofía Martínez', 'sofia@gmail.com', '600111222', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'CLIENT', NULL),
(7, 'Lucía Gómez', 'lucia@gmail.com', '600222333', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'CLIENT', NULL),
(8, 'Valentina Silva', 'valentina.silva@gmail.com', '600333111', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'CLIENT', NULL),
(9, 'Javier Navarro', 'javier.navarro@gmail.com', '600444222', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'CLIENT', NULL),
(10, 'Carmen Morales', 'carmen.morales@gmail.com', '600555333', '$2a$10$bSyjQnxRmehvXMA6zC5lUuB.6cwpfZ/yXUN7OS9vlK4C6SQUzSxYC', 'CLIENT', NULL)
ON CONFLICT (id) DO NOTHING;

-- Treatments (Comprehensive catalog for all treatment types)
INSERT INTO treatments (id, name, description, price, duration, treatment_type, center_id) VALUES
-- Center 1 (Madrid Centro Histórico)
(1, 'Manicura Rusa & Semipermanente', 'Limpieza profunda de cutículas con torno, nivelación con base rubber y esmaltado de alta duración.', 32.00, 50, 'MANICURA_PEDICURA', 1),
(2, 'Tratamiento Facial Ácido Hialurónico', 'Limpieza profunda con vapor, exfoliación enzimática, masaje kobido y mascarilla hidratante.', 48.00, 60, 'FACIAL', 1),
(3, 'Masaje Relajante Aromaterapia', 'Masaje corporal completo con aceites esenciales botánicos de lavanda y jazmín.', 42.00, 55, 'MASAJES', 1),
(4, 'Exfoliación con Sales del Mar Muerto', 'Renovación celular corporal completa con sales minerales y envoltura hidratante de karité.', 45.00, 45, 'CORPORAL', 1),
(5, 'Depilación Láser Diodo Facial y Axilas', 'Tratamiento indoloro con cabezal frío para eliminación duradera del vello.', 35.00, 30, 'DEPILACION', 1),
(6, 'Corte de Autor & Peinado Glam', 'Asesoría visagista personalizada, lavado nutritivo y peinado con secado profesional.', 28.00, 45, 'PELUQUERIA', 1),

-- Center 2 (Madrid Plaza Norte)
(7, 'Champú & Queratina Orgánica', 'Tratamiento antifrizz intensivo con aminoácidos y queratina vegetal libre de formol.', 38.00, 45, 'PELUQUERIA', 2),
(8, 'Balayage Luminoso & Matiz Gloss', 'Técnica de aclarado degradado a mano alzada con baño de brillo nutritivo.', 95.00, 120, 'PELUQUERIA', 2),
(9, 'Higiene Facial Punta de Diamante', 'Microdermoabrasión suave para eliminar impurezas, puntos negros y afinar la textura.', 52.00, 60, 'FACIAL', 2),
(10, 'Pedicura Spa Rejuvenecedora', 'Baño de sales, torno podal, exfoliación de talones y masaje circulatorio relajante.', 38.00, 50, 'MANICURA_PEDICURA', 2),
(11, 'Depilación Láser Piernas Completas', 'Sesión de alta potencia con tecnología de última generación para piernas suaves.', 65.00, 50, 'DEPILACION', 2),

-- Center 3 (Barcelona Sarrià)
(12, 'Maderoterapia Reductora & Drenante', 'Técnica holística con utensilios de madera de cedro para remodelar y reducir retención.', 58.00, 60, 'CORPORAL', 3),
(13, 'Glow Facial Vitamina C Pura', 'Cóctel antioxidante e iluminador para pieles apagadas o expuestas al estrés urbano.', 50.00, 55, 'FACIAL', 3),
(14, 'Masaje Descontracturante Profundo', 'Terapia muscular focalizada en espalda, cuello y hombros para disolver nudos de tensión.', 48.00, 50, 'MASAJES', 3),
(15, 'Diseño de Cejas con Hilo & Henna', 'Diseño de mirada de precisión con hilo de algodón orgánico y tinte natural de henna.', 24.00, 30, 'DEPILACION', 3),

-- Center 4 (Santiago Las Condes)
(16, 'Manicura Spa de Lujo', 'Tratamiento exclusivo con sales minerales, exfoliación suave y esmaltado espejo.', 28.50, 45, 'MANICURA_PEDICURA', 4),
(17, 'Masaje Balinés con Pindas Calientes', 'Presión media y sacos herbales calientes para una relajación corporal absoluta.', 65.00, 75, 'MASAJES', 4),
(18, 'Tratamiento Capilar Botox & Brillo', 'Relleno de fibra capilar dañada con colágeno vegetal y ácido hialurónico.', 44.00, 60, 'PELUQUERIA', 4)
ON CONFLICT (id) DO NOTHING;

-- Global Products Catalog
INSERT INTO products (id, name, description, category, price) VALUES
(1, 'Sérum Facial Vitamina C Pura 15%', 'Sérum antioxidante iluminador para reducir manchas y potenciar el colágeno natural.', 'Rostro', 28.50),
(2, 'Crema Hidratante de Noche con Ácido Hialurónico', 'Fórmula reparadora con manteca de karité, ceramidas y ácido hialurónico multimolecular.', 'Rostro', 34.00),
(3, 'Mascarilla Facial Purificante de Arcilla Verde', 'Tratamiento purificante que absorbe el exceso de grasa y minimiza poros dilatados.', 'Rostro', 16.50),
(4, 'Tónico Facial Calmante con Agua de Rosas', 'Bruma refrescante e hidratante para equilibrar el pH tras la limpieza.', 'Rostro', 18.00),
(5, 'Champú Reparador de Queratina y Argán', 'Champú nutritivo libre de sulfatos y siliconas, formulado para cabello quebradizo.', 'Cabello', 21.00),
(6, 'Mascarilla Capilar Nutrición Intensa', 'Tratamiento intensivo con manteca de murumuru y aceite de coco virgen.', 'Cabello', 24.50),
(7, 'Aceite de Argán 100% Puro Prensado en Frío', 'Aceite multiuso para puntas abiertas, cabello seco y nutrición corporal intensiva.', 'Cabello', 29.90),
(8, 'Sérum Protector Térmico & Escudo Anti-Frizz', 'Protector térmico hasta 230°C con acabado sedoso no graso.', 'Cabello', 22.00),
(9, 'Exfoliante Corporal con Sales del Mar Muerto', 'Sales ricas en magnesio combinadas con aceites nutritivos para renovar la epidermis.', 'Cuerpo', 22.50),
(10, 'Aceite Esencial Puro de Lavanda Francesa', 'Esencia calmante de grado terapéutico para relajación y aromaterapia.', 'Cuerpo', 16.00),
(11, 'Loción Reafirmante con Cafeína y Té Verde', 'Crema corporal drenante y tonificante de rápida absorción.', 'Cuerpo', 26.00),
(12, 'Esmalte OPI Rojo Borgoña Larga Duración', 'Esmalte profesional con brillo espejo y resistencia al astillado por más de 10 días.', 'Uñas', 13.50),
(13, 'Aceite Nutritivo para Cutículas con Vitamina E', 'Tratamiento hidratante con gotero para uñas flexibles y cutículas suaves.', 'Uñas', 11.00),
(14, 'Base Fortalecedora con Calcio y Queratina', 'Tratamiento endurecedor para uñas finas, quebradizas o desvitalizadas.', 'Uñas', 12.00)
ON CONFLICT (id) DO NOTHING;

-- Center Inventories
INSERT INTO inventories (id, center_id, product_id, stock) VALUES
-- Center 1
(1, 1, 1, 14),
(2, 1, 2, 8),
(3, 1, 3, 0),  -- Out of stock alert test
(4, 1, 5, 18),
(5, 1, 7, 2),  -- Low stock warning test
(6, 1, 9, 12),
(7, 1, 12, 25),
(8, 1, 13, 15),

-- Center 2
(9, 2, 1, 6),
(10, 2, 5, 20),
(11, 2, 6, 12),
(12, 2, 7, 10),
(13, 2, 8, 16),
(14, 2, 12, 18),
(15, 2, 14, 5),

-- Center 3
(16, 3, 1, 10),
(17, 3, 2, 7),
(18, 3, 9, 15),
(19, 3, 10, 8),
(20, 3, 11, 14),
(21, 3, 13, 11),

-- Center 4
(22, 4, 5, 12),
(23, 4, 7, 6),
(24, 4, 10, 10),
(25, 4, 12, 22),
(26, 4, 14, 9)
ON CONFLICT (id) DO NOTHING;

-- Appointments (Uses dynamic relative timestamps for timeless demo readiness)
INSERT INTO appointments (id, date_time, treatment_id, center_id, client_id, guest_name, guest_phone, worker_id, status, worker_message) VALUES
-- Center 1 (Today)
(1, CURRENT_DATE + TIME '11:00:00', 1, 1, 6, NULL, NULL, 1, 'CONFIRMED', 'Cliente habitual, prefiere base rubber tono nude.'),
(2, CURRENT_DATE + TIME '15:30:00', 2, 1, 7, NULL, NULL, 1, 'CONFIRMED', 'Piel sensible, usar tónico de rosas sin alcohol.'),

-- Center 1 (Tomorrow - Full schedule)
(3, CURRENT_DATE + INTERVAL '1 day' + TIME '09:00:00', 2, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),
(4, CURRENT_DATE + INTERVAL '1 day' + TIME '10:00:00', 3, 1, 7, NULL, NULL, 1, 'CONFIRMED', NULL),
(5, CURRENT_DATE + INTERVAL '1 day' + TIME '11:00:00', 1, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),
(6, CURRENT_DATE + INTERVAL '1 day' + TIME '12:00:00', 2, 1, NULL, 'María López', '611222333', 1, 'CONFIRMED', 'Reserva invitado vía web.'),
(7, CURRENT_DATE + INTERVAL '1 day' + TIME '13:00:00', 1, 1, 7, NULL, NULL, 1, 'CONFIRMED', NULL),
(8, CURRENT_DATE + INTERVAL '1 day' + TIME '14:00:00', 3, 1, NULL, 'Pedro García', '611333444', 1, 'CONFIRMED', NULL),
(9, CURRENT_DATE + INTERVAL '1 day' + TIME '15:00:00', 2, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),
(10, CURRENT_DATE + INTERVAL '1 day' + TIME '16:00:00', 1, 1, 7, NULL, NULL, 1, 'CONFIRMED', NULL),
(11, CURRENT_DATE + INTERVAL '1 day' + TIME '17:00:00', 3, 1, NULL, 'Ana Ruiz', '611444555', 1, 'CONFIRMED', NULL),
(12, CURRENT_DATE + INTERVAL '1 day' + TIME '18:00:00', 2, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),

-- Center 1 (Day + 2 - Pending and Confirmed)
(13, CURRENT_DATE + INTERVAL '2 days' + TIME '09:00:00', 1, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),
(14, CURRENT_DATE + INTERVAL '2 days' + TIME '10:30:00', 2, 1, 7, NULL, NULL, 1, 'CONFIRMED', NULL),
(15, CURRENT_DATE + INTERVAL '2 days' + TIME '14:00:00', 3, 1, NULL, 'Juan Pérez', '611777888', 1, 'PENDING', 'Pendiente de confirmación telefónica.'),
(16, CURRENT_DATE + INTERVAL '2 days' + TIME '16:00:00', 1, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),

-- Center 1 (Day + 3 - Includes Rejected)
(17, CURRENT_DATE + INTERVAL '3 days' + TIME '10:00:00', 2, 1, 7, NULL, NULL, 1, 'REJECTED', 'Horario no disponible por mantenimiento en cabina.'),
(18, CURRENT_DATE + INTERVAL '3 days' + TIME '12:30:00', 1, 1, 6, NULL, NULL, 1, 'CONFIRMED', NULL),

-- Center 2 (Plaza Norte)
(19, CURRENT_DATE + TIME '10:00:00', 7, 2, 7, NULL, NULL, 3, 'CONFIRMED', NULL),
(20, CURRENT_DATE + INTERVAL '1 day' + TIME '09:30:00', 8, 2, 6, NULL, NULL, 3, 'CONFIRMED', 'Solicita asesoramiento de corte.'),
(21, CURRENT_DATE + INTERVAL '1 day' + TIME '11:00:00', 7, 2, 7, NULL, NULL, 3, 'CONFIRMED', NULL),
(22, CURRENT_DATE + INTERVAL '1 day' + TIME '15:00:00', 8, 2, NULL, 'Laura Sánchez', '622111222', 3, 'CONFIRMED', NULL),

-- Center 3 (Barcelona Sarrià)
(23, CURRENT_DATE + INTERVAL '1 day' + TIME '10:30:00', 12, 3, 8, NULL, NULL, 4, 'CONFIRMED', 'Primera sesión bono de 5 maderoterapia.'),
(24, CURRENT_DATE + INTERVAL '1 day' + TIME '16:00:00', 13, 3, 9, NULL, NULL, 4, 'CONFIRMED', NULL),

-- Center 4 (Las Condes)
(25, CURRENT_DATE + INTERVAL '1 day' + TIME '10:00:00', 16, 4, 6, NULL, NULL, 5, 'CONFIRMED', NULL),
(26, CURRENT_DATE + INTERVAL '1 day' + TIME '14:30:00', 17, 4, 7, NULL, NULL, 5, 'CONFIRMED', NULL)
ON CONFLICT (id) DO NOTHING;

-- 4. Advance Sequences to allow new inserts without primary key collisions
SELECT setval('centers_id_seq', COALESCE((SELECT MAX(id) FROM centers), 1));
SELECT setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 1));
SELECT setval('treatments_id_seq', COALESCE((SELECT MAX(id) FROM treatments), 1));
SELECT setval('products_id_seq', COALESCE((SELECT MAX(id) FROM products), 1));
SELECT setval('inventories_id_seq', COALESCE((SELECT MAX(id) FROM inventories), 1));
SELECT setval('appointments_id_seq', COALESCE((SELECT MAX(id) FROM appointments), 1));
