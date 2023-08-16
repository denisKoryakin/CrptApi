package com.company;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private Semaphore semaphore;
    private TimeUnit timeUnit;
    private int requestLimit;
    private ScheduledExecutorService scheduler;

    private CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    public static CrptApi init(TimeUnit timeUnit, int requestLimit) {
        CrptApi crptApi = new CrptApi(timeUnit, requestLimit);
        crptApi.schedulePermitReplenishment();
        return crptApi;
    }

    public void schedulePermitReplenishment() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> semaphore.release(requestLimit - semaphore.availablePermits()), 1, timeUnit);
    }

    enum documentFormat {
        MANUAL, XML, CSV
    }

    public String createDocument(Document document) throws IOException, InterruptedException {
        class Body {
            final String product_document;
            final String document_format;
            final String type;
            final String signature;

            public Body(Document document1) {
                this.product_document = new String(Base64.getEncoder().encode(document.toString().getBytes()));
                this.document_format = documentFormat.MANUAL.toString();
                this.type = "LP_INTRODUCE_GOODS";
                this.signature = new String(Base64.getEncoder().encode(document.signature.getBytes()));
            }
        }
        Token token = getToken();
        Body body = new Body(document);
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        String bodyString = gson.toJson(body);
        var client = HttpClient.newHttpClient();
        var request = HttpRequest
                .newBuilder(URI.create("'https://ismp.crpt.ru/api/v3/lk/documents/create?pg=" + document.productGroup))
                .header("accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer" + token)
                .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private class Document {

        String documentFormat;
        String productDocument;
        String productGroup;
        String signature;
        String type;
    }

    private class AuthResponse {

        String uuid;
        String data;

        public AuthResponse(String uuid, String data) {
            this.uuid = uuid;
            this.data = data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }

        @Override
        public String toString() {
            return "AuthResponse{" +
                    "uuid='" + uuid + '\'' +
                    ", data='" + data + '\'' +
                    '}';
        }
    }

    private class Token {

        String token;

        public Token(String token) {
            this.token = token;
        }

        @Override
        public String toString() {
            return "Token{" +
                    "token='" + token + '\'' +
                    '}';
        }
    }

    private AuthResponse auth() throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest
                .newBuilder(URI.create("https://ismp.crpt.ru/api/v3/auth/cert/key"))
                .header("accept", "application/json")
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        return gson.fromJson(response, AuthResponse.class);
    }

    private Token getToken() throws IOException, InterruptedException {
        AuthResponse authResponse = auth();
        authResponse.setData(new String(Base64.getEncoder().encode(authResponse.getData().getBytes())));
        GsonBuilder builder = new GsonBuilder();
        Gson gson = builder.create();
        String body = gson.toJson(authResponse);
        var client = HttpClient.newHttpClient();
        var request = HttpRequest
                .newBuilder(URI.create("https://ismp.crpt.ru/api/v3/auth/cert/"))
                .header("accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        return gson.fromJson(response, Token.class);
    }
}
