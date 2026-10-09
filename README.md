# Cosmo Cats Intergalactic Marketplace — лабораторна 1

Product CRUD на Spring Boot 4.1.1 / Java 25 після виправлень self-review PR1.
Поточна робоча гілка: `lab1-fixes`.

## Збірка та запуск

Потрібен JDK 25. Maven завантажується через включений Maven Wrapper.
У PowerShell з кореня проєкту:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Якщо залежності вже закешовані, збірка без мережі: `.\mvnw.cmd -o clean verify`.
Базова адреса API: `http://localhost:8080`.
Тести не звертаються до реального зовнішнього сервісу.

## API

OpenAPI 3.0.3: [src/main/resources/api-specs/cosmo-cats-market.yml](src/main/resources/api-specs/cosmo-cats-market.yml).
Файл можна відкрити у Swagger Editor або імпортувати в Postman.

| Метод | Шлях | Результат |
| --- | --- | --- |
| POST | `/api/v1/products` | `201`, товар та заголовок `Location` |
| GET | `/api/v1/products?page=0&size=20` | `200`, `content`, `page`, `size`, `totalElements` |
| GET | `/api/v1/products/{id}` | `200`, товар; `404`, якщо відсутній |
| PUT | `/api/v1/products/{id}` | `200`, повністю оновлений товар; `404`, якщо відсутній |
| DELETE | `/api/v1/products/{id}` | `204` без тіла; `404`, якщо відсутній |
| GET | `/api/v1/products/external/{externalId}` | `200`, товар DummyJSON; `404` або `502` для помилок провайдера |

Створення й оновлення приймають однакове JSON-тіло:

```json
{
  "name": "Star Antigravity Yarn",
  "description": "A floating yarn ball for intergalactic cats.",
  "price": 19.99,
  "category": "TOYS",
  "sellerEmail": "toys@cosmo-cats.market"
}
```

Усі п'ять полів обов'язкові й не можуть бути `null`. `name`: 3–100 символів,
містить окреме слово `star`, `galaxy` або `comet` без урахування регістру.
`description`: 1–1000 символів, не лише пробіли. `price`: більше 0,
до 10 цифр у цілій частині й до 2 після десяткової крапки.
`category`: `TOYS`, `FOOD` або `ACCESSORIES`.
`sellerEmail`: непорожня адреса, до 254 символів, перевірена `@Email`.
UUID генерується сервером; PUT зберігає ID та замінює всі редаговані поля.

`page` починається з 0; `size` — від 1 до 100. Порядок — порядок додавання.
Сторінка за межами списку повертає порожній `content` та актуальний `totalElements`.
Пагінація працює над знімком колекції в пам'яті; питання стабільності сторінок
при паралельних змінах у БД залишене для наступної лабораторної.

## Помилки

`@ControllerAdvice` використовує Spring `ProblemDetail` та
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html), тип вмісту — `application/problem+json`:

```json
{
  "type": "https://cosmo-cats.market/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "Field 'price' must be greater than 0.",
  "instance": "/api/v1/products",
  "errors": [{"field": "price", "message": "must be greater than 0"}]
}
```

`errors` обов'язкове для відповідей валідації та містить усі порушення з назвами полів або параметрів.
Некоректний UUID, JSON, категорія та параметри пагінації повертають `400`.
Відсутній товар — `404`, недоступний зовнішній каталог — `502`,
непідтримуваний Content-Type — `415`, неочікувана помилка — `500`.
Внутрішні подробиці не потрапляють до відповіді `500`.

## Межі шарів

- `domain`: прості незмінні `Product`, `Category`, `Cart`, `Order` та інтерфейс `ProductRepository`.
  Cart і Order містять знімки списків товарів; Order обчислює суму через `BigDecimal`.
  Для них не створено окремих API чи сценаріїв оформлення замовлення.
- `application`: `ProductService` для локального CRUD і валідації пагінації,
  `CatalogService` для зовнішнього каталогу, результат пагінації, помилки сценаріїв;
  `ProductCatalogClient` — інтерфейс для стороннього каталогу.
- `api`: контролер з базовим шляхом `/api/v1/products`, request/response DTO, MapStruct-інтерфейс `ProductMapper`,
  Jakarta Bean Validation і `@CosmicWordCheck`, глобальний обробник помилок.
- `infrastructure`: синхронізований `Map<UUID, Product>` у `InMemoryProductRepository`
  та реалізація клієнта DummyJSON через Spring `RestClient`.

Інтерфейси введені для сховища, зовнішнього клієнта й MapStruct-маппера; сервіси — звичайні класи.
JPA, Hibernate ORM, Spring Data Repository та реальна БД не використовуються.
`Hibernate Validator` є реалізацією Jakarta Bean Validation, а не ORM.

У `lab1-draft` маппер був ручним; у `lab1-fixes` його замінено на `@Mapper`
з Spring component model. MapStruct 1.6.3 генерує `ProductMapperImpl` у
`target/generated-sources/annotations` під час Maven compile.
POST і далі генерує UUID, PUT зберігає переданий UUID; поля DTO/domain,
метадані пагінації й незмінність списку `content` збережені.

## Mock-дані й зовнішній сервіс

При старті у пам'ять додаються:

- `00000000-0000-0000-0000-000000000001`: Star Antigravity Yarn;
- `00000000-0000-0000-0000-000000000002`: Galaxy Cosmic Milk;
- `00000000-0000-0000-0000-000000000003`: Comet Collar.

CRUD змінює це сховище. Після перезапуску дані повертаються до початкових.

Для обов'язкової інтеграції вибрано публічний тестовий
[каталог DummyJSON](https://dummyjson.com/docs/products): `GET /products/{id}`.
Endpoint `/api/v1/products/external/1` показує запит через `RestClient` і повертає
`id`, `title`, `description`, `price` провайдера. Він не створює локальний товар.
Зовнішні назви не підпадають під локальне правило космічних слів.
Запуск застосунку й локальний CRUD не потребують доступу до DummyJSON;
зовнішній endpoint потребує мережі.

Налаштування в `application.properties`: `catalog.base-url`, `catalog.connect-timeout=2s`,
`catalog.read-timeout=3s`. Їх можна перевизначити, наприклад змінною `CATALOG_BASE_URL`.
Без автоматичних повторів: `404` провайдера відображається як `404`, інші HTTP-помилки,
порожні/некоректні відповіді й транспортні збої — як `502`.

## Перевірка вимог

- CRUD-контракт у потрібній папці: endpoints, DTO, обмеження, коди та приклади помилок.
- RFC 9457, глобальний `@ControllerAdvice`, подробиці кожного порушення валідації.
- Чотири доменні моделі та відокремлені domain/application/API/infrastructure.
- DTO і MapStruct `ProductMapper.toDomain` / `toDto` після ручної реалізації в draft.
- Сервісний CRUD та окремий in-memory repository з mock-даними, без БД.
- Дійсна інтеграція `RestClient` з окремою реалізацією й обробкою збоїв.
- Jakarta `@NotNull`, `@Size`, `@Email` та кастомна `@CosmicWordCheck`.
- `page` / `size` і paged envelope.
- Тести HTTP CRUD, валідації, пагінації, помилок і RestClient через `MockRestServiceServer`;
  перевірка читання YAML та локальних `$ref`, мінімальних правил Order/Cart.

У цій гілці нових тестів не додано; у двох наявних файлах тестів оновлено лише URL для API v1.

Maven Wrapper для Windows містить виправлення початкового маркера PowerShell-коментаря,
щоб стандартні команди збірки працювали.
