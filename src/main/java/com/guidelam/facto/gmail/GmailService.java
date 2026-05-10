package com.guidelam.facto.gmail;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.gmail.Gmail;
import com.guidelam.facto.auth.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GmailService {

    public static final String APP_NAME = "facto";
    public static final String INVOICE_QUERY = "has:attachment filename:pdf -in:trash -in:spam";

    private final TokenStore tokenStore;
    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;

    public Optional<Gmail> client() {
        return tokenStore.loadCredential().map(credential ->
                new Gmail.Builder(httpTransport, jsonFactory, credential)
                        .setApplicationName(APP_NAME)
                        .build());
    }
}
