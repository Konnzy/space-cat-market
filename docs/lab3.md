# Лабораторна №3: PostgreSQL, Liquibase, JPA та Testcontainers

## Запуск

Потрібні JDK 25 та запущений Docker Desktop із Linux containers.
Maven встановлювати окремо не потрібно — використовується Maven Wrapper.

```powershell
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

PostgreSQL: localhost:5432, база `cosmo_cats`, користувач/пароль `cosmo`.
Це локальні навчальні значення; інше підключення задається через
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Для роботи з іншою базою вона вже має існувати.
Під час старту Liquibase створює схему, потім Hibernate перевіряє її (`ddl-auto=validate`).
Контейнерна база зберігається у volume. `docker compose stop` зупиняє її без видалення даних.

## Модель та 3NF

П'ять таблиць предметної області (Liquibase також створює дві службові таблиці):

| Таблиця | Ключ та поля | Зв'язки |
| --- | --- | --- |
| categories | category_id, code, category_name | 1:N products |
| customers | customer_id, customer_name, email | 1:N orders |
| products | product_id, public_id, product_name, description, price, stock_quantity, category_id, seller_email | N:1 categories |
| orders | order_id, order_number, customer_id, created_at, status | N:1 customers; 1:N order_details |
| order_details | order_detail_id, order_id, product_id, quantity, unit_price | N:1 orders; N:1 products |

У таблицях атомарні значення. Неключові поля залежать від ключів своїх таблиць;
імена покупців і категорій не дублюються в замовленнях/товарах. Позиції замовлення
винесені окремо; `quantity` залежить від позиції, а не від заголовка замовлення.
`unit_price` — історична ціна конкретної позиції, а `products.price` — поточна ціна.
Ці поля мають різний зміст. Підсумок замовлення обчислюється, а не дублюється в БД.
Пара `(order_id, product_id)` унікальна: повтори товару передаються кількістю.

UUID `products.public_id` зберігає контракт API попередніх лабораторних.
Внутрішні PK — BIGINT із sequence. Значення `allocationSize=1` у JPA збігається
з `INCREMENT BY 1` у PostgreSQL. Проміжки в ID допустимі.

Для сумісності API код категорії залишається одним із `TOYS`, `FOOD`, `ACCESSORIES`.
Liquibase додає три початкові категорії; їхні відображувані назви можна змінювати.
POST категорії повертає 409, якщо код уже існує; видалити й повторно створити можна
лише категорію, яка не має товарів. Довільні нові коди потребують розширення enum
і відповідного CHECK окремою міграцією.
Старі domain records `Order` і `Cart` залишені з ЛР1; поточна персистентна модель
замовлень — `OrderEntity`/`OrderItemEntity`, API повертає `OrderView`.

## Міграції

`src/main/resources/db/changelog/db.changelog-master.yaml` послідовно включає:

1. `001-schema.sql`: п'ять sequence, таблиці, FK, NOT NULL, CHECK.
2. `002-constraints-and-indexes.sql`: окремі changeset для email, унікальності
   товару в категорії, номера замовлення та пошукових індексів.
3. `003-categories.sql`: початкові категорії без тестових товарів/замовлень.

Загалом шість changeset, кожен має rollback. SQL оформлений як Liquibase formatted SQL.
Застосовані changeset не переписують: нові зміни додають новими changeset.
PK та UNIQUE автоматично мають індекси; складений UNIQUE товару також покриває
пошук за category_id. Окремі індекси: назва товару, покупець замовлення,
статус+дата замовлення, товар позиції.

FK забороняють видалення категорії з товарами, покупця із замовленнями та товару
з історією покупок. Видалення дозволеного замовлення видаляє його позиції.
Перевірки забороняють порожні імена, непозитивні ціни/кількість та від'ємний залишок.
Email покупця нормалізується до нижнього регістру без крайніх пробілів.

## JPA та транзакції

`ProductJpaRepository`, `CategoryJpaRepository`, `OrderJpaRepository`,
`CustomerJpaRepository` використовують Spring Data JPA. `JpaProductRepository`
адаптує JPA до чинного інтерфейсу й domain record `Product`.
`ProductService`, `CategoryService`, `CustomerService`, `OrderService` мають
транзакційні межі; читання позначені readOnly, зміни — звичайними транзакціями.
Зовнішній HTTP-каталог не виконується всередині транзакцій БД.

Новий товар має нульовий залишок. Оформлення замовлення зменшує залишки,
фіксує ціни й створює всі позиції атомарно. Нестача одного товару відкочує всю операцію.
Песимістичні блокування запобігають продажу одного залишку двом покупцям.
Блокування товарів беруться в стабільному порядку UUID.
Редагування товару також блокує рядок, щоб не перезаписати залишок паралельного замовлення.

Статуси: NEW → COMPLETED або NEW → CANCELLED.
Повторне встановлення того самого статусу допустиме. Скасування повертає залишок один раз.
Видалення NEW повертає залишок; CANCELLED видаляється без повторного повернення.
COMPLETED не видаляється й не змінює статус: історія продажів зберігається.
Оновлення замовлення в CRUD — зміна статусу; склад оформленого замовлення незмінний.

## Додаткові бали: +4 за наведеною умовою

| Бонус | Реалізація |
| --- | --- |
| +1: унікальна назва в категорії | `uq_product_category_name(category_id, product_name)`; регістр назв враховується |
| +1: Natural ID | `OrderEntity.orderNumber`, `@NaturalId`, UNIQUE NOT NULL, незмінне поле; пошук через Hibernate natural-id API |
| +2: projection + JPQL | `ProductSalesProjection`, `OrderJpaRepository.findPopularProducts` |

Звіт повертає лише productId, productName, unitsSold. Популярність = SUM(quantity).
WHERE враховує лише COMPLETED за період `[from, to)`; GROUP BY агрегує позиції,
ORDER BY сортує кількість за спаданням, потім назву та UUID для стабільного порядку.
Запит використовує сутності та їхні поля, не native SQL.

## API лабораторної №3

Усі шляхи мають префікс `/api/v1`. API товарів із README збережено.

| Метод | Шлях | Призначення |
| --- | --- | --- |
| GET / POST | /categories | Список / створення категорії |
| GET / PUT / DELETE | /categories/{id} | Читання / перейменування / видалення |
| POST | /customers | Створення покупця |
| GET | /customers/{id} | Отримання покупця |
| POST | /products/{uuid}/stock | Додати залишок товару |
| POST / GET | /orders | Створення / список замовлень |
| GET / DELETE | /orders/{id} | Читання / видалення замовлення |
| PUT | /orders/{id}/status | Оновлення статусу |
| GET | /orders/by-number/{number} | Пошук за Natural ID |
| GET | /reports/popular-products?from=...&to=... | Звіт за період, ISO-8601 Instant |

Створення повертає 201 із Location, видалення — 204.
Помилки: 400 — валідація/недопустима операція/нестача товару, 404 — запис відсутній,
409 — порушення унікальності або зв'язків. Формат помилок — ProblemDetail.
Послідовний приклад запитів: `docs/lab3.http`; підставте ID зі справжніх відповідей.

## Перевірка та покриття

```powershell
.\mvnw.cmd clean verify
# Лише інтеграційні тести:
.\mvnw.cmd -Dtest=MarketplaceIntegrationTest test
```

Docker має бути запущений. Окремо запускати compose для тестів не потрібно:
Testcontainers піднімає PostgreSQL на випадковому порту й видаляє тестовий контейнер.
Перший запуск завантажує Docker-образи та Maven-залежності.
Тести не підміняють PostgreSQL на H2 і не пропускаються, якщо Docker недоступний.

Інтеграційний тест використовує `@DataJpaTest`, `@ServiceConnection` і `@Import`
потрібних сервісів, без вебконтексту. Усі Liquibase-міграції застосовуються до
порожньої БД; Hibernate виконує validate. Тести перевіряють CRUD, sequence,
індекси й обмеження, Natural ID, історичну ціну, rollback, конкурентне резервування,
агрегацію, фільтрацію та сортування звіту.
Тестова транзакція вимкнена: перевіряються справжні commit/rollback сервісних методів.

JaCoCo перевіряє щонайменше **80% рядків усього основного коду** у фазі verify,
без виключення нових класів чи згенерованого мапера. Збірка падає при меншому покритті.
HTML: `target/site/jacoco/index.html`; XML: `target/site/jacoco/jacoco.xml`;
результати тестів: `target/surefire-reports/`.
CI виконує той самий `clean verify` на Linux із Docker та публікує звіти.

## Результат перевірки 12.11.2026

`mvnw.cmd --batch-mode --no-transfer-progress clean verify` — BUILD SUCCESS.

| Перевірка | Результат |
| --- | ---: |
| Усі сценарії тестів | 161 |
| PostgreSQL integration: JPA slice + повний API-сценарій | 17 + 1 |
| Помилки / падіння / пропуски | 0 / 0 / 0 |
| Покриття рядків усього коду | 93.25% |
| Покриття гілок усього коду | 80.52% |

`MarketplaceApiIntegrationTest` додатково перевіряє весь Spring-контекст через MockMvc:
товар → поповнення складу → покупець → замовлення → Natural ID → завершення → звіт.
Репозиторії та сервіси в цьому сценарії справжні, база — PostgreSQL у Testcontainers.
CI-конфігурацію оновлено; віддалений GitHub Actions у межах цієї роботи не запускався.
