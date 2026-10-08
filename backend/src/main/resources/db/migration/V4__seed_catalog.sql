-- Catalogo de demonstracao do POS (F8).
--
-- Os 40 SKUs da organizacao de demo do PulseBoard (`php artisan pulseboard:demo`), com nomes e
-- precos copiados do DemoDataSeeder: 20 produtos de ProductFactory::CATALOG, cada um nas variantes
-- Essential (preco minimo) e Pro (media entre minimo e maximo), com preco final floor(valor) + 0.90.
-- Os SKUs precisam existir no PulseBoard para a ingestao aceitar a venda.

INSERT INTO products (id, sku, name, price, active, created_at, updated_at)
SELECT gen_random_uuid(), catalog.sku, catalog.name, catalog.price, true, now(), now()
FROM (VALUES
    ('PB-001-ESS', 'Mouse sem fio Essential', 79.90),
    ('PB-001-PRO', 'Mouse sem fio Pro', 114.90),
    ('PB-002-ESS', 'Teclado mecânico Essential', 249.90),
    ('PB-002-PRO', 'Teclado mecânico Pro', 424.90),
    ('PB-003-ESS', 'Headset USB Essential', 129.90),
    ('PB-003-PRO', 'Headset USB Pro', 264.90),
    ('PB-004-ESS', 'Webcam Full HD Essential', 179.90),
    ('PB-004-PRO', 'Webcam Full HD Pro', 314.90),
    ('PB-005-ESS', 'Suporte para notebook Essential', 69.90),
    ('PB-005-PRO', 'Suporte para notebook Pro', 129.90),
    ('PB-006-ESS', 'Hub USB-C Essential', 99.90),
    ('PB-006-PRO', 'Hub USB-C Pro', 194.90),
    ('PB-007-ESS', 'Mousepad XL Essential', 39.90),
    ('PB-007-PRO', 'Mousepad XL Pro', 69.90),
    ('PB-008-ESS', 'Cabo USB-C 2m Essential', 29.90),
    ('PB-008-PRO', 'Cabo USB-C 2m Pro', 49.90),
    ('PB-009-ESS', 'Carregador 65W Essential', 149.90),
    ('PB-009-PRO', 'Carregador 65W Pro', 214.90),
    ('PB-010-ESS', 'Monitor 24" Essential', 799.90),
    ('PB-010-PRO', 'Monitor 24" Pro', 1049.90),
    ('PB-011-ESS', 'Luminária de mesa LED Essential', 89.90),
    ('PB-011-PRO', 'Luminária de mesa LED Pro', 154.90),
    ('PB-012-ESS', 'Cadeira ergonômica Essential', 899.90),
    ('PB-012-PRO', 'Cadeira ergonômica Pro', 1549.90),
    ('PB-013-ESS', 'Mesa regulável Essential', 1499.90),
    ('PB-013-PRO', 'Mesa regulável Pro', 2399.90),
    ('PB-014-ESS', 'SSD externo 1TB Essential', 399.90),
    ('PB-014-PRO', 'SSD externo 1TB Pro', 599.90),
    ('PB-015-ESS', 'Microfone condensador Essential', 199.90),
    ('PB-015-PRO', 'Microfone condensador Pro', 449.90),
    ('PB-016-ESS', 'Apoio de punho Essential', 34.90),
    ('PB-016-PRO', 'Apoio de punho Pro', 62.90),
    ('PB-017-ESS', 'Filtro de linha Essential', 49.90),
    ('PB-017-PRO', 'Filtro de linha Pro', 89.90),
    ('PB-018-ESS', 'Organizador de cabos Essential', 19.90),
    ('PB-018-PRO', 'Organizador de cabos Pro', 39.90),
    ('PB-019-ESS', 'Caixa de som bluetooth Essential', 149.90),
    ('PB-019-PRO', 'Caixa de som bluetooth Pro', 324.90),
    ('PB-020-ESS', 'Ring light Essential', 79.90),
    ('PB-020-PRO', 'Ring light Pro', 139.90)
) AS catalog (sku, name, price);
