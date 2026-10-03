# Prompt para el agente Angular — Catálogo de unidades, conversiones e importación .xlsx

> Copia todo lo que está debajo de la línea y pégalo al agente del front.

---

Eres el agente del frontend de **Cronos — Specialty Bakery Management System** (Angular + PrimeNG con
la plantilla **Freya**). El backend acaba de rediseñar el catálogo de **tipos de unidad**
(`/unit-type`) y **unidades de medida** (`/measurement-unit`), agregó **conversiones al vuelo** para
recetas y una **carga masiva desde .xlsx** con validación, trazabilidad y auditoría. Implementa todo lo
de abajo siguiendo las convenciones que ya existen en el proyecto (estructura de features, servicios
HTTP, interceptores de autenticación/DPoP/JWE, manejo de errores, i18n, toasts). Antes de escribir
código, revisa cómo están hechas las pantallas actuales de *Unit Types* y *Units* y la versión de
PrimeNG instalada (usa `p-select` o `p-dropdown`, `p-toggleswitch` o `p-inputSwitch`, etc. según la
versión). Nada de `any`: tipa todo con interfaces/`enum`s; componentes standalone + `OnPush` + signals
si el proyecto ya los usa; formularios reactivos tipados; sin lógica de negocio en templates; sin
suscripciones colgadas (`takeUntilDestroyed`/`async`).

## 1. Contrato general de la API

- Base URL: `${environment.apiUrl}` (= `http://localhost:9191/api/v1` en local). Reutiliza el
  `HttpClient`/interceptores existentes (Bearer + DPoP/JWE si aplica).
- Envía siempre `Accept-Language: es` o `en` (según el idioma de la UI): los mensajes de error y del
  reporte de importación vienen traducidos por el backend.
- Toda respuesta JSON usa este sobre:

```ts
export interface ApiEnvelope<T> {
  meta: { traceId: string; timestamp: string };
  status: 'SUCCESS' | 'ERROR';
  message: string | null;
  data: T | null;
  errors?: ApiError[];          // solo cuando status = 'ERROR'
}
export interface ApiError { code: string; message: string; field: string | null; imageUrl?: string; }
export interface Page<T> {
  content: T[]; pageNumber: number; pageSize: number;
  totalElements: number; totalPages: number; last: boolean;
}
```

- Paginación: `page` (base 0), `size`, `sort=campo,asc|desc` (puede repetirse). Un campo de orden no
  permitido responde 400.
- Errores: 400 `VALIDATION_FAILED` (con `errors[].field` = nombre del campo del body → márcalo en el
  control del formulario), 404 `RESOURCE_NOT_FOUND`, 409 `DUPLICATE_RESOURCE` (con `field`),
  409 `DATA_INTEGRITY_VIOLATION` / `BUSINESS_CONFLICT` (reglas de negocio: muéstralas en
  `p-message`/toast), 403 sin permiso, 413 archivo muy grande. Siempre muestra `meta.traceId` en los
  errores 5xx ("Referencia: …") para soporte.

### Enums

```ts
export type UnitDimension = 'MASS' | 'VOLUME' | 'COUNT' | 'LENGTH';
export type RecordStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
export type ImportResource = 'UNIT_TYPE' | 'MEASUREMENT_UNIT';
export type ImportStatus = 'VALIDATED' | 'COMMITTED' | 'REJECTED' | 'FAILED';
export type ImportAction = 'CREATE' | 'UPDATE' | 'UNCHANGED';
export type ConversionPath = 'IDENTITY' | 'LINEAR' | 'DENSITY';
```

Etiquetas sugeridas (i18n): MASS = Masa/Mass, VOLUME = Volumen/Volume, COUNT = Conteo/Count,
LENGTH = Longitud/Length.

### Permisos

Las **escrituras** (crear, editar, eliminar, cambiar estatus, importar, descargar plantilla, historial
de importaciones) requieren el rol `SUPER_ADMIN` o el permiso `MANAGE_CATALOGS`. Las lecturas y
`/convert` son para cualquier usuario autenticado. Oculta/deshabilita botones de escritura si el
usuario no tiene esos permisos (usa el mecanismo de permisos que ya tenga el front) y maneja el 403
de todas formas.

## 2. Tipos de unidad — `/unit-type`

```ts
export interface UnitType {
  id: number; codeIdentity: string; name: string; dimension: UnitDimension; status: RecordStatus;
  createdAt: string; createdBy: string | null; updatedAt: string | null; updatedBy: string | null;
}
export interface UnitTypeRequest { codeIdentity: string; name: string; dimension: UnitDimension; }
```

| Acción | Llamada | Respuesta |
|---|---|---|
| Listar | `GET /unit-type?search=&dimension=&status=&page=0&size=10&sort=name,asc` | `Page<UnitType>` |
| Opciones (combo) | `GET /unit-type/catalog` | `UnitType[]` (solo activos) |
| Detalle | `GET /unit-type/{id}` | `UnitType` |
| Crear | `POST /unit-type` body `UnitTypeRequest` | 201 `UnitType` |
| Editar | `PUT /unit-type/{id}` body `UnitTypeRequest` | `UnitType` |
| Eliminar | `DELETE /unit-type/{id}` | `data: null` |
| Estatus | `PATCH /unit-type/{id}/status` body `{ "status": "INACTIVE" }` | `data: null` |

Pantalla (lista + diálogo "New Unit Type" que ya existe):
- `p-table` lazy (paginación/orden en servidor) con columnas Code, Name, Dimension (tag), Status
  (tag), Updated (fecha + usuario). Filtros: búsqueda con debounce (300 ms) → `search`, combo de
  dimensión → `dimension`, combo de estatus → `status`. Columnas ordenables: `codeIdentity`, `name`,
  `dimension`, `status`, `updatedAt`.
- Diálogo: **Code** (requerido, máx. 20, patrón `^[\p{L}\p{N}][\p{L}\p{N}._-]*$` — sin espacios),
  **Name** (requerido, máx. 100), **Dimension**: reemplaza el input de texto libre ("e.g. mass,
  volume, length") por un **select** con los 4 valores del enum. Botón Create deshabilitado mientras el
  form sea inválido o la petición esté en curso.
- Reglas del backend que debes comunicar bien (vienen como 409 con mensaje traducido): solo un tipo
  por dimensión; la dimensión no cambia si el tipo ya tiene unidades; no se puede desactivar/eliminar
  un tipo con unidades (activas). Confirma con `p-confirmDialog` antes de eliminar o desactivar.

## 3. Unidades de medida — `/measurement-unit`

```ts
export interface MeasurementUnit {
  id: number; codeIdentity: string; name: string; namePlural: string;
  unitTypeId: number; unitTypeCode: string; unitType: string;   // unitType = nombre del tipo
  dimension: UnitDimension; multiplierToBase: number;            // ver nota de precisión
  isBaseUnit: boolean; isSystemDefault: boolean;
  inUse: boolean;                                                // usada por insumos/recetas
  status: RecordStatus;
  createdAt: string; createdBy: string | null; updatedAt: string | null; updatedBy: string | null;
}
export interface MeasurementUnitRequest {
  codeIdentity: string; name: string; namePlural: string;
  unitTypeId: number; multiplierToBase: number; isBaseUnit: boolean;
}
export interface MeasurementUnitOption {      // GET /measurement-unit/catalog
  id: number; codeIdentity: string; name: string; namePlural: string;
  unitTypeId: number; unitTypeCode: string; unitType: string; dimension: UnitDimension;
  multiplierToBase: number; isBaseUnit: boolean;
}
```

| Acción | Llamada | Respuesta |
|---|---|---|
| Listar | `GET /measurement-unit?search=&unitTypeId=&dimension=&status=&page=0&size=10&sort=name,asc` | `Page<MeasurementUnit>` |
| Opciones (recetas/insumos) | `GET /measurement-unit/catalog` | `MeasurementUnitOption[]` (activas, ordenadas por tipo y tamaño) |
| Detalle | `GET /measurement-unit/{id}` | `MeasurementUnit` |
| Crear | `POST /measurement-unit` | 201 `MeasurementUnit` |
| Editar | `PUT /measurement-unit/{id}` | `MeasurementUnit` |
| Eliminar | `DELETE /measurement-unit/{id}` | `data: null` |
| Estatus | `PATCH /measurement-unit/{id}/status` body `{ "status": "INACTIVE" }` | `data: null` |

**Cambios incompatibles con el código actual del front** (corrígelos):
- `GET /measurement-unit/system` ya no existe → `GET /measurement-unit`.
- `PUT /measurement-unit` (con `id`, `status`, `userId` en el body) ya no existe →
  `PUT /measurement-unit/{id}` con `MeasurementUnitRequest`. El estatus solo cambia con el `PATCH`.
- `POST /measurement-unit` ya no acepta `userId`.

Pantalla (lista + diálogo "New Unit" que ya existe):
- `p-table` lazy con columnas Code, Name, Plural, Unit Type (nombre + tag de dimensión), Factor to base,
  Base (icono), In use (icono/tag), Status, Updated. Orden permitido: `codeIdentity`, `name`,
  `namePlural`, `unitType`, `dimension`, `multiplierToBase`, `status`, `updatedAt`. Filtros: búsqueda,
  tipo de unidad (combo con `/unit-type/catalog` → `unitTypeId`), dimensión, estatus.
- Diálogo:
  - **Code** requerido, máx. 20, mismo patrón; aclara en el hint que distingue mayúsculas (`T` ≠ `t`).
  - **Name** y **Plural** requeridos, máx. 100.
  - **Unit Type**: hoy es un input de texto ("e.g. mass") → cámbialo a **select** alimentado por
    `GET /unit-type/catalog` (label = `name`, mostrar la dimensión como sufijo/tag, value = `id`).
  - **Factor to base unit**: `p-inputNumber` con `mode="decimal"`, `minFractionDigits=0`,
    `maxFractionDigits=10`, `min` > 0, `useGrouping=false`, locale `en-US` para que el separador
    decimal sea punto. Validadores: requerido, > 0, máx. 10 enteros y 10 decimales.
  - **Is base unit**: al activarlo, fija el factor en `1` y deshabilita el campo; al desactivarlo,
    habilítalo. Muestra ayuda: "Exactamente una unidad base por tipo; la primera unidad de un tipo
    debe ser su base".
  - Muestra un texto de equivalencia en vivo: `1 {name} = {factor} {baseUnit.namePlural}` (la base se
    obtiene de `/measurement-unit/catalog` filtrando `unitTypeId` + `isBaseUnit`).
  - Si `inUse = true`: deshabilita Unit Type, Factor e Is base unit y muestra un `p-message` info:
    "Unidad en uso por insumos o recetas: su tipo, factor y unidad base están bloqueados para no
    alterar costos históricos". Code/Name/Plural siguen editables.
  - Códigos reservados `g`, `cup`, `tbsp`, `tsp`: deshabilita Code, el botón eliminar y desactivar
    (el backend también lo impide).
- Eliminar: deshabilitado si `inUse`; confirmación. El backend además rechaza eliminar una unidad
  base de la que dependen otras.

**Nota de precisión**: `multiplierToBase`/`result` llegan como número JSON con hasta 10 decimales.
Para cálculos en el cliente usa una utilidad de decimales (p. ej. `decimal.js`/`big.js` si ya está
en el proyecto) o redondea solo para mostrar; nunca muestres notación científica.

## 4. Conversiones al vuelo en recetas (y donde se capturen cantidades)

```ts
export interface UnitConversionRequest { quantity: number; fromUnitId: number; toUnitId: number; rawMaterialId?: string; }
export interface UnitConversionResult {
  quantity: number; fromUnitId: number; fromUnitCode: string; toUnitId: number; toUnitCode: string;
  result: number; path: ConversionPath; densityRuleId: number | null; rawMaterialId: string | null;
}
```

`POST /measurement-unit/convert` → `UnitConversionResult` (no guarda nada).

Implementa un `UnitConversionService` (front) + un componente reutilizable **`<app-unit-quantity>`**
(cantidad + selector de unidad) para el editor de recetas:
1. Carga una sola vez `GET /measurement-unit/catalog` (cachéalo con `shareReplay(1)`/signal en un
   servicio `providedIn: 'root'`; invalídalo tras cualquier alta/edición/importación de unidades).
2. Agrupa el selector por `unitType` (`p-select` con `group`), mostrando `codeIdentity — name`.
3. Al cambiar de unidad sobre una cantidad ya capturada, convierte:
   - **Misma dimensión** → en el cliente, sin red: `qty × from.multiplierToBase ÷ to.multiplierToBase`
     (con la utilidad decimal; muestra hasta 4 decimales).
   - **MASS ⇄ VOLUME** → llama a `/convert` con `rawMaterialId` del insumo de la línea. Si responde
     409 `BUSINESS_CONFLICT` (falta la densidad del insumo), muestra el mensaje y ofrece un link a la
     ficha del insumo para capturar "gramos por taza/cucharada/cucharadita".
   - Otras combinaciones (p. ej. COUNT → MASS) → no permitas seleccionarlas: deshabilita esas opciones
     del selector, o filtra a la misma dimensión + (MASS/VOLUME si el insumo tiene densidad).
4. Muestra un chip/tooltip con la equivalencia aplicada ("3 tazas = 720 ml · lineal" o
   "2 cdas = 16 g · densidad del insumo").
5. Las unidades inactivas no aparecen en el catálogo; si una línea existente usa una unidad inactiva,
   muéstrala como texto de solo lectura con tag "Inactiva" (el backend rechaza elegirla de nuevo con
   409).

## 5. Carga masiva desde Excel (.xlsx)

```ts
export interface ImportIssue {
  row: number | null;            // fila de Excel (encabezado = 1); null = problema del archivo
  column: string | null; severity: 'ERROR' | 'WARNING'; code: string; message: string;
}
export interface FieldChange { from: unknown; to: unknown; }
export interface ImportRowResult {
  row: number; key: string; action: ImportAction; recordId: number | null;
  changes: Record<string, FieldChange>;
}
export interface ImportReport {
  batchId: string; resource: ImportResource; status: ImportStatus; dryRun: boolean;
  fileName: string; fileSizeBytes: number; fileSha256: string;
  totalRows: number; created: number; updated: number; unchanged: number; rejectedRows: number;
  errorCount: number; warningCount: number; issuesTruncated: boolean;
  issues: ImportIssue[]; rows: ImportRowResult[];
  actorUsername: string; traceId: string; startedAt: string; finishedAt: string; durationMs: number;
}
export interface ImportBatchSummary {
  batchId: string; resource: ImportResource; status: ImportStatus; dryRun: boolean;
  fileName: string; fileSizeBytes: number; fileSha256: string;
  totalRows: number; created: number; updated: number; unchanged: number; rejectedRows: number;
  errorCount: number; warningCount: number; actorUsername: string; traceId: string;
  startedAt: string; finishedAt: string;
}
```

| Acción | Llamada |
|---|---|
| Validar | `POST /unit-type/import?dryRun=true` · `POST /measurement-unit/import?dryRun=true` — `multipart/form-data`, parte **`file`** |
| Aplicar | mismo endpoint con `dryRun=false` |
| Plantilla | `GET /unit-type/import/template` · `GET /measurement-unit/import/template` (blob .xlsx) |
| Historial | `GET /data-imports?resource=&status=&page=0&size=20` → `Page<ImportBatchSummary>` |
| Reporte | `GET /data-imports/{batchId}` → `ImportReport` |

Respuesta de importación: HTTP 200 con `data: ImportReport` cuando el archivo se procesó (incluso si
`status = 'REJECTED'`: nada se guardó y `issues` explica por qué). Un error técnico inesperado
responde 5xx con el sobre de error (`status = FAILED` queda en el historial con el mismo `traceId`).
Límites: `.xlsx` sin macros ni contraseña, 2 MB, 2 000 filas.

Implementa un **asistente de importación** (`p-dialog` o página con `p-steps`/`p-stepper`), accesible
con un botón "Importar Excel" en ambas listas:
1. **Archivo**: botón "Descargar plantilla" (descarga el blob con el nombre del header
   `Content-Disposition`), `p-fileUpload` en modo custom (`customUpload`, `accept=".xlsx"`,
   `maxFileSize=2097152`, un solo archivo). Valida extensión y tamaño en el cliente antes de enviar.
   Aviso: "Importa primero los tipos de unidad y después las unidades de medida".
2. **Validación** (siempre primero, `dryRun=true`): muestra un resumen con tarjetas (Total, Crear,
   Actualizar, Sin cambios, Filas con error, Advertencias) y una `p-table` de `issues` con filtros por
   severidad/columna, ordenada por fila, con tag de severidad; si `issuesTruncated`, avisa que hay más
   errores que los mostrados. Debajo, tabla de `rows` con su `action` (tags de color) y un
   expandible con el diff `changes` (`campo: antes → después`). Si `status = 'REJECTED'`, bloquea el
   paso siguiente y permite volver a subir el archivo corregido. Muestra los `WARNING` (p. ej.
   "este archivo ya fue importado") sin bloquear.
3. **Confirmación** (`status = 'VALIDATED'`): `p-confirmDialog` "Se crearán X y actualizarán Y
   registros. ¿Aplicar?" → reenvía **el mismo archivo** con `dryRun=false`.
4. **Resultado**: muestra el reporte final (`COMMITTED`) con `batchId` (copiable), duración y
   `traceId`; invalida las cachés de catálogos y refresca las tablas. Si llega `REJECTED` (alguien
   cambió el catálogo entre la validación y la aplicación), muestra los issues igual que en el paso 2.
5. Botón para descargar los issues a CSV (generado en el cliente) para corregir el Excel.

**Historial de importaciones** (nueva pantalla bajo Catálogos/Administración, solo con permiso):
`p-table` lazy de `/data-imports` con filtros `resource` y `status`, columnas Fecha, Recurso, Archivo,
Modo (Validación/Aplicación por `dryRun`), Estatus (tag), Totales, Usuario, SHA-256 (truncado con
copia), y acción "Ver reporte" que abre el mismo visor del paso 2 con `GET /data-imports/{batchId}`.

Agrega la entrada de menú si el menú es estático; si viene del backend (`menu_items`), avísame para
darla de alta.

## 6. Calidad

- Pruebas unitarias de: servicios HTTP (`HttpTestingController`, incluidos los nuevos paths y el
  `multipart` con la parte `file`), validadores del formulario de unidad (patrón de código, factor,
  base = 1), conversión lineal en el cliente (casos: 2.5 kg → 2500 g, 3 tazas → 48 cdas, 0.5 mg → kg
  sin notación científica), y el asistente (no permite aplicar si la validación salió REJECTED).
- Accesibilidad: labels asociados, mensajes de error con `aria-describedby`, foco al primer campo
  inválido.
- Textos en los archivos de i18n existentes (es/en); nada hardcodeado en templates.
- No dupliques modelos: un `unit-catalog.models.ts` con las interfaces de arriba.
