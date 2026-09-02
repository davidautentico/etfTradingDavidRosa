package com.alphapowertrading.tickdownloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {

  private static final String USER_TOKEN =
          "REPLACE_WITH_YOUR_CURRENT_USER_TOKEN";

  private static final String WS_TOKEN =
          "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtZHMtY2xpZW50IiwiaXNzIjoidG9rZW4tc2VydmljZSIsInNjb3BlIjoid2Vic29ja2V0IiwiaWF0IjoxNzg4Mzg5MjI4LCJleHAiOjE3ODgzODk2NDh9.30aoVrRk5jfh6eHTkJvtGB2jGIBOaTRhAMA8lgev6W4";
  private static final String TRACING_SALT =
          "af5a8d16eb5dc49f8a72b26fd9185475c7a";

  private static final String MDS_TRACING_ID =
          "ea65e63f-b88f-414f-b1a9-e035263c8b0f";

  private static final String TOKEN_URL =
          "https://api.live.deutsche-boerse.com/v1/mdstokenservice/token";

  private static final String WEBSOCKET_URL =
          "wss://api.live.deutsche-boerse.com/v1/mds/ws";

  private static final String CURRENCY = "EUR";
  private static final String MARKET = "ETR>STX";

  private static final List<String> DEFAULT_ISINS =
          List.of(  "IE00BLRPRL42","IE00B52VJ196","IE00B4K48X80","IE000Z7P04F4","IE00BFZXGZ54","FR0010342592",
            "LU0411078552","XS2399364152","IE00B53SZB19"
          );

  private static final String DEFAULT_FROM =
          "2000-01-01T06:00:00Z";

  private static final String DEFAULT_TO =
          "2026-09-02T20:00:00Z";

  private static final String DEFAULT_QUALITY =
          "REALTIME";

  private static final int PAGE_SIZE = 100;

  private static final String DAILY_RESOLUTION = "1D";

  private static final String DAILY_QUALITY = "DELAYED";

  private static final Duration REQUEST_TIMEOUT =
          Duration.ofSeconds(60);

  private static final DateTimeFormatter FILE_DATE_FORMAT =
          DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private static final ObjectMapper OBJECT_MAPPER =
          new ObjectMapper();

  private Main() {}

  public static void main(String[] args) throws Exception {
    if (args.length > 4) {
      printUsage();
      System.exit(1);
    }

    // Command-line arguments override the default parameters.
    List<String> isins =
            args.length >= 1
                    ? parseIsins(args[0])
                    : DEFAULT_ISINS;

    Instant from =
            parseInstant(args.length >= 2 ? args[1] : DEFAULT_FROM);

    Instant to =
            parseInstant(args.length >= 3 ? args[2] : DEFAULT_TO);

    String quality =
            args.length >= 4
                    ? args[3].trim().toUpperCase()
                    : DEFAULT_QUALITY;

    if (isins.isEmpty()) {
      throw new IllegalArgumentException("At least one ISIN is required.");
    }

    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("From must be before to.");
    }

    String fromDate =
            FILE_DATE_FORMAT.withZone(ZoneOffset.UTC).format(from);

    String toDate =
            FILE_DATE_FORMAT.withZone(ZoneOffset.UTC).format(to);

    System.out.println("==============================================");
    System.out.println("DEUTSCHE BÖRSE TICK DOWNLOADER");
    System.out.println("ISINs:          " + String.join(", ", isins));
    System.out.println("From:           " + from);
    System.out.println("To:             " + to);
    System.out.println("Quality:        " + quality);
    System.out.println("==============================================");

    // Keep WS_TOKEN as a temporary solution until token retrieval is finalized.
    // String mdsToken = fetchMdsToken(USER_TOKEN);
    String mdsToken = WS_TOKEN;

    try (TickHistoryClient client = new TickHistoryClient()) {
      client.connect();

      /*
       * Authentication is not considered successful until dataAuthentication
       * is received from the Deutsche Börse WebSocket.
       */
      client.authenticate(mdsToken);

      for (String isin : isins) {
        downloadInstrument(client, isin, from, to, fromDate, toDate, quality);
      }
    }
  }

  private static void downloadInstrument(
          TickHistoryClient client,
          String isin,
          Instant from,
          Instant to,
          String fromDate,
          String toDate,
          String quality)
          throws Exception {
    String marketstateId =
            quality + "[" + isin + "," + CURRENCY + "@" + MARKET + "]";

    Path tickOutput =
            Path.of(isin + "_ticks_" + fromDate + "_" + toDate + ".csv");

    Path dailyOutput =
            Path.of(isin + "_daily_" + fromDate + "_" + toDate + ".csv");

    System.out.println();
    System.out.println("----------------------------------------------");
    System.out.println("ISIN:           " + isin);
    System.out.println("Market state:   " + marketstateId);
    System.out.println("Tick output:    " + tickOutput.toAbsolutePath());
    System.out.println("Daily output:   " + dailyOutput.toAbsolutePath());
    System.out.println("----------------------------------------------");

    List<Tick> ticks = client.downloadAll(marketstateId, from, to, quality);

    // Write newest ticks first.
    ticks.sort(Comparator.comparing(Tick::datetime).reversed());

    writeCsv(tickOutput, ticks);

    System.out.println("Downloaded ticks: " + ticks.size());
    System.out.println("Tick CSV: " + tickOutput.toAbsolutePath());

    List<DailyValue> dailyValues = client.downloadDaily(isin, from, to);

    // Write newest daily values first.
    dailyValues.sort(Comparator.comparing(DailyValue::datetime).reversed());

    writeDailyCsv(dailyOutput, dailyValues);

    System.out.println("Downloaded daily values: " + dailyValues.size());
    System.out.println("Daily CSV: " + dailyOutput.toAbsolutePath());
  }

  private static List<String> parseIsins(String value) {
    List<String> isins =
            new ArrayList<>();

    for (String isin : value.split("[,;]")) {
      String normalized = isin.trim().toUpperCase();
      if (!normalized.isBlank() && !isins.contains(normalized)) {
        isins.add(normalized);
      }
    }

    return isins;
  }

  private static String fetchMdsToken(
          String userToken)
          throws IOException, InterruptedException {

    Instant now = Instant.now();

    ZonedDateTime localNow =
            now.atZone(
                    ZoneId.systemDefault());

    DateTimeFormatter clientDateFormatter =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd'T'HH:mm:ssXXX");

    DateTimeFormatter securityFormatter =
            DateTimeFormatter.ofPattern(
                    "yyyyMMddHHmm");

    String clientDate =
            localNow.format(
                    clientDateFormatter);

    String requestDateTime =
            DateTimeFormatter.ISO_INSTANT
                    .format(now);

    String clientTraceId =
            md5(
                    clientDate
                            + TRACING_SALT);

    String security =
            md5(
                    localNow.format(
                            securityFormatter));

    String requestTraceId =
            sha256(
                    "/v1/mdstokenservice/token"
                            + "@"
                            + requestDateTime
                            + MDS_TRACING_ID);

    HttpRequest request =
            HttpRequest.newBuilder()
                    .uri(
                            URI.create(TOKEN_URL))
                    .timeout(
                            Duration.ofSeconds(15))
                    .header(
                            "Accept",
                            "application/json, text/plain, */*")
                    .header(
                            "Authorization",
                            "Bearer " + userToken)
                    .header(
                            "Client-Date",
                            clientDate)
                    .header(
                            "X-Client-TraceId",
                            clientTraceId)
                    .header(
                            "X-Security",
                            security)
                    .header(
                            "X-Request-Datetime",
                            requestDateTime)
                    .header(
                            "X-Request-Trace-ID",
                            requestTraceId)
                    .header(
                            "Origin",
                            "https://live.deutsche-boerse.com")
                    .header(
                            "Referer",
                            "https://live.deutsche-boerse.com/")
                    .GET()
                    .build();

    HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(15))
                    .build();

    HttpResponse<String> response =
            httpClient.send(
                    request,
                    HttpResponse.BodyHandlers
                            .ofString());

    if (response.statusCode() != 200) {
      throw new IOException(
              "MDS token request failed. HTTP "
                      + response.statusCode()
                      + ": "
                      + response.body());
    }

    JsonNode json =
            OBJECT_MAPPER.readTree(
                    response.body());

    JsonNode tokenNode =
            json.get("token");

    if (tokenNode == null
            || !tokenNode.isTextual()
            || tokenNode.asText().isBlank()) {

      throw new IOException(
              "MDS token is missing or empty. "
                      + "Response: "
                      + response.body());
    }

    return tokenNode.asText();
  }

  private static String md5(String value)
          throws IOException {

    return hash("MD5", value);
  }

  private static String sha256(String value)
          throws IOException {

    return hash("SHA-256", value);
  }

  private static String hash(
          String algorithm,
          String value)
          throws IOException {

    try {
      MessageDigest digest =
              MessageDigest.getInstance(
                      algorithm);

      byte[] hash =
              digest.digest(
                      value.getBytes(
                              StandardCharsets.UTF_8));

      StringBuilder result =
              new StringBuilder(
                      hash.length * 2);

      for (byte b : hash) {
        result.append(
                String.format(
                        "%02x",
                        b));
      }

      return result.toString();

    } catch (Exception error) {
      throw new IOException(
              "Unable to calculate "
                      + algorithm
                      + " hash.",
              error);
    }
  }

  private static Instant parseInstant(
          String value) {

    try {
      return Instant.parse(value);
    } catch (Exception ignored) {
      return OffsetDateTime
              .parse(value)
              .toInstant();
    }
  }

  private static void writeCsv(
          Path output,
          List<Tick> ticks)
          throws IOException {

    Path parent =
            output.getParent();

    if (parent != null) {
      Files.createDirectories(
              parent);
    }

    try (BufferedWriter writer =
                 Files.newBufferedWriter(
                         output,
                         StandardCharsets.UTF_8)) {

      writer.write(
              "datetime,value,quantity");

      writer.newLine();

      for (Tick tick : ticks) {

        writer.write(
                tick.datetime());

        writer.write(',');

        writer.write(
                Double.toString(
                        tick.value()));

        writer.write(',');

        writer.write(
                Double.toString(
                        tick.quantity()));

        writer.newLine();
      }
    }
  }

  private static void printUsage() {

    System.out.println();

    System.out.println(
            "Usage:");

    System.out.println(
            "  java ... "
                    + Main.class.getName()
                    + " <ISIN[,ISIN...]> <FROM_ISO> "
                    + "<TO_ISO> [QUALITY]");

    System.out.println();

    System.out.println(
            "Example:");

    System.out.println(
            "  java ... "
                    + Main.class.getName()
                    + " IE00BLRPRL42,IE00B52VJ196 "
                    + "2026-09-01T06:00:00Z "
                    + "2026-09-02T20:00:00Z");

    System.out.println();

    System.out.println(
            "Default tick quality: REALTIME");

    System.out.println(
            "Daily series quality: DELAYED");
  }

  private record Tick(
          String datetime,
          double value,
          double quantity) {}

  private record DailyValue(
          String datetime,
          Map<String, String> values) {}

  private static void writeDailyCsv(
          Path output,
          List<DailyValue> values)
          throws IOException {

    Path parent =
            output.getParent();

    if (parent != null) {
      Files.createDirectories(parent);
    }

    List<String> columns =
            new ArrayList<>();

    for (DailyValue value : values) {
      for (String key : value.values().keySet()) {
        if (!columns.contains(key)) {
          columns.add(key);
        }
      }
    }

    columns.remove("datetime");
    columns.add(0, "datetime");

    try (BufferedWriter writer =
                 Files.newBufferedWriter(
                         output,
                         StandardCharsets.UTF_8)) {

      writer.write(String.join(",", columns));
      writer.newLine();

      for (DailyValue value : values) {
        for (int i = 0; i < columns.size(); i++) {
          if (i > 0) {
            writer.write(',');
          }

          String column = columns.get(i);

          if ("datetime".equals(column)) {
            writer.write(escapeCsv(value.datetime()));
          } else {
            writer.write(
                    escapeCsv(
                            value.values()
                                    .getOrDefault(column, "")));
          }
        }

        writer.newLine();
      }
    }
  }

  private static String escapeCsv(String value) {

    if (value == null) {
      return "";
    }

    if (value.contains(",")
            || value.contains("\"")
            || value.contains("\n")
            || value.contains("\r")) {

      return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    return value;
  }

  private record TickHistoryPageResult(
          List<Tick> ticks,
          int totalResultCount) {}

  private static final class TickHistoryClient
          implements AutoCloseable {

    private final AtomicInteger requestSequence =
            new AtomicInteger();

    private final Map<String, RequestState>
            requests =
            new ConcurrentHashMap<>();

    private final WebSocketListener listener =
            new WebSocketListener(requests);

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private volatile WebSocket webSocket;

    private void connect()
            throws Exception {

      webSocket =
              httpClient
                      .newWebSocketBuilder()
                      .connectTimeout(
                              Duration.ofSeconds(15))
                      .buildAsync(
                              URI.create(
                                      WEBSOCKET_URL),
                              listener)
                      .get(
                              30,
                              TimeUnit.SECONDS);

      System.out.println(
              "WebSocket connected.");
    }

    private void authenticate(
            String token)
            throws Exception {

      String requestId =
              nextRequestId();

      CompletableFuture<Boolean>
              completion =
              new CompletableFuture<>();

      requests.put(
              requestId,
              new AuthenticationState(
                      completion));

      Map<String, Object> message =
              new LinkedHashMap<>();

      message.put(
              "subscribeAuthentication",
              Map.of("token", token));

      message.put(
              "requestId",
              requestId);

      try {

        /*
         * send() only confirms that the message
         * was sent by the WebSocket client.
         */
        send(message);

        /*
         * Wait for the server response.
         *
         * AuthenticationState completes this
         * future only after dataAuthentication
         * has been received.
         */
        boolean authenticated =
                completion.get(
                        30,
                        TimeUnit.SECONDS);

        if (!authenticated) {
          throw new IOException(
                  "WebSocket authentication failed.");
        }

        System.out.println(
                "WebSocket authenticated.");

      } finally {
        requests.remove(requestId);
      }
    }

    private List<Tick> downloadAll(
            String marketstateId,
            Instant from,
            Instant to,
            String quality)
            throws Exception {

      List<Tick> allTicks =
              new ArrayList<>();

      int offset = 0;

      int totalResultCount =
              Integer.MAX_VALUE;

      while (offset
              < totalResultCount) {

        String requestId =
                nextRequestId();

        TickHistoryState pageState =
                new TickHistoryState();

        requests.put(
                requestId,
                pageState);

        Map<String, Object>
                listTickHistory =
                new LinkedHashMap<>();

        listTickHistory.put(
                "marketstateId",
                marketstateId);

        listTickHistory.put(
                "start",
                from.toString());

        listTickHistory.put(
                "end",
                to.toString());

        listTickHistory.put(
                "filterByVolume",
                null);

        listTickHistory.put(
                "quality",
                quality);

        listTickHistory.put(
                "offset",
                offset);

        listTickHistory.put(
                "limit",
                PAGE_SIZE);

        Map<String, Object> message =
                new LinkedHashMap<>();

        message.put(
                "listTickHistory",
                listTickHistory);

        message.put(
                "requestId",
                requestId);

        try {

          send(message);

          TickHistoryPageResult result =
                  pageState.completion.get(
                          REQUEST_TIMEOUT
                                  .toMillis(),
                          TimeUnit.MILLISECONDS);

          allTicks.addAll(
                  result.ticks());

          totalResultCount =
                  result.totalResultCount();

          System.out.printf(
                  "Request %s: received %d "
                          + "ticks. Total: %d/%d%n",
                  requestId,
                  result.ticks().size(),
                  allTicks.size(),
                  totalResultCount);

          if (result.ticks().isEmpty()) {
            break;
          }

          offset +=
                  result.ticks().size();

        } finally {
          requests.remove(requestId);
        }
      }

      return allTicks;
    }

    private List<DailyValue> downloadDaily(
            String isin,
            Instant from,
            Instant to)
            throws Exception {

      String requestId =
              nextRequestId();

      TimeseriesState state =
              new TimeseriesState();

      requests.put(
              requestId,
              state);

      String marketstateId =
              DAILY_QUALITY
                      + "["
                      + isin
                      + ","
                      + CURRENCY
                      + "@"
                      + MARKET
                      + "]";

      Map<String, Object>
              listTimeseries =
              new LinkedHashMap<>();

      listTimeseries.put(
              "resolution",
              DAILY_RESOLUTION);

      listTimeseries.put(
              "marketstateId",
              marketstateId);

      listTimeseries.put(
              "start",
              from.toString());

      listTimeseries.put(
              "end",
              to.toString());

      listTimeseries.put(
              "cleanSplits",
              true);

      listTimeseries.put(
              "cleanDividends",
              true);

      listTimeseries.put(
              "cleanDistributions",
              true);

      listTimeseries.put(
              "cleanSubscriptions",
              false);

      listTimeseries.put(
              "quality",
              DAILY_QUALITY);

      Map<String, Object> message =
              new LinkedHashMap<>();

      message.put(
              "listTimeseries",
              listTimeseries);

      message.put(
              "requestId",
              requestId);

      try {
        send(message);

        List<DailyValue> result =
                state.completion.get(
                        REQUEST_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS);

        if (result.isEmpty()) {
          System.out.println(
                  "Warning: no daily values were returned.");
        }

        System.out.printf(
                "Request %s: received %d daily values.%n",
                requestId,
                result.size());

        return result;

      } finally {
        requests.remove(requestId);
      }
    }

    private void send(
            Object message)
            throws Exception {

      if (webSocket == null) {
        throw new IOException(
                "WebSocket is not connected.");
      }

      String json =
              OBJECT_MAPPER.writeValueAsString(
                      message);

      webSocket
              .sendText(json, true)
              .join();
    }

    private String nextRequestId() {
      return "request-"
              + requestSequence
              .incrementAndGet();
    }

    @Override
    public void close() {

      WebSocket socket =
              webSocket;

      webSocket = null;

      if (socket != null) {
        try {

          socket
                  .sendClose(
                          WebSocket.NORMAL_CLOSURE,
                          "Done")
                  .get(
                          10,
                          TimeUnit.SECONDS);

        } catch (Exception ignored) {

          socket.abort();
        }
      }
    }
  }

  private static final class WebSocketListener
          implements WebSocket.Listener {

    private final Map<String, RequestState>
            requests;

    private final StringBuilder buffer =
            new StringBuilder();

    private WebSocketListener(
            Map<String, RequestState>
                    requests) {

      this.requests = requests;
    }

    @Override
    public void onOpen(
            WebSocket webSocket) {

      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(
            WebSocket webSocket,
            CharSequence data,
            boolean last) {

      buffer.append(data);

      if (last) {

        processMessage(
                buffer.toString());

        buffer.setLength(0);
      }

      webSocket.request(1);

      return CompletableFuture
              .completedFuture(null);
    }

    @Override
    public void onError(
            WebSocket webSocket,
            Throwable error) {

      requests.values()
              .forEach(
                      state ->
                              state.fail(error));
    }

    @Override
    public CompletionStage<?> onClose(
            WebSocket webSocket,
            int statusCode,
            String reason) {

      IOException error =
              new IOException(
                      "WebSocket closed. Code: "
                              + statusCode
                              + ", reason: "
                              + reason);

      requests.values()
              .forEach(
                      state ->
                              state.fail(error));

      return CompletableFuture
              .completedFuture(null);
    }

    private void processMessage(
            String text) {

      try {

        JsonNode message =
                OBJECT_MAPPER.readTree(
                        text);

        String requestId =
                message
                        .path("requestId")
                        .asText();

        if (requestId.isBlank()) {
          return;
        }

        RequestState state =
                requests.get(requestId);

        if (state != null) {
          state.accept(message);
        }

      } catch (Exception error) {

        requests.values()
                .forEach(
                        state ->
                                state.fail(error));
      }
    }
  }

  private interface RequestState {

    void accept(JsonNode message);

    void fail(Throwable error);
  }

  private static final class AuthenticationState
          implements RequestState {

    private final CompletableFuture<Boolean>
            completion;

    private AuthenticationState(
            CompletableFuture<Boolean>
                    completion) {

      this.completion = completion;
    }

    @Override
    public void accept(
            JsonNode message) {

      /*
       * Successful WebSocket authentication is
       * identified by dataAuthentication.
       *
       * Do NOT use isComplete here.
       */
      JsonNode authentication =
              message.get(
                      "dataAuthentication");

      if (authentication != null
              && !authentication.isNull()) {

        completion.complete(true);
        return;
      }

      JsonNode error =
              message.get("error");

      if (error != null
              && !error.isNull()) {

        completion.completeExceptionally(
                new IOException(
                        "Authentication failed: "
                                + error));
      }
    }

    @Override
    public void fail(
            Throwable error) {

      completion.completeExceptionally(
              error);
    }
  }

  private static final class TimeseriesState
          implements RequestState {

    private final List<DailyValue> values =
            new ArrayList<>();

    private final CompletableFuture<List<DailyValue>>
            completion =
            new CompletableFuture<>();

    @Override
    public synchronized void accept(
            JsonNode message) {

      JsonNode data =
              message.get("dataTimeseries");

      if (data != null
              && !data.isNull()) {

        extractTimeseriesValues(
                data,
                values);
      }

      if (message
              .path("isComplete")
              .asBoolean(false)) {

        completion.complete(
                new ArrayList<>(values));
      }
    }

    @Override
    public void fail(
            Throwable error) {

      completion.completeExceptionally(
              error);
    }

    private static void extractTimeseriesValues(
            JsonNode data,
            List<DailyValue> target) {

      if (data.isArray()) {
        for (JsonNode item : data) {
          extractTimeseriesValues(
                  item,
                  target);
        }
        return;
      }

      if (!data.isObject()) {
        return;
      }

      JsonNode date = data.get("date");

      if (date != null
              && date.isValueNode()) {

        Map<String, String> values =
                new LinkedHashMap<>();

        data.fields().forEachRemaining(
                entry -> {

                  JsonNode value =
                          entry.getValue();

                  if (value.isValueNode()
                          && !value.isNull()) {

                    values.put(
                            entry.getKey(),
                            value.asText());
                  }
                });

        target.add(
                new DailyValue(
                        date.asText(),
                        values));

        return;
      }

      data.fields().forEachRemaining(
              entry ->
                      extractTimeseriesValues(
                              entry.getValue(),
                              target));
    }
  }

  private static final class TickHistoryState
          implements RequestState {

    private final List<Tick> ticks =
            new ArrayList<>();

    private final CompletableFuture<TickHistoryPageResult>
            completion =
            new CompletableFuture<>();

    private int totalResultCount;

    @Override
    public synchronized void accept(
            JsonNode message) {

      JsonNode tick =
              message.get(
                      "dataTickHistory");

      if (tick != null
              && !tick.isNull()) {

        ticks.add(
                new Tick(
                        tick.path("datetime")
                                .asText(),
                        tick.path("value")
                                .asDouble(),
                        tick.path("quantity")
                                .asDouble()));

        totalResultCount =
                message
                        .path("totalResultCount")
                        .asInt(
                                totalResultCount);
      }

      /*
       * isComplete belongs to the
       * listTickHistory response.
       */
      if (message
              .path("isComplete")
              .asBoolean(false)) {

        completion.complete(
                new TickHistoryPageResult(
                        new ArrayList<>(
                                ticks),
                        totalResultCount));
      }
    }

    @Override
    public void fail(
            Throwable error) {

      completion.completeExceptionally(
              error);
    }
  }
}