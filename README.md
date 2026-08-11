# Trading Simulator

Java 21 + Spring Boot 3.5 + Maven.

## Estructura

- `core/market`: OHLC y datos históricos
- `core/loader`: carga y parsing de CSV
- `core/engine`: motor de backtesting
- `core/broker`: ejecución simulada y operaciones
- `core/strategy`: contrato que debe implementar cada estrategia
- `strategy/ExampleStrategy.java`: único fichero que necesitas modificar para empezar a programar la estrategia

## CSV

El proyecto ya incluye `data/3QQQ.csv`, con el fichero histórico proporcionado.

El parser soporta:

- fechas `dd.MM.yyyy`
- números con formato europeo (`381,70`)
- miles con punto
- volumen con `K`, `M` y `B`
- CSV con campos entre comillas
- datos descargados en orden descendente: el parser los ordena cronológicamente

## Configuración

En `src/main/resources/application.yml`:

```yaml
simulator:
  symbol: 3QQQ
  data-directory: data
  initial-capital: 100000
```

## Build

```bash
mvn clean package
```

## Ejecución

```bash
mvn spring-boot:run
```

o:

```bash
java -jar target/trading-simulator-0.0.1-SNAPSHOT.jar
```

## Crear una estrategia

Modifica `ExampleStrategy.java`:

```java
@Override
public void onCandle(
        Candle candle,
        MarketData marketData,
        int index,
        Broker broker) {

    // reglas de la estrategia
}
```

En cada llamada:

- `candle` = vela actual
- `marketData` = histórico completo
- `index` = posición de la vela actual
- `broker` = cuenta simulada

La estrategia no necesita conocer nada de Spring ni del parser.


## Formato interno de precios

Los precios se almacenan como `long`, eliminando la coma decimal:

- `381,70` -> `38170`
- `379,34` -> `37934`
- `366,60` -> `36660`

La columna `Vol.` del CSV se ignora completamente.
