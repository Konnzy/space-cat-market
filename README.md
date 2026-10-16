# Cosmo Cats Intergalactic Marketplace — лабораторна 1

Spring Boot 4.1.1, Java 25, Maven Wrapper, MapStruct 1.6.3.
Гілка `lab1-fixes` містить виправлення self-review та зауважень викладача.

## Збірка та запуск

З кореня проєкту у PowerShell:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

За наявності закешованих залежностей: `.\mvnw.cmd -o clean verify`.
API: `http://localhost:8080/api/v1/products`.
Автоматичні тести та їхні Maven-залежності вилучені за вказівкою викладача: це матеріал ЛР2.
Збірка підтверджує компіляцію та пакування; поведінку перевіряємо HTTP-запитами.

## Контракт і CRUD

[OpenAPI 3.0.3](src/main/resources/api-specs/cosmo-cats-market.yml) містить конкретні examples
успішних відповідей та помилок для перегляду у Swagger Editor.

| Метод | Шлях | Результат |
| --- | --- | --- |
| POST | `/api/v1/products` | `201`, товар і Location |
| GET | `/api/v1/products?page=0&size=20` | `200`, сторінка товарів |
| GET | `/api/v1/products/{id}` | `200` або `404` |
| PUT | `/api/v1/products/{id}` | `200` або `404`, заміна всіх редагованих полів |
| DELETE | `/api/v1/products/{id}` | `204` без тіла або `404` |
| GET | `/api/v1/products/external/{externalId}` | `200`, товар каталогу; `404` або `502` для збоїв |

Тіло POST/PUT:

```json
{
  "name": "Star Antigravity Yarn",
  "description": "A floating yarn ball for intergalactic cats.",
  "price": 19.99,
  "category": "TOYS",
  "sellerEmail": "toys@cosmo-cats.market"
}
```

Усі поля обов'язкові й не можуть бути null. `name`: 3–100 символів, містить окреме слово
star, galaxy або comet без урахування регістру (`@CosmicWordCheck`). `description`: 1–1000
символів, не лише пробіли. `price`: більше 0, до 10 цифр у цілій частині й до 2 після крапки.
`category`: TOYS, FOOD або ACCESSORIES. `sellerEmail`: непорожня адреса до 254 символів,
перевіряється `@Email`. POST генерує UUID, PUT зберігає ID з URL. ULID залишився навчальною рекомендацією.

## Пагінація

Спільний `PaginationRequest` містить page, size і необов'язковий cursor; контролер перевіряє його
через `@Valid` до виклику сервісу. Початкові значення: page=0, size=20; межі: page >= 0, size від 1 до 100.
Без cursor працює offset page/size у порядку додавання товарів.
З cursor результати починаються після вказаного UUID в тому самому порядку; page ігнорується,
а page у відповіді дорівнює 0. Size діє в обох режимах.

```text
GET /api/v1/products?size=1
GET /api/v1/products?size=1&cursor=00000000-0000-0000-0000-000000000001
```

Envelope: content, page, size, totalElements, nextCursor. TotalElements — розмір усього локального
каталогу в знімку запиту. NextCursor — останній повернений ID, якщо далі є товари; інакше null.
Порожній каталог: `200`, content=[], totalElements=0. Сторінка за межами непорожнього каталогу:
`200`, content=[], фактичний totalElements. GET списку не має штучного сценарію 404.
Некоректний, невідомий або видалений cursor повертає 400.
Зміни між запитами й стабільність DB/keyset-пагінації залишаються матеріалом наступної лабораторної.

## Помилки

Глобальний `@ControllerAdvice` використовує Spring ProblemDetail,
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) та application/problem+json:

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

ValidationError залишається DTO одного порушення; errors обов'язкове для валідації.
Для помилок конвертації path-параметрів handler доповнює стандартний ProblemDetail Spring
нашим errors. Значення категорії формуються з Category.values().
400: неправильні поля/параметри, cursor або JSON; 404: відсутній товар за ID;
415: непідтримуваний Content-Type; 502: недоступний каталог або невалідна відповідь;
500: неочікуваний збій без внутрішніх подробиць у відповіді.

## Організація коду

- domain: незмінні Product, Category, Cart, Order без залежностей від Spring.
- application: ProductService, ProductPage, спільний application.dto.PaginationRequest;
  CatalogService із зовнішнім запитом, обробкою помилок і окремим методом перевірки відповіді.
  application.catalog.dto.CatalogProductDto використовують сервіс і контролер.
- api: контролер, DTO локального API, Jakarta-валідація, MapStruct ProductMapper та handler.
- infrastructure.persistence: ProductRepository поруч з InMemoryProductRepository.
  Сервіс залежить від інтерфейсу; сховище — синхронізований змінюваний LinkedHashMap.
- infrastructure.catalog: створення RestClient і ввімкнення Spring resilience.

MapStruct генерує ProductMapperImpl у target/generated-sources/annotations; ручна реалізація була
в lab1-draft. Колекція content мапиться автоматично, response DTO створює незмінну копію списку.
Cart і Order не мають додаткових API чи workflow. JPA, Hibernate ORM, Spring Data і БД не використовуються.
Hibernate Validator є реалізацією Jakarta Bean Validation, а не ORM.

## Mock-дані й зовнішній каталог

При старті: 00000000-0000-0000-0000-000000000001 — Star Antigravity Yarn;
00000000-0000-0000-0000-000000000002 — Galaxy Cosmic Milk;
00000000-0000-0000-0000-000000000003 — Comet Collar. Перезапуск відновлює mock-дані.

[DummyJSON](https://dummyjson.com/docs/products) читається через GET /products/{id}.
Товари не зберігаються локально; повертаються id, title, description, price.
Космічна валідація не застосовується до зовнішніх назв.
Налаштування: catalog.base-url, catalog.connect-timeout=2s, catalog.read-timeout=3s.
CATALOG_BASE_URL дозволяє використати контрольований HTTP-сервер.
Локальний CRUD і запуск застосунку не звертаються до каталогу.

Вбудований Spring 7 `@Retryable` повторює транспортні збої та HTTP 500/502/503/504:
дві повторні спроби після початкової, із затримками 200 і 400 мс.
Інші HTTP-помилки, зокрема 404, та невалідні товари не повторюються.
404 провайдера стає локальним 404; решта остаточних збоїв — 502.

## Логування і ручна перевірка

SLF4J: початкове наповнення сховища й успішні зміни товарів — INFO; читання, пагінація,
зовнішні запити та їхня тривалість — DEBUG; валідація, збої спроб і повтори з номером/затримкою — WARN;
остаточні зовнішні та неочікувані збої — ERROR зі stack trace у handler. Відсутній ресурс — INFO.
Повні товари й email продавців у повідомлення не додаються. Пакет застосунку налаштований на DEBUG;
рівень перевизначається через logging.level.com.market.spacecatmarket.

Перевірка після збірки:

1. POST → GET → PUT → GET → DELETE, Location і сталість UUID.
2. Page/size, cursor/nextCursor, кінець списку й порожній каталог з 200.
3. Bean Validation, CosmicWordCheck, неправильні UUID/JSON/category, 404 та 415.
4. На контрольованому HTTP-сервері: успіх, відновлення після тимчасових збоїв, вичерпання трьох спроб,
   одиничні 404/інші 4xx/невалідні відповіді, timeout.
5. У консолі: повідомлення відповідних рівнів, номери повторів і відсутність повних DTO.

Windows Maven Wrapper використовує виправлений PowerShell comment marker.
